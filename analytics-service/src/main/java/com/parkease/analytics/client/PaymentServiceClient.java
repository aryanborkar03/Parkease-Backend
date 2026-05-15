package com.parkease.analytics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@FeignClient(name = "PAYMENT-SERVICE", fallback = PaymentServiceClient.PaymentServiceFallback.class)
public interface PaymentServiceClient {

    @GetMapping("/api/payments/admin/all")
    List<Map<String, Object>> getAllPayments(
            @RequestHeader("Authorization") String authorization);

    // ── Fallback implementation ───────────────────────────────────────────────
    @Component
    class PaymentServiceFallback implements PaymentServiceClient {

        private static final Logger log = LoggerFactory.getLogger(PaymentServiceFallback.class);

        @Override
        public List<Map<String, Object>> getAllPayments(String authorization) {
            log.warn("⚡ Circuit Breaker OPEN — Payment service is down. " +
                     "Could not fetch payments.");
            return Collections.emptyList();
        }
    }
}
