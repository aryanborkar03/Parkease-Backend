package com.parkease.payment.service;

import com.parkease.payment.entity.Payment;
import com.parkease.payment.entity.PaymentMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @InjectMocks
    private ReceiptService receiptService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(receiptService, "storagePath", tempDir.toString() + File.separator);
    }

    @Test
    void shouldGenerateReceiptSuccessfully() {
        Payment payment = Payment.builder()
                .paymentId(1L)
                .bookingId(100L)
                .driverEmail("test@test.com")
                .amount(50.0)
                .currency("INR")
                .mode(PaymentMode.CARD)
                .razorpayPaymentId("pay_123")
                .razorpayOrderId("order_123")
                .paidAt(LocalDateTime.now())
                .description("Test Payment")
                .build();

        String result = receiptService.generateReceipt(payment);

        assertNotNull(result);
        assertTrue(result.contains("receipt_1.pdf"));
        File file = new File(result);
        assertTrue(file.exists());
    }

    @Test
    void shouldReturnNullWhenExceptionThrown() {
        ReflectionTestUtils.setField(receiptService, "storagePath", "Z:/non_existent_drive/invalid/");
        Payment payment = Payment.builder()
                .paymentId(1L)
                .driverEmail("test@test.com")
                .build();
        
        String result = receiptService.generateReceipt(payment);
        assertNull(result);
    }
}
