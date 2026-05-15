package com.parkease.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pass_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @Column(nullable = false)
    private Long passId;

    @Column(nullable = false)
    private String driverEmail;

    @Column(nullable = false)
    private Long bookingId;

    private double amount;

    private int countBefore;
    private int countAfter;

    private String passTransactionRef;
    private String receiptPath;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
