package com.parkease.payment.dto.response;

import com.parkease.payment.entity.PassStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassBalanceDTO {
    private Long passId;
    private String driverEmail;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private PassStatus status;
    private int parkingCountLimit;
    private int parkingCountUsed;
    private int parkingCountRemaining;
    private int transactionCount;
    private LocalDateTime purchasedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime cancelledAt;
}
