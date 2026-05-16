package com.parkease.payment.service;

import com.parkease.payment.entity.Payment;
import com.parkease.payment.entity.PaymentMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    private PdfReceiptHelper helper;
    private ReceiptService receiptService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        helper = new PdfReceiptHelper();
        receiptService = new ReceiptService(helper);
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
        assertTrue(result.contains("ParkEase_Receipt_1.pdf"));
        File file = new File(result);
        assertTrue(file.exists());
    }

    @Test
    void shouldGenerateReceiptWithNullOptionalFields() {
        // Tests the null-guard branches: mode == null, razorpayPaymentId == null,
        // razorpayOrderId == null, paidAt == null, description == null
        Payment payment = Payment.builder()
                .paymentId(2L)
                .bookingId(200L)
                .driverEmail("driver@test.com")
                .amount(75.0)
                .currency("INR")
                .build();

        String result = receiptService.generateReceipt(payment);

        assertNotNull(result);
        assertTrue(new File(result).exists());
    }

    @Test
    void shouldGenerateReceiptWithBlankDescription() {
        // Tests the !description.isBlank() guard (blank description must NOT add a row)
        Payment payment = Payment.builder()
                .paymentId(3L)
                .bookingId(300L)
                .driverEmail("driver@test.com")
                .amount(100.0)
                .currency("INR")
                .description("   ")
                .build();

        String result = receiptService.generateReceipt(payment);

        assertNotNull(result);
        assertTrue(new File(result).exists());
    }

    @Test
    void shouldReturnNullWhenExceptionThrown() {
        // Provide an invalid path that cannot be written to
        ReflectionTestUtils.setField(receiptService, "storagePath", "Z:/non_existent_drive/invalid/");
        Payment payment = Payment.builder()
                .paymentId(1L)
                .driverEmail("test@test.com")
                .build();

        String result = receiptService.generateReceipt(payment);
        assertNull(result);
    }
}
