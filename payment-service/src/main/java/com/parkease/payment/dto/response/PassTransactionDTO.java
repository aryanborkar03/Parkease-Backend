package com.parkease.payment.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassTransactionDTO {

    private Long transactionId;
    private Long passId;
    private String driverEmail;
    private Long bookingId;
    private double amount;

    /**
     * The driver-facing Transaction ID — format: PASS-{UUID}.
     * Shown in UI and receipts, equivalent to razorpayPaymentId for regular payments.
     */
    private String passTransactionRef;

    private int countBefore;
    private int countAfter;
    private LocalDateTime createdAt;
}
