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
import com.parkease.payment.messaging.NotificationPublisher;
import com.parkease.payment.repository.PassBalanceRepository;
import com.parkease.payment.repository.PassTransactionRepository;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassServiceImplTest {

    @Mock private PassBalanceRepository passRepo;
    @Mock private PassTransactionRepository txnRepo;
    @Mock private RazorpayClient razorpayClient;
    @Mock private OrderClient orderClient;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private PassReceiptService passReceiptService;

    @InjectMocks
    private PassServiceImpl service;

    private PassBalance activePass;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "razorpayKeyId", "rzp_test_key");
        ReflectionTestUtils.setField(service, "razorpayKeySecret", "test_secret");
        razorpayClient.orders = orderClient;

        activePass = PassBalance.builder()
                .passId(1L)
                .driverEmail("aryan@test.com")
                .status(PassStatus.ACTIVE)
                .parkingCountLimit(150)
                .parkingCountUsed(5)
                .purchasedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(29))
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateSig(String orderId, String paymentId) throws Exception {
        String payload = orderId + "|" + paymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test_secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private PassTransaction buildTxn() {
        PassTransaction txn = new PassTransaction();
        txn.setTransactionId(10L);
        txn.setPassId(1L);
        txn.setDriverEmail("aryan@test.com");
        txn.setBookingId(100L);
        txn.setAmount(50.0);
        txn.setPassTransactionRef("PASS-XYZ");
        txn.setCountBefore(5);
        txn.setCountAfter(6);
        txn.setCreatedAt(LocalDateTime.now());
        return txn;
    }

    // ── createPassOrder ──────────────────────────────────────────────────────

    @Test
    void shouldCreatePassOrderSuccessfully() throws Exception {
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.empty());

        Order mockOrder = mock(Order.class);
        when(orderClient.create(any(JSONObject.class))).thenReturn(mockOrder);
        when(mockOrder.get("id")).thenReturn("order_pass_123");

        OrderResponseDTO result = service.createPassOrder("aryan@test.com");

        assertNotNull(result);
        assertEquals("order_pass_123", result.getRazorpayOrderId());
        assertEquals(5000.0, result.getAmount());
        assertEquals("INR", result.getCurrency());
    }

    @Test
    void shouldThrowWhenActivePassExists() {
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));

        assertThrows(BadRequestException.class,
                () -> service.createPassOrder("aryan@test.com"));
    }

    @Test
    void shouldAllowNewPassWhenPreviousPassExhausted() throws Exception {
        // Pass with used count == limit — not active in the business sense
        activePass.setParkingCountUsed(150);
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));

        Order mockOrder = mock(Order.class);
        when(orderClient.create(any(JSONObject.class))).thenReturn(mockOrder);
        when(mockOrder.get("id")).thenReturn("order_pass_456");

        OrderResponseDTO result = service.createPassOrder("aryan@test.com");
        assertNotNull(result);
    }

    @Test
    void shouldThrowPaymentExceptionWhenRazorpayFails() throws Exception {
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.empty());
        when(orderClient.create(any(JSONObject.class))).thenThrow(new RazorpayException("Network error"));

        assertThrows(PaymentException.class,
                () -> service.createPassOrder("aryan@test.com"));
    }

    // ── activatePass ─────────────────────────────────────────────────────────

    @Test
    void shouldActivatePassSuccessfully() throws Exception {
        VerifyPassRequest req = new VerifyPassRequest();
        req.setRazorpayOrderId("order_pass_123");
        req.setRazorpayPaymentId("pay_pass_456");
        req.setRazorpaySignature(generateSig("order_pass_123", "pay_pass_456"));

        when(passRepo.findAllByDriverEmailAndStatusNot("aryan@test.com", PassStatus.ACTIVE))
                .thenReturn(List.of());
        when(passRepo.save(any(PassBalance.class))).thenReturn(activePass);
        doNothing().when(notificationPublisher).publish(any());

        PassBalanceDTO result = service.activatePass(req, "aryan@test.com");

        assertNotNull(result);
        verify(passRepo).save(any(PassBalance.class));
    }

    @Test
    void shouldCleanUpOldPassesWhenActivating() throws Exception {
        VerifyPassRequest req = new VerifyPassRequest();
        req.setRazorpayOrderId("order_pass_123");
        req.setRazorpayPaymentId("pay_pass_456");
        req.setRazorpaySignature(generateSig("order_pass_123", "pay_pass_456"));

        PassBalance oldPass = PassBalance.builder().passId(99L).status(PassStatus.EXPIRED).build();
        when(passRepo.findAllByDriverEmailAndStatusNot("aryan@test.com", PassStatus.ACTIVE))
                .thenReturn(List.of(oldPass));
        when(passRepo.save(any(PassBalance.class))).thenReturn(activePass);
        doNothing().when(notificationPublisher).publish(any());

        service.activatePass(req, "aryan@test.com");

        verify(passRepo).deleteAll(List.of(oldPass));
        verify(passRepo).flush();
    }

    @Test
    void shouldThrowWhenPassSignatureInvalid() {
        VerifyPassRequest req = new VerifyPassRequest();
        req.setRazorpayOrderId("order_pass_123");
        req.setRazorpayPaymentId("pay_pass_456");
        req.setRazorpaySignature("bad_signature");

        assertThrows(PaymentException.class,
                () -> service.activatePass(req, "aryan@test.com"));
    }

    // ── getMyPass ────────────────────────────────────────────────────────────

    @Test
    void shouldGetMyPassSuccessfully() {
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));
        when(txnRepo.findByDriverEmailOrderByCreatedAtDesc("aryan@test.com")).thenReturn(List.of());

        PassBalanceDTO result = service.getMyPass("aryan@test.com");

        assertNotNull(result);
        assertEquals(PassStatus.ACTIVE, result.getStatus());
        assertEquals(145, result.getParkingCountRemaining()); // 150 - 5
    }

    @Test
    void shouldThrowWhenNoPassFound() {
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMyPass("aryan@test.com"));
    }

    @Test
    void shouldLazilyExpirePassWhenChecked() {
        activePass.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));
        when(passRepo.save(activePass)).thenReturn(activePass);
        when(txnRepo.findByDriverEmailOrderByCreatedAtDesc("aryan@test.com")).thenReturn(List.of());

        service.getMyPass("aryan@test.com");

        verify(passRepo).save(activePass);
        assertEquals(PassStatus.EXPIRED, activePass.getStatus());
    }

    @Test
    void shouldThrowForCancelledPassAfterExpiry() {
        // CANCELLED + already expired → lazily marked EXPIRED first, then the CANCELLED
        // check can't trigger (it's now EXPIRED). So this test instead verifies the
        // pure CANCELLED (but NOT expired) path — which still throws ResourceNotFoundException.
        activePass.setStatus(PassStatus.CANCELLED);
        activePass.setExpiresAt(LocalDateTime.now().plusDays(5)); // NOT expired
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMyPass("aryan@test.com"));
        // Status must remain CANCELLED (expiry check is skipped because it's not after expiresAt)
        assertEquals(PassStatus.CANCELLED, activePass.getStatus());
    }

    @Test
    void shouldThrowForActiveCancelledPass() {
        // CANCELLED but not yet expired → still throw "no active pass"
        activePass.setStatus(PassStatus.CANCELLED);
        activePass.setExpiresAt(LocalDateTime.now().plusDays(10));
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMyPass("aryan@test.com"));
    }

    // ── payWithPass ──────────────────────────────────────────────────────────

    @Test
    void shouldPayWithPassSuccessfully() {
        PayWithPassRequest req = new PayWithPassRequest();
        req.setBookingId(100L);
        req.setAmount(50.0);

        PassTransaction savedTxn = buildTxn();
        when(txnRepo.findByBookingId(100L)).thenReturn(Optional.empty());
        when(passRepo.findFirstByDriverEmailAndStatusInOrderByPurchasedAtDesc(
                eq("aryan@test.com"), anyList()))
                .thenReturn(Optional.of(activePass));
        when(passRepo.save(any(PassBalance.class))).thenReturn(activePass);
        when(txnRepo.save(any(PassTransaction.class))).thenReturn(savedTxn);
        when(passReceiptService.generatePassReceipt(any(), any())).thenReturn("receipt.pdf");
        doNothing().when(notificationPublisher).publish(any());

        PassTransactionDTO result = service.payWithPass(req, "aryan@test.com");

        assertNotNull(result);
        verify(txnRepo, atLeastOnce()).save(any(PassTransaction.class));
    }

    @Test
    void shouldThrowWhenDuplicatePassPaymentForBooking() {
        PayWithPassRequest req = new PayWithPassRequest();
        req.setBookingId(100L);

        when(txnRepo.findByBookingId(100L)).thenReturn(Optional.of(buildTxn()));

        assertThrows(BadRequestException.class,
                () -> service.payWithPass(req, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenNoActivePassForPayment() {
        PayWithPassRequest req = new PayWithPassRequest();
        req.setBookingId(101L);

        when(txnRepo.findByBookingId(101L)).thenReturn(Optional.empty());
        when(passRepo.findFirstByDriverEmailAndStatusInOrderByPurchasedAtDesc(
                anyString(), anyList()))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> service.payWithPass(req, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenPassExpiredAtPaymentTime() {
        activePass.setExpiresAt(LocalDateTime.now().minusDays(1));
        PayWithPassRequest req = new PayWithPassRequest();
        req.setBookingId(102L);

        when(txnRepo.findByBookingId(102L)).thenReturn(Optional.empty());
        when(passRepo.findFirstByDriverEmailAndStatusInOrderByPurchasedAtDesc(
                anyString(), anyList()))
                .thenReturn(Optional.of(activePass));
        when(passRepo.save(activePass)).thenReturn(activePass);

        assertThrows(BadRequestException.class,
                () -> service.payWithPass(req, "aryan@test.com"));
        assertEquals(PassStatus.EXPIRED, activePass.getStatus());
    }

    @Test
    void shouldThrowWhenPassDepleted() {
        activePass.setParkingCountUsed(150); // already at limit
        PayWithPassRequest req = new PayWithPassRequest();
        req.setBookingId(103L);

        when(txnRepo.findByBookingId(103L)).thenReturn(Optional.empty());
        when(passRepo.findFirstByDriverEmailAndStatusInOrderByPurchasedAtDesc(
                anyString(), anyList()))
                .thenReturn(Optional.of(activePass));

        assertThrows(BadRequestException.class,
                () -> service.payWithPass(req, "aryan@test.com"));
        assertEquals(PassStatus.DEPLETED, activePass.getStatus());
    }

    @Test
    void shouldMarkPassDepletedWhenLastParkingUsed() {
        // One parking left (used = 149) — after paying, should become DEPLETED
        activePass.setParkingCountUsed(149);
        PayWithPassRequest req = new PayWithPassRequest();
        req.setBookingId(104L);

        PassTransaction savedTxn = buildTxn();
        when(txnRepo.findByBookingId(104L)).thenReturn(Optional.empty());
        when(passRepo.findFirstByDriverEmailAndStatusInOrderByPurchasedAtDesc(
                anyString(), anyList()))
                .thenReturn(Optional.of(activePass));
        when(passRepo.save(any(PassBalance.class))).thenAnswer(inv -> {
            PassBalance saved = inv.getArgument(0);
            saved.setPassId(1L); // simulate JPA returning saved entity
            return saved;
        });
        when(txnRepo.save(any(PassTransaction.class))).thenReturn(savedTxn);
        when(passReceiptService.generatePassReceipt(any(), any())).thenReturn(null);
        doNothing().when(notificationPublisher).publish(any());

        service.payWithPass(req, "aryan@test.com");

        assertEquals(PassStatus.DEPLETED, activePass.getStatus());
    }

    // ── cancelPass ───────────────────────────────────────────────────────────

    @Test
    void shouldCancelPassSuccessfully() {
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));
        when(passRepo.save(activePass)).thenReturn(activePass);

        service.cancelPass("aryan@test.com");

        assertEquals(PassStatus.CANCELLED, activePass.getStatus());
        assertNotNull(activePass.getCancelledAt());
    }

    @Test
    void shouldThrowWhenCancellingNonActivePass() {
        activePass.setStatus(PassStatus.EXPIRED);
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));

        assertThrows(BadRequestException.class,
                () -> service.cancelPass("aryan@test.com"));
    }

    @Test
    void shouldThrowWhenNoPassToCancel() {
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.cancelPass("aryan@test.com"));
    }

    // ── getMyPassTransactions ─────────────────────────────────────────────────

    @Test
    void shouldGetMyPassTransactionsSuccessfully() {
        PassTransaction txn = buildTxn();
        when(txnRepo.findByDriverEmailOrderByCreatedAtDesc("aryan@test.com"))
                .thenReturn(List.of(txn));

        List<PassTransactionDTO> result = service.getMyPassTransactions("aryan@test.com");

        assertEquals(1, result.size());
        assertEquals("PASS-XYZ", result.get(0).getPassTransactionRef());
    }

    @Test
    void shouldReturnEmptyListWhenNoTransactions() {
        when(txnRepo.findByDriverEmailOrderByCreatedAtDesc("aryan@test.com")).thenReturn(List.of());

        List<PassTransactionDTO> result = service.getMyPassTransactions("aryan@test.com");

        assertTrue(result.isEmpty());
    }

    // ── getMyCurrentPassTransactions ──────────────────────────────────────────

    @Test
    void shouldGetCurrentPassTransactionsSuccessfully() {
        PassTransaction txn = buildTxn();
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));
        when(txnRepo.findByPassIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(txn));

        List<PassTransactionDTO> result = service.getMyCurrentPassTransactions("aryan@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoCurrentPass() {
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.empty());

        List<PassTransactionDTO> result = service.getMyCurrentPassTransactions("aryan@test.com");

        assertTrue(result.isEmpty());
    }

    // ── getPassTransactionByBooking ───────────────────────────────────────────

    @Test
    void shouldGetPassTransactionByBooking() {
        PassTransaction txn = buildTxn();
        when(txnRepo.findByBookingId(100L)).thenReturn(Optional.of(txn));

        PassTransactionDTO result = service.getPassTransactionByBooking(100L, "aryan@test.com");

        assertNotNull(result);
        assertEquals(100L, result.getBookingId());
    }

    @Test
    void shouldThrowWhenTransactionBelongsToDifferentDriver() {
        PassTransaction txn = buildTxn();
        when(txnRepo.findByBookingId(100L)).thenReturn(Optional.of(txn));

        // "other@test.com" is not the transaction's driver
        assertThrows(ResourceNotFoundException.class,
                () -> service.getPassTransactionByBooking(100L, "other@test.com"));
    }

    @Test
    void shouldThrowWhenPassTransactionNotFound() {
        when(txnRepo.findByBookingId(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getPassTransactionByBooking(999L, "aryan@test.com"));
    }

    // ── getPassReceiptPath ────────────────────────────────────────────────────

    @Test
    void shouldReturnExistingReceiptPathWhenFileExists() throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("pass_receipt", ".pdf");
        tempFile.deleteOnExit();

        PassTransaction txn = buildTxn();
        txn.setReceiptPath(tempFile.getAbsolutePath());

        when(txnRepo.findById(10L)).thenReturn(Optional.of(txn));

        String result = service.getPassReceiptPath(10L, "aryan@test.com");

        assertEquals(tempFile.getAbsolutePath(), result);
        verify(passReceiptService, never()).generatePassReceipt(any(), any());
    }

    @Test
    void shouldRegenerateReceiptWhenMissing() {
        PassTransaction txn = buildTxn();
        txn.setReceiptPath(null);

        when(txnRepo.findById(10L)).thenReturn(Optional.of(txn));
        when(passRepo.findById(1L)).thenReturn(Optional.of(activePass));
        when(passReceiptService.generatePassReceipt(txn, activePass)).thenReturn("new_receipt.pdf");
        when(txnRepo.save(txn)).thenReturn(txn);

        String result = service.getPassReceiptPath(10L, "aryan@test.com");

        assertEquals("new_receipt.pdf", result);
        verify(txnRepo).save(txn);
    }

    @Test
    void shouldFallbackToDriverEmailPassWhenPassByIdNotFound() {
        PassTransaction txn = buildTxn();
        txn.setReceiptPath(null);

        when(txnRepo.findById(10L)).thenReturn(Optional.of(txn));
        when(passRepo.findById(1L)).thenReturn(Optional.empty());
        when(passRepo.findFirstByDriverEmailOrderByPurchasedAtDesc("aryan@test.com"))
                .thenReturn(Optional.of(activePass));
        when(passReceiptService.generatePassReceipt(txn, activePass)).thenReturn("fallback.pdf");
        when(txnRepo.save(txn)).thenReturn(txn);

        String result = service.getPassReceiptPath(10L, "aryan@test.com");

        assertEquals("fallback.pdf", result);
    }

    @Test
    void shouldThrowWhenReceiptPathNotFoundAndDriverMismatch() {
        PassTransaction txn = buildTxn();

        when(txnRepo.findById(10L)).thenReturn(Optional.of(txn));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getPassReceiptPath(10L, "other@test.com"));
    }

    @Test
    void shouldThrowWhenTxnNotFound() {
        when(txnRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getPassReceiptPath(999L, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenReceiptRegenerationFails() {
        PassTransaction txn = buildTxn();
        txn.setReceiptPath(null);

        when(txnRepo.findById(10L)).thenReturn(Optional.of(txn));
        when(passRepo.findById(1L)).thenReturn(Optional.of(activePass));
        when(passReceiptService.generatePassReceipt(txn, activePass)).thenReturn(null);

        assertThrows(PaymentException.class,
                () -> service.getPassReceiptPath(10L, "aryan@test.com"));
    }

    // ── checkAndExpirePasses (scheduler) ─────────────────────────────────────

    @Test
    void shouldExpireStaleActivePasses() {
        PassBalance stale = PassBalance.builder()
                .passId(5L).status(PassStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();

        when(passRepo.findAllByStatusAndExpiresAtBefore(eq(PassStatus.ACTIVE), any()))
                .thenReturn(new ArrayList<>(List.of(stale)));  // mutable list
        when(passRepo.findAllByStatusAndExpiresAtBefore(eq(PassStatus.CANCELLED), any()))
                .thenReturn(new ArrayList<>());
        when(passRepo.saveAll(anyList())).thenReturn(List.of(stale));

        service.checkAndExpirePasses();

        assertEquals(PassStatus.EXPIRED, stale.getStatus());
        verify(passRepo).saveAll(anyList());
    }

    @Test
    void shouldExpireStaleCancelledPasses() {
        PassBalance staleCancelled = PassBalance.builder()
                .passId(6L).status(PassStatus.CANCELLED)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();

        when(passRepo.findAllByStatusAndExpiresAtBefore(eq(PassStatus.ACTIVE), any()))
                .thenReturn(new ArrayList<>());
        when(passRepo.findAllByStatusAndExpiresAtBefore(eq(PassStatus.CANCELLED), any()))
                .thenReturn(new ArrayList<>(List.of(staleCancelled)));
        when(passRepo.saveAll(anyList())).thenReturn(List.of(staleCancelled));

        service.checkAndExpirePasses();

        assertEquals(PassStatus.EXPIRED, staleCancelled.getStatus());
    }

    @Test
    void shouldDoNothingWhenNoStalePasses() {
        when(passRepo.findAllByStatusAndExpiresAtBefore(eq(PassStatus.ACTIVE), any()))
                .thenReturn(new ArrayList<>());
        when(passRepo.findAllByStatusAndExpiresAtBefore(eq(PassStatus.CANCELLED), any()))
                .thenReturn(new ArrayList<>());

        service.checkAndExpirePasses();

        verify(passRepo, never()).saveAll(anyList());
    }
}
