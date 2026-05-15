package com.parkease.analytics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@FeignClient(name = "BOOKING-SERVICE", fallback = BookingServiceClient.BookingServiceFallback.class)
public interface BookingServiceClient {

    @GetMapping("/api/bookings/internal/lot-ids")
    List<Long> getDistinctLotIds();

    @GetMapping("/api/bookings/lot/{lotId}")
    List<Map<String, Object>> getBookingsByLot(
            @PathVariable("lotId") Long lotId,
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/api/bookings/admin/all")
    List<Map<String, Object>> getAllBookings(
            @RequestHeader("Authorization") String authorization);

    // ── Fallback implementation ───────────────────────────────────────────────
    @Component
    class BookingServiceFallback implements BookingServiceClient {

        private static final Logger log = LoggerFactory.getLogger(BookingServiceFallback.class);

        @Override
        public List<Long> getDistinctLotIds() {
            log.warn("⚡ Circuit Breaker OPEN — Booking service is down. " +
                     "Could not fetch lot IDs.");
            return Collections.emptyList();
        }

        @Override
        public List<Map<String, Object>> getBookingsByLot(Long lotId, String authorization) {
            log.warn("⚡ Circuit Breaker OPEN — Booking service is down. " +
                     "Could not fetch bookings for lotId: {}", lotId);
            return Collections.emptyList();
        }

        @Override
        public List<Map<String, Object>> getAllBookings(String authorization) {
            log.warn("⚡ Circuit Breaker OPEN — Booking service is down. " +
                     "Could not fetch all bookings.");
            return Collections.emptyList();
        }
    }
}
