package com.parkease.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parkease.payment.dto.request.PayWithPassRequest;
import com.parkease.payment.dto.request.VerifyPassRequest;
import com.parkease.payment.dto.response.OrderResponseDTO;
import com.parkease.payment.dto.response.PassBalanceDTO;
import com.parkease.payment.dto.response.PassTransactionDTO;
import com.parkease.payment.entity.PassStatus;
import com.parkease.payment.exception.GlobalExceptionHandler;
import com.parkease.payment.exception.PaymentException;
import com.parkease.payment.exception.ResourceNotFoundException;
import com.parkease.payment.service.PassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PassControllerTest {

    @Mock
    private PassService passService;

    @InjectMocks
    private PassController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private PassBalanceDTO sampleBalance;
    private PassTransactionDTO sampleTxn;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        sampleBalance = PassBalanceDTO.builder()
                .passId(1L)
                .driverEmail("aryan@test.com")
                .status(PassStatus.ACTIVE)
                .parkingCountLimit(10)
                .parkingCountUsed(3)
                .parkingCountRemaining(7)
                .purchasedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .build();

        sampleTxn = PassTransactionDTO.builder()
                .transactionId(10L)
                .passId(1L)
                .driverEmail("aryan@test.com")
                .bookingId(100L)
                .amount(50.0)
                .passTransactionRef("PASS-abc123")
                .countBefore(7)
                .countAfter(6)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UsernamePasswordAuthenticationToken driverAuth() {
        return new UsernamePasswordAuthenticationToken(
                "aryan@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_DRIVER")));
    }

    // ── POST /api/pass/order ─────────────────────────────────────────────────

    @Test
    void shouldCreatePassOrderSuccessfully() throws Exception {
        OrderResponseDTO orderResp = new OrderResponseDTO();
        orderResp.setRazorpayOrderId("order_pass_123");
        when(passService.createPassOrder("aryan@test.com")).thenReturn(orderResp);

        mockMvc.perform(post("/api/pass/order")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razorpayOrderId").value("order_pass_123"));
    }

    @Test
    void shouldReturn409WhenPassAlreadyActive() throws Exception {
        when(passService.createPassOrder("aryan@test.com"))
                .thenThrow(new PaymentException("Driver already has an active pass."));

        mockMvc.perform(post("/api/pass/order")
                        .principal(driverAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Driver already has an active pass."));
    }

    // ── POST /api/pass/verify ────────────────────────────────────────────────

    @Test
    void shouldActivatePassSuccessfully() throws Exception {
        VerifyPassRequest req = new VerifyPassRequest();
        req.setRazorpayOrderId("order_pass_123");
        req.setRazorpayPaymentId("pay_pass_456");
        req.setRazorpaySignature("valid_sig");

        when(passService.activatePass(any(VerifyPassRequest.class), eq("aryan@test.com")))
                .thenReturn(sampleBalance);

        mockMvc.perform(post("/api/pass/verify")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.parkingCountRemaining").value(7));
    }

    @Test
    void shouldReturn409WhenPassVerificationFails() throws Exception {
        VerifyPassRequest req = new VerifyPassRequest();
        req.setRazorpayOrderId("order_pass_123");
        req.setRazorpayPaymentId("pay_pass_456");
        req.setRazorpaySignature("bad_sig");

        when(passService.activatePass(any(), anyString()))
                .thenThrow(new PaymentException("Invalid payment signature."));

        mockMvc.perform(post("/api/pass/verify")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid payment signature."));
    }

    // ── GET /api/pass/my ─────────────────────────────────────────────────────

    @Test
    void shouldGetMyPassSuccessfully() throws Exception {
        when(passService.getMyPass("aryan@test.com")).thenReturn(sampleBalance);

        mockMvc.perform(get("/api/pass/my")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passId").value(1L))
                .andExpect(jsonPath("$.driverEmail").value("aryan@test.com"))
                .andExpect(jsonPath("$.parkingCountLimit").value(10));
    }

    @Test
    void shouldReturn404WhenNoPass() throws Exception {
        when(passService.getMyPass("aryan@test.com"))
                .thenThrow(new ResourceNotFoundException("No active pass found for aryan@test.com"));

        mockMvc.perform(get("/api/pass/my")
                        .principal(driverAuth()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/pass/transactions ────────────────────────────────────────────

    @Test
    void shouldGetMyTransactions() throws Exception {
        when(passService.getMyPassTransactions("aryan@test.com")).thenReturn(List.of(sampleTxn));

        mockMvc.perform(get("/api/pass/transactions")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value(10L))
                .andExpect(jsonPath("$[0].passTransactionRef").value("PASS-abc123"));
    }

    @Test
    void shouldReturnEmptyTransactions() throws Exception {
        when(passService.getMyPassTransactions("aryan@test.com")).thenReturn(List.of());

        mockMvc.perform(get("/api/pass/transactions")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/pass/transactions/current ────────────────────────────────────

    @Test
    void shouldGetCurrentPassTransactions() throws Exception {
        when(passService.getMyCurrentPassTransactions("aryan@test.com")).thenReturn(List.of(sampleTxn));

        mockMvc.perform(get("/api/pass/transactions/current")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(100L))
                .andExpect(jsonPath("$[0].amount").value(50.0));
    }

    // ── POST /api/pass/pay ────────────────────────────────────────────────────

    @Test
    void shouldPayWithPassSuccessfully() throws Exception {
        PayWithPassRequest req = new PayWithPassRequest();
        req.setBookingId(100L);
        req.setAmount(50.0);

        when(passService.payWithPass(any(PayWithPassRequest.class), eq("aryan@test.com")))
                .thenReturn(sampleTxn);

        mockMvc.perform(post("/api/pass/pay")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(10L))
                .andExpect(jsonPath("$.countAfter").value(6));
    }

    @Test
    void shouldReturn409WhenPassExhausted() throws Exception {
        PayWithPassRequest req = new PayWithPassRequest();
        req.setBookingId(100L);
        req.setAmount(50.0);

        when(passService.payWithPass(any(), anyString()))
                .thenThrow(new PaymentException("No remaining pass counts."));

        mockMvc.perform(post("/api/pass/pay")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No remaining pass counts."));
    }

    // ── GET /api/pass/transactions/booking/{bookingId} ──────────────────────

    @Test
    void shouldGetPassTransactionByBooking() throws Exception {
        when(passService.getPassTransactionByBooking(100L, "aryan@test.com")).thenReturn(sampleTxn);

        mockMvc.perform(get("/api/pass/transactions/booking/100")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(100L));
    }

    @Test
    void shouldReturn404WhenPassTransactionNotFound() throws Exception {
        when(passService.getPassTransactionByBooking(999L, "aryan@test.com"))
                .thenThrow(new ResourceNotFoundException("No pass transaction for booking: 999"));

        mockMvc.perform(get("/api/pass/transactions/booking/999")
                        .principal(driverAuth()))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/pass/cancel ─────────────────────────────────────────────────

    @Test
    void shouldCancelPassSuccessfully() throws Exception {
        doNothing().when(passService).cancelPass("aryan@test.com");

        mockMvc.perform(post("/api/pass/cancel")
                        .principal(driverAuth()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn409WhenCancellingExpiredPass() throws Exception {
        doThrow(new PaymentException("Pass is not active — cannot cancel."))
                .when(passService).cancelPass("aryan@test.com");

        mockMvc.perform(post("/api/pass/cancel")
                        .principal(driverAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Pass is not active — cannot cancel."));
    }

    // ── GET /api/pass/transactions/{txnId}/receipt ─────────────────────────

    @Test
    void shouldDownloadPassReceiptSuccessfully() throws Exception {
        File temp = File.createTempFile("pass_receipt", ".pdf");
        temp.deleteOnExit();
        when(passService.getPassReceiptPath(10L, "aryan@test.com")).thenReturn(temp.getAbsolutePath());

        mockMvc.perform(get("/api/pass/transactions/10/receipt")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=pass_receipt_10.pdf"));
    }

    @Test
    void shouldReturn404WhenPassReceiptNotFound() throws Exception {
        when(passService.getPassReceiptPath(10L, "aryan@test.com")).thenReturn("/nonexistent/path.pdf");

        mockMvc.perform(get("/api/pass/transactions/10/receipt")
                        .principal(driverAuth()))
                .andExpect(status().isNotFound());
    }

    // ── Direct controller tests for edge cases ────────────────────────────────

    @Test
    void createPassOrderReturns200WithDTO() {
        OrderResponseDTO orderResp = new OrderResponseDTO();
        orderResp.setRazorpayOrderId("order_xyz");
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.getPrincipal()).thenReturn("aryan@test.com");
        when(passService.createPassOrder("aryan@test.com")).thenReturn(orderResp);

        ResponseEntity<OrderResponseDTO> response = controller.createPassOrder(auth);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("order_xyz", response.getBody().getRazorpayOrderId());
    }
}
