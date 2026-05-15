package com.parkease.parkinglot.mapper;

import com.parkease.parkinglot.dto.request.ParkingLotRequestDTO;
import com.parkease.parkinglot.dto.response.ParkingLotResponseDTO;
import com.parkease.parkinglot.entity.ParkingLot;
import org.springframework.stereotype.Component;

@Component
public class ParkingLotMapper {

    public ParkingLot toEntity(ParkingLotRequestDTO dto, String managerEmail) {
        return ParkingLot.builder()
                .name(dto.getName())
                .address(dto.getAddress())
                .city(dto.getCity())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .totalSpots(dto.getTotalSpots())
                .availableSpots(dto.getTotalSpots())  
                .managerEmail(managerEmail)
                .managerName(dto.getManagerName())
                .isOpen(false)                     
                .isApproved(false)                    
                .openTime(dto.getOpenTime())
                .closeTime(dto.getCloseTime())
                .imageUrl(dto.getImageUrl())
                .vehicleTypes(dto.getVehicleTypes())
                .isEv(dto.isEv())
                .isHandicapped(dto.isHandicapped())
                .build();
    }

    public ParkingLotResponseDTO toDTO(ParkingLot lot) {
        return ParkingLotResponseDTO.builder()
                .lotId(lot.getLotId())
                .name(lot.getName())
                .address(lot.getAddress())
                .city(lot.getCity())
                .latitude(lot.getLatitude())
                .longitude(lot.getLongitude())
                .totalSpots(lot.getTotalSpots())
                .availableSpots(lot.getAvailableSpots())
                .managerName(lot.getManagerName() != null && !lot.getManagerName().isEmpty() 
                             ? lot.getManagerName() 
                             : lot.getManagerEmail().split("@")[0])
                .managerEmail(lot.getManagerEmail())
                .isOpen(lot.isOpen())
                .isApproved(lot.isApproved())
                .openTime(lot.getOpenTime())
                .closeTime(lot.getCloseTime())
                .imageUrl(lot.getImageUrl())
                .createdAt(lot.getCreatedAt())
                .vehicleTypes(lot.getVehicleTypes())
                .isEv(lot.isEv())
                .isHandicapped(lot.isHandicapped())
                .build();
    }

    public ParkingLotResponseDTO toDTO(ParkingLot lot, double distanceKm) {
        ParkingLotResponseDTO dto = toDTO(lot);
        dto.setDistanceKm(Math.round(distanceKm * 100.0) / 100.0);
        return dto;
    }
}
