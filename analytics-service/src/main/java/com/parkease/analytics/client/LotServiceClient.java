package com.parkease.analytics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@FeignClient(name = "PARKINGLOT-SERVICE", fallback = LotServiceClient.LotServiceFallback.class)
public interface LotServiceClient {

    @GetMapping("/api/lots/manager")
    List<Map<String, Object>> getLotsByManager(
            @RequestParam("email") String email,
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/api/lots")
    List<Map<String, Object>> getAllLots(
            @RequestHeader("Authorization") String authorization);

    // ── Fallback implementation ───────────────────────────────────────────────
    @Component
    class LotServiceFallback implements LotServiceClient {

        private static final Logger log = LoggerFactory.getLogger(LotServiceFallback.class);

        @Override
        public List<Map<String, Object>> getLotsByManager(String email, String authorization) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingLot service is down. " +
                     "Could not fetch lots for manager: {}", email);
            return Collections.emptyList();
        }

        @Override
        public List<Map<String, Object>> getAllLots(String authorization) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingLot service is down. " +
                     "Could not fetch all lots.");
            return Collections.emptyList();
        }
    }
}
