package com.parkease.payment.controller;

import com.parkease.payment.dto.request.PayWithPassRequest;
import com.parkease.payment.dto.request.VerifyPassRequest;
import com.parkease.payment.dto.response.OrderResponseDTO;
import com.parkease.payment.dto.response.PassBalanceDTO;
import com.parkease.payment.dto.response.PassTransactionDTO;
import com.parkease.payment.service.PassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pass")
@RequiredArgsConstructor
@Slf4j
public class PassController {

    private final PassService passService;

    @PostMapping("/order")
    public ResponseEntity<OrderResponseDTO> createPassOrder(Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("POST /api/pass/order - driver: {}", email);
        return ResponseEntity.ok(passService.createPassOrder(email));
    }

    @PostMapping("/verify")
    public ResponseEntity<PassBalanceDTO> verifyPass(
            @Valid @RequestBody VerifyPassRequest request,
            Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("POST /api/pass/verify - driver: {}", email);
        return ResponseEntity.ok(passService.activatePass(request, email));
    }

    @GetMapping("/my")
    public ResponseEntity<PassBalanceDTO> getMyPass(Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("GET /api/pass/my - driver: {}", email);
        return ResponseEntity.ok(passService.getMyPass(email));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<PassTransactionDTO>> getMyTransactions(Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("GET /api/pass/transactions - driver: {}", email);
        return ResponseEntity.ok(passService.getMyPassTransactions(email));
    }

    @GetMapping("/transactions/current")
    public ResponseEntity<List<PassTransactionDTO>> getCurrentPassTransactions(Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("GET /api/pass/transactions/current - driver: {}", email);
        return ResponseEntity.ok(passService.getMyCurrentPassTransactions(email));
    }

    @PostMapping("/pay")
    public ResponseEntity<PassTransactionDTO> payWithPass(
            @RequestBody PayWithPassRequest request,
            Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("POST /api/pass/pay - driver: {}", email);
        return ResponseEntity.ok(passService.payWithPass(request, email));
    }

    @GetMapping("/transactions/booking/{bookingId}")
    public ResponseEntity<PassTransactionDTO> getPassTransactionByBooking(
            @PathVariable Long bookingId,
            Authentication auth) {
        String email = (String) auth.getPrincipal();
        return ResponseEntity.ok(passService.getPassTransactionByBooking(bookingId, email));
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelPass(Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("POST /api/pass/cancel - driver: {}", email);
        passService.cancelPass(email);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions/{txnId}/receipt")
    public ResponseEntity<org.springframework.core.io.Resource> downloadPassReceipt(
            @PathVariable Long txnId,
            Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("GET /api/pass/transactions/{}/receipt - driver: {}", txnId, email);
        String filePath = passService.getPassReceiptPath(txnId, email);
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(file);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=pass_receipt_" + txnId + ".pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
