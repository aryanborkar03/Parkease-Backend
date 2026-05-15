package com.parkease.parkingspot.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collections;
import java.util.List;

@FeignClient(name = "PARKINGLOT-SERVICE", fallback = LotServiceClient.LotServiceFallback.class)
public interface LotServiceClient {

    @GetMapping("/api/lots/{lotId}")
    LotInfo getLotById(@PathVariable("lotId") Long lotId);

    /**
     * Simplified DTO to fetch lot details including totalSpots for limit validation
     */
    class LotInfo {
        private Long lotId;
        private List<String> vehicleTypes;
        private String name;
        private Integer totalSpots;

        public Long getLotId() { return lotId; }
        public void setLotId(Long lotId) { this.lotId = lotId; }
        public List<String> getVehicleTypes() { return vehicleTypes; }
        public void setVehicleTypes(List<String> vehicleTypes) { this.vehicleTypes = vehicleTypes; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getTotalSpots() { return totalSpots; }
        public void setTotalSpots(Integer totalSpots) { this.totalSpots = totalSpots; }
    }

    @Component
    class LotServiceFallback implements LotServiceClient {
        private static final Logger log = LoggerFactory.getLogger(LotServiceFallback.class);

        @Override
        public LotInfo getLotById(Long lotId) {
            log.error("⚡ Circuit Breaker OPEN — ParkingLot service is down. Could not fetch lot: {}", lotId);
            return null;
        }
    }
}