package com.parkease.payment.service;

import com.parkease.payment.dto.request.PayWithPassRequest;
import com.parkease.payment.dto.request.VerifyPassRequest;
import com.parkease.payment.dto.response.OrderResponseDTO;
import com.parkease.payment.dto.response.PassBalanceDTO;
import com.parkease.payment.dto.response.PassTransactionDTO;
import com.parkease.payment.entity.*;
import com.parkease.payment.exception.BadRequestException;
import com.parkease.payment.exception.PaymentException;
import com.parkease.payment.exception.ResourceNotFoundException;
import com.parkease.payment.messaging.NotificationEvent;
import com.parkease.payment.messaging.NotificationPublisher;
import com.parkease.payment.repository.PassBalanceRepository;
import com.parkease.payment.repository.PassTransactionRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassServiceImpl implements PassService {

    private static final double PASS_PRICE   = 5000.0;
    private static final int    PASS_LIMIT   = 150;
    private static final int    PASS_DAYS    = 30;
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final PassBalanceRepository    passRepo;
    private final PassTransactionRepository txnRepo;
    private final RazorpayClient           razorpayClient;
    private final NotificationPublisher    notificationPublisher;
    private final PassReceiptService       passReceiptService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    // ─── Buy Pass — Step 1: create Razorpay order ────────────────────────────

    @Override
    @Transactional
    public OrderResponseDTO createPassOrder(String driverEmail) {
        log.info("Creating pass order for driver: {}", driverEmail);

        // Guard: only one active pass at a time
        passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc(driverEmail).ifPresent(existing -> {
            if (existing.getStatus() == PassStatus.ACTIVE
                    && LocalDateTime.now().isBefore(existing.getExpiresAt())
                    && existing.getParkingCountUsed() < PASS_LIMIT) {
                throw new BadRequestException(
                        "You already have an active pass valid until "
                        + existing.getExpiresAt().format(DATE_FMT) + ".");
            }
        });

        try {
            int amountInPaise = (int) (PASS_PRICE * 100);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount",   amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt",  "pass_" + driverEmail.replace("@", "_"));
            orderRequest.put("notes", new JSONObject()
                    .put("type",        "PASS_PURCHASE")
                    .put("driverEmail", driverEmail));

            Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = razorpayOrder.get("id");
            log.info("Razorpay pass order created: {} for driver: {}", razorpayOrderId, driverEmail);

            return OrderResponseDTO.builder()
                    .razorpayOrderId(razorpayOrderId)
                    .amount(PASS_PRICE)
                    .currency("INR")
                    .status("created")
                    .razorpayKeyId(razorpayKeyId)
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay pass order creation failed: {}", e.getMessage());
            throw new PaymentException("Failed to create pass order: " + e.getMessage());
        }
    }

    // ─── Buy Pass — Step 2: verify payment and activate pass ─────────────────

    @Override
    @Transactional
    public PassBalanceDTO activatePass(VerifyPassRequest request, String driverEmail) {
        log.info("Activating pass for driver: {} order: {}", driverEmail, request.getRazorpayOrderId());

        boolean isValid = verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());

        if (!isValid) {
            throw new PaymentException("Pass payment verification failed. Invalid signature.");
        }

        LocalDateTime now        = LocalDateTime.now();
        LocalDateTime expiresAt  = now.plusDays(PASS_DAYS);

        // Delete ALL previous non-active passes so the new pass starts clean
        List<PassBalance> oldPasses = passRepo.findAllByDriverEmailAndStatusNot(driverEmail, PassStatus.ACTIVE);
        if (!oldPasses.isEmpty()) {
            passRepo.deleteAll(oldPasses);
            passRepo.flush();
            log.info("Cleaned up {} old pass record(s) for driver: {}", oldPasses.size(), driverEmail);
        }

        PassBalance pass = PassBalance.builder()
                .driverEmail(driverEmail)
                .razorpayOrderId(request.getRazorpayOrderId())
                .razorpayPaymentId(request.getRazorpayPaymentId())
                .status(PassStatus.ACTIVE)
                .parkingCountLimit(PASS_LIMIT)
                .parkingCountUsed(0)
                .purchasedAt(now)
                .expiresAt(expiresAt)
                .build();

        PassBalance saved = passRepo.save(pass);
        log.info("Pass {} activated for driver: {}, valid until: {}", saved.getPassId(), driverEmail, expiresAt);

        notificationPublisher.publish(NotificationEvent.builder()
                .recipientEmail(driverEmail)
                .type("PAYMENT")
                .title("ParkEase Pass Activated! 🎫")
                .message("Your ParkEase Premium Pass is now active!\n"
                        + "150 parkings available until " + expiresAt.format(DATE_FMT) + ".\n"
                        + "Use it at checkout to skip Razorpay payments.")
                .channel("BOTH")
                .relatedId(saved.getPassId())
                .relatedType("PASS")
                .build());

        return toDTO(saved, 0);
    }

    // ─── Get current pass ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public PassBalanceDTO getMyPass(String driverEmail) {
        PassBalance pass = passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pass found for driver: " + driverEmail));

        // Lazy expiry check
        if ((pass.getStatus() == PassStatus.ACTIVE || pass.getStatus() == PassStatus.CANCELLED)
                && LocalDateTime.now().isAfter(pass.getExpiresAt())) {
            pass.setStatus(PassStatus.EXPIRED);
            pass = passRepo.save(pass);
            log.info("Pass {} lazily expired for driver: {}", pass.getPassId(), driverEmail);
        }
        
        // If pass is cancelled, we pretend they don't have one so they can buy a new one
        if (pass.getStatus() == PassStatus.CANCELLED) {
            throw new ResourceNotFoundException("No active pass found for driver: " + driverEmail);
        }

        int txnCount = txnRepo.findByDriverEmailOrderByCreatedAtDesc(driverEmail).size();
        return toDTO(pass, txnCount);
    }

    // ─── Use pass for payment ─────────────────────────────────────────────────

    @Override
    @Transactional
    public PassTransactionDTO payWithPass(PayWithPassRequest request, String driverEmail) {
        log.info("Processing pass payment for driver: {} booking: {}",
                driverEmail, request.getBookingId());

        // Idempotency guard — reject duplicate pass payments for the same booking
        if (txnRepo.findByBookingId(request.getBookingId()).isPresent()) {
            throw new BadRequestException(
                    "A pass payment has already been made for booking #" + request.getBookingId() + ".");
        }

        PassBalance pass = passRepo.findFirstByDriverEmailAndStatusInOrderByPurchasedAtDesc(driverEmail, List.of(PassStatus.ACTIVE, PassStatus.CANCELLED))
                .orElseThrow(() -> new BadRequestException("No active pass found. Please purchase a pass first."));

        // Expiry guard
        if (LocalDateTime.now().isAfter(pass.getExpiresAt())) {
            pass.setStatus(PassStatus.EXPIRED);
            passRepo.save(pass);
            throw new BadRequestException("Your pass expired on "
                    + pass.getExpiresAt().format(DATE_FMT) + ". Please buy a new pass.");
        }

        // Depletion guard
        if (pass.getParkingCountUsed() >= PASS_LIMIT) {
            pass.setStatus(PassStatus.DEPLETED);
            passRepo.save(pass);
            throw new BadRequestException("Your pass has been fully used (150/150). Please buy a new pass.");
        }

        // Proceed with deduction
        int countBefore = pass.getParkingCountUsed();
        pass.setParkingCountUsed(countBefore + 1);
        pass.setLastUsedAt(LocalDateTime.now());

        if (pass.getParkingCountUsed() >= PASS_LIMIT) {
            pass.setStatus(PassStatus.DEPLETED);
        }

        PassBalance savedPass = passRepo.save(pass);
        int remaining = PASS_LIMIT - savedPass.getParkingCountUsed();

        // Create transaction record
        String ref = "PASS-" + UUID.randomUUID().toString().toUpperCase();
        PassTransaction txn = PassTransaction.builder()
                .passId(savedPass.getPassId())
                .driverEmail(driverEmail)
                .bookingId(request.getBookingId())
                .amount(request.getAmount())
                .passTransactionRef(ref)
                .countBefore(countBefore)
                .countAfter(savedPass.getParkingCountUsed())
                .build();

        PassTransaction savedTxn = txnRepo.save(txn);
        log.info("Pass payment recorded: {} for booking: {}, remaining: {}",
                ref, request.getBookingId(), remaining);

        // Generate PDF receipt and persist the file path
        try {
            String receiptPath = passReceiptService.generatePassReceipt(savedTxn, savedPass);
            if (receiptPath != null) {
                savedTxn.setReceiptPath(receiptPath);
                savedTxn = txnRepo.save(savedTxn);
            }
        } catch (Exception e) {
            log.warn("Pass receipt generation failed for txn {}: {}", ref, e.getMessage());
        }

        // Notify driver
        String statusSuffix = savedPass.getStatus() == PassStatus.DEPLETED
                ? " Your pass has been fully used."
                : " Parkings remaining: " + remaining + ".";

        notificationPublisher.publish(NotificationEvent.builder()
                .recipientEmail(driverEmail)
                .type("PAYMENT")
                .title("Pass Used — Booking #" + request.getBookingId())
                .message("Your ParkEase Pass was used for Booking #" + request.getBookingId()
                        + ".\nTransaction Ref: " + ref
                        + "\nAmount: ₹" + String.format("%.2f", request.getAmount())
                        + statusSuffix)
                .channel("BOTH")
                .relatedId(savedTxn.getTransactionId())
                .relatedType("PASS_TRANSACTION")
                .build());

        return toTransactionDTO(savedTxn);
    }

    // ─── Query methods ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void cancelPass(String driverEmail) {
        log.info("Cancelling pass for driver: {}", driverEmail);
        PassBalance pass = passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("No pass found for driver: " + driverEmail));

        if (pass.getStatus() != PassStatus.ACTIVE) {
            throw new BadRequestException("Only ACTIVE passes can be cancelled.");
        }

        pass.setStatus(PassStatus.CANCELLED);
        pass.setCancelledAt(LocalDateTime.now());
        passRepo.save(pass);
        log.info("Pass {} cancelled by driver: {}", pass.getPassId(), driverEmail);
    }

    @Override
    public List<PassTransactionDTO> getMyPassTransactions(String driverEmail) {
        // Returns ALL historical pass transactions — used by My Receipts page
        return txnRepo.findByDriverEmailOrderByCreatedAtDesc(driverEmail)
                .stream().map(this::toTransactionDTO).toList();
    }

    @Override
    public List<PassTransactionDTO> getMyCurrentPassTransactions(String driverEmail) {
        // Returns only transactions for the CURRENT active pass — used by Subscription Usage History
        return passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc(driverEmail)
                .map(pass -> txnRepo.findByPassIdOrderByCreatedAtDesc(pass.getPassId())
                        .stream().map(this::toTransactionDTO).toList())
                .orElse(List.of());
    }

    @Override
    public PassTransactionDTO getPassTransactionByBooking(Long bookingId, String driverEmail) {
        return txnRepo.findByBookingId(bookingId)
                .filter(t -> t.getDriverEmail().equals(driverEmail))
                .map(this::toTransactionDTO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pass transaction found for booking: " + bookingId));
    }

    @Override
    @Transactional
    public String getPassReceiptPath(Long txnId, String driverEmail) {
        PassTransaction txn = txnRepo.findById(txnId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pass transaction not found: " + txnId));

        if (!txn.getDriverEmail().equals(driverEmail)) {
            throw new ResourceNotFoundException("Pass transaction not found: " + txnId);
        }

        // Resolve absolute path — storagePath may be relative (e.g. "receipts/")
        boolean fileExists = txn.getReceiptPath() != null
                && new java.io.File(txn.getReceiptPath()).exists();

        if (!fileExists) {
            log.info("Pass receipt missing for txn {}, regenerating...", txnId);

            // Look up the pass by its ID (stored on the transaction) — NOT the driver's current pass,
            // because the driver may have purchased a new pass since this transaction was created.
            PassBalance pass = passRepo.findById(txn.getPassId())
                    .orElseGet(() -> passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc(driverEmail)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Pass not found for transaction: " + txnId)));

            String path = passReceiptService.generatePassReceipt(txn, pass);
            if (path != null) {
                txn.setReceiptPath(path);
                txnRepo.save(txn);
            } else {
                throw new com.parkease.payment.exception.PaymentException(
                        "Receipt generation failed for transaction: " + txnId);
            }
        }

        return txn.getReceiptPath();
    }

    // ─── Scheduler: hourly expiry sweep ──────────────────────────────────────

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void checkAndExpirePasses() {
        List<PassBalance> stale = passRepo.findAllByStatusAndExpiresAtBefore(
                PassStatus.ACTIVE, LocalDateTime.now());
        List<PassBalance> staleCancelled = passRepo.findAllByStatusAndExpiresAtBefore(
                PassStatus.CANCELLED, LocalDateTime.now());
        
        stale.addAll(staleCancelled);
        
        if (!stale.isEmpty()) {
            stale.forEach(p -> p.setStatus(PassStatus.EXPIRED));
            passRepo.saveAll(stale);
            log.info("Scheduler expired {} pass(es).", stale.size());
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).equals(signature);
        } catch (Exception e) {
            log.error("Pass signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private PassBalanceDTO toDTO(PassBalance p, int txnCount) {
        return PassBalanceDTO.builder()
                .passId(p.getPassId())
                .driverEmail(p.getDriverEmail())
                .razorpayOrderId(p.getRazorpayOrderId())
                .razorpayPaymentId(p.getRazorpayPaymentId())
                .status(p.getStatus())
                .parkingCountLimit(p.getParkingCountLimit())
                .parkingCountUsed(p.getParkingCountUsed())
                .parkingCountRemaining(p.getParkingCountLimit() - p.getParkingCountUsed())
                .transactionCount(txnCount)
                .purchasedAt(p.getPurchasedAt())
                .expiresAt(p.getExpiresAt())
                .lastUsedAt(p.getLastUsedAt())
                .cancelledAt(p.getCancelledAt())
                .build();
    }

    private PassTransactionDTO toTransactionDTO(PassTransaction t) {
        return PassTransactionDTO.builder()
                .transactionId(t.getTransactionId())
                .passId(t.getPassId())
                .driverEmail(t.getDriverEmail())
                .bookingId(t.getBookingId())
                .amount(t.getAmount())
                .passTransactionRef(t.getPassTransactionRef())
                .countBefore(t.getCountBefore())
                .countAfter(t.getCountAfter())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
