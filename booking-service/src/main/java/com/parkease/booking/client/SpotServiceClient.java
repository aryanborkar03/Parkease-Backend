package com.parkease.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@FeignClient(name = "PARKINGSPOT-SERVICE", fallback = SpotServiceClient.SpotServiceFallback.class)
public interface SpotServiceClient {

    @GetMapping("/api/spots/{spotId}")
    Map<String, Object> getSpotById(@PathVariable("spotId") Long spotId);

    @GetMapping("/api/spots/lot/{lotId}")
    List<Map<String, Object>> getSpotsByLot(@PathVariable("lotId") Long lotId);

    @PutMapping("/api/spots/{spotId}/reserve")
    void reserveSpot(@PathVariable("spotId") Long spotId);

    @PutMapping("/api/spots/{spotId}/release")
    void releaseSpot(@PathVariable("spotId") Long spotId);

    // ── Fallback implementation ───────────────────────────────────────────────
    @Component
    class SpotServiceFallback implements SpotServiceClient {

        private static final Logger log = LoggerFactory.getLogger(SpotServiceFallback.class);

        @Override
        public Map<String, Object> getSpotById(Long spotId) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingSpot service is down. " +
                     "Could not fetch spot details for spotId: {}", spotId);
            return Collections.emptyMap();
        }

        @Override
        public List<Map<String, Object>> getSpotsByLot(Long lotId) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingSpot service is down. " +
                     "Could not fetch spots for lotId: {}", lotId);
            return Collections.emptyList();
        }

        @Override
        public void reserveSpot(Long spotId) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingSpot service is down. " +
                     "Could not reserve spotId: {}", spotId);
        }

        @Override
        public void releaseSpot(Long spotId) {
            log.warn("⚡ Circuit Breaker OPEN — ParkingSpot service is down. " +
                     "Could not release spotId: {}", spotId);
        }
    }
}
