package com.parkease.analytics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@FeignClient(name = "PARKINGSPOT-SERVICE", fallback = SpotServiceClient.SpotServiceFallback.class)
public interface SpotServiceClient {

    @GetMapping("/api/spots/lot/{lotId}/count")
    Integer getAvailableSpotCount(@PathVariable("lotId") Long lotId);

    @GetMapping("/api/spots/lot/{lotId}")
    List<Map<String, Object>> getSpotsByLot(@PathVariable("lotId") Long lotId);

    // ── Fallback implementation ───────────────────────────────────────────────
    @Component
    class SpotServiceFallback implements SpotServiceClient {

        private static final Logger log = LoggerFactory.getLogger(SpotServiceFallback.class);

        @Override
        public Integer getAvailableSpotCount(Long lotId) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingSpot service is down. " +
                     "Could not fetch spot count for lotId: {}", lotId);
            return 0;
        }

        @Override
        public List<Map<String, Object>> getSpotsByLot(Long lotId) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingSpot service is down. " +
                     "Could not fetch spots for lotId: {}", lotId);
            return Collections.emptyList();
        }
    }
}
