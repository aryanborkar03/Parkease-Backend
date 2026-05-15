package com.parkease.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "PARKINGLOT-SERVICE", fallback = LotServiceClient.LotServiceFallback.class)
public interface LotServiceClient {

    @PutMapping("/api/lots/{lotId}/decrement")
    void decrementAvailable(@PathVariable("lotId") Long lotId);

    @PutMapping("/api/lots/{lotId}/increment")
    void incrementAvailable(@PathVariable("lotId") Long lotId);

    // ── Fallback implementation ───────────────────────────────────────────────
    @Component
    class LotServiceFallback implements LotServiceClient {

        private static final Logger log = LoggerFactory.getLogger(LotServiceFallback.class);

        @Override
        public void decrementAvailable(Long lotId) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingLot service is down. " +
                     "Could not decrement spot count for lotId: {}", lotId);
        }

        @Override
        public void incrementAvailable(Long lotId) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingLot service is down. " +
                     "Could not increment spot count for lotId: {}", lotId);
        }
    }
}
