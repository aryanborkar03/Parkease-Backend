package com.parkease.payment.repository;

import com.parkease.payment.entity.PassBalance;
import com.parkease.payment.entity.PassStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PassBalanceRepository extends JpaRepository<PassBalance, Long> {

    Optional<PassBalance> findFirstByDriverEmailOrderByPurchasedAtDesc(String email);

    Optional<PassBalance> findByDriverEmailAndStatus(String email, PassStatus status);
    
    Optional<PassBalance> findFirstByDriverEmailAndStatusInOrderByPurchasedAtDesc(String email, List<PassStatus> statuses);

    List<PassBalance> findAllByStatusAndExpiresAtBefore(PassStatus status, LocalDateTime now);

    List<PassBalance> findAllByDriverEmail(String email);

    List<PassBalance> findAllByDriverEmailAndStatusNot(String email, PassStatus status);
}
