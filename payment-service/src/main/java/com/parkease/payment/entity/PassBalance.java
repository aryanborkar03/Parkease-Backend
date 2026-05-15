package com.parkease.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pass_balances")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long passId;

    @Column(nullable = false, unique = true)
    private String driverEmail;

    private String razorpayOrderId;
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private PassStatus status;

    private int parkingCountLimit;
    private int parkingCountUsed;

    private LocalDateTime purchasedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime cancelledAt;
}
