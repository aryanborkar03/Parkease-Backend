package com.parkease.parkinglot.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalTime;
import java.util.List;

@Data
public class ParkingLotRequestDTO {

    @NotBlank(message = "Lot name is required")
    private String name;

    @NotBlank(message = "Manager name is required")
    private String managerName;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0",  message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0",   message = "Latitude must be <= 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
    private Double longitude;

    @Min(value = 1, message = "Total spots must be at least 1")
    private int totalSpots;

    private LocalTime openTime;
    private LocalTime closeTime;

    private String imageUrl;

    private List<String> vehicleTypes;
    @JsonProperty("isEv")
    private boolean isEv;
    @JsonProperty("isHandicapped")
    private boolean isHandicapped;
}
