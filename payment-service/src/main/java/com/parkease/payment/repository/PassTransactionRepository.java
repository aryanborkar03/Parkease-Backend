package com.parkease.payment.repository;

import com.parkease.payment.entity.PassTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PassTransactionRepository extends JpaRepository<PassTransaction, Long> {
    List<PassTransaction> findByDriverEmailOrderByCreatedAtDesc(String driverEmail);
    List<PassTransaction> findByPassIdOrderByCreatedAtDesc(Long passId);
    Optional<PassTransaction> findByBookingId(Long bookingId);
}
