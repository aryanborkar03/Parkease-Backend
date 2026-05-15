package com.parkease.payment.mapper;

import com.parkease.payment.dto.response.PaymentResponseDTO;
import com.parkease.payment.entity.Payment;
import com.parkease.payment.entity.PaymentMode;
import com.parkease.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapper();

    @Test
    void toDTO() {
        LocalDateTime now = LocalDateTime.now();
        Payment p = Payment.builder()
                .paymentId(1L)
                .bookingId(100L)
                .driverEmail("test@test.com")
                .amount(50.0)
                .currency("INR")
                .status(PaymentStatus.PAID)
                .mode(PaymentMode.CARD)
                .razorpayOrderId("order_1")
                .razorpayPaymentId("pay_1")
                .razorpayRefundId("ref_1")
                .description("Desc")
                .createdAt(now)
                .paidAt(now)
                .refundedAt(now)
                .build();

        PaymentResponseDTO dto = mapper.toDTO(p);

        assertEquals(1L, dto.getPaymentId());
        assertEquals(100L, dto.getBookingId());
        assertEquals("test@test.com", dto.getDriverEmail());
        assertEquals(50.0, dto.getAmount());
        assertEquals("INR", dto.getCurrency());
        assertEquals(PaymentStatus.PAID, dto.getStatus());
        assertEquals(PaymentMode.CARD, dto.getMode());
        assertEquals("order_1", dto.getRazorpayOrderId());
        assertEquals("pay_1", dto.getRazorpayPaymentId());
        assertEquals("ref_1", dto.getRazorpayRefundId());
        assertEquals("Desc", dto.getDescription());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getPaidAt());
        assertEquals(now, dto.getRefundedAt());
    }
}
