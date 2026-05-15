package com.parkease.vehicle.dto.response;
 
import com.parkease.vehicle.entity.VehicleType;
import lombok.*;
import java.time.LocalDateTime;
 
@Data 
@Builder 
@NoArgsConstructor 
@AllArgsConstructor
public class VehicleResponseDTO {
    private Long vehicleId;
    private String ownerEmail;
    private String licensePlate;
    private String make;
    private String model;
    private String color;
    private VehicleType vehicleType;
    @com.fasterxml.jackson.annotation.JsonProperty("isEV")
    private boolean isEV;
    
    @com.fasterxml.jackson.annotation.JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime registeredAt;
}