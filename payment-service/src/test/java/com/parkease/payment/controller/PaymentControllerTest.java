package com.parkease.payment.controller;

import com.parkease.payment.dto.request.CreateOrderRequest;
import com.parkease.payment.dto.request.RefundRequest;
import com.parkease.payment.dto.request.VerifyPaymentRequest;
import com.parkease.payment.dto.response.ApiResponse;
import com.parkease.payment.dto.response.OrderResponseDTO;
import com.parkease.payment.dto.response.PaymentResponseDTO;
import com.parkease.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService service;

    @InjectMocks
    private PaymentController controller;

    @Mock
    private Authentication auth;

    private PaymentResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        responseDTO = new PaymentResponseDTO();
    }

    @Test
    void createOrder() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setBookingId(1L);
        when(auth.getPrincipal()).thenReturn("test@test.com");
        OrderResponseDTO orderResp = new OrderResponseDTO();
        when(service.createOrder(any(), eq("test@test.com"))).thenReturn(orderResp);

        ResponseEntity<OrderResponseDTO> res = controller.createOrder(req, auth);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertEquals(orderResp, res.getBody());
    }

    @Test
    void verifyPayment() {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        when(service.verifyPayment(any())).thenReturn(responseDTO);

        ResponseEntity<PaymentResponseDTO> res = controller.verifyPayment(req);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(responseDTO, res.getBody());
    }

    @Test
    void refund() {
        RefundRequest req = new RefundRequest();
        when(service.refundPayment(any())).thenReturn(responseDTO);

        ResponseEntity<PaymentResponseDTO> res = controller.refund(req);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(responseDTO, res.getBody());
    }

    @Test
    void getMyPayments() {
        when(auth.getPrincipal()).thenReturn("test@test.com");
        when(service.getMyPayments("test@test.com")).thenReturn(List.of(responseDTO));

        ResponseEntity<List<PaymentResponseDTO>> res = controller.getMyPayments(auth);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());
    }

    @Test
    void getByBooking() {
        when(service.getPaymentByBookingId(1L)).thenReturn(responseDTO);
        ResponseEntity<PaymentResponseDTO> res = controller.getByBooking(1L);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(responseDTO, res.getBody());
    }

    @Test
    void getById() {
        when(service.getPaymentById(1L)).thenReturn(responseDTO);
        ResponseEntity<PaymentResponseDTO> res = controller.getById(1L);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(responseDTO, res.getBody());
    }

    @Test
    void downloadReceipt_Success() throws IOException {
        File temp = File.createTempFile("receipt", ".pdf");
        temp.deleteOnExit();

        when(auth.getPrincipal()).thenReturn("test@test.com");
        when(service.getReceiptPath(1L, "test@test.com")).thenReturn(temp.getAbsolutePath());

        ResponseEntity<Resource> res = controller.downloadReceipt(1L, auth);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
    }

    @Test
    void downloadReceipt_NotFound() {
        when(auth.getPrincipal()).thenReturn("test@test.com");
        when(service.getReceiptPath(1L, "test@test.com")).thenReturn("invalid_path.pdf");

        ResponseEntity<Resource> res = controller.downloadReceipt(1L, auth);
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    void getAllPayments() {
        when(service.getAllPayments()).thenReturn(List.of(responseDTO));
        ResponseEntity<List<PaymentResponseDTO>> res = controller.getAllPayments();
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());
    }

    @Test
    void handleWebhook() {
        ResponseEntity<ApiResponse> res = controller.handleWebhook("payload", "signature");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().isSuccess());
    }
}
