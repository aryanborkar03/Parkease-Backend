package com.parkease.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "VEHICLE-SERVICE", fallback = VehicleServiceClient.VehicleServiceFallback.class)
public interface VehicleServiceClient {

    @GetMapping("/api/vehicles/{id}")
    Map<String, Object> getVehicleById(@PathVariable("id") Long id);

    @Component
    class VehicleServiceFallback implements VehicleServiceClient {

        @Override
        public Map<String, Object> getVehicleById(Long id) {
            // Vehicle service unavailable - return null (booking will handle the error)
            return null;
        }
    }
}