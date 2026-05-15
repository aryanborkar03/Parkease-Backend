package com.parkease.parkinglot.service;

import com.parkease.parkinglot.dto.request.ParkingLotRequestDTO;
import com.parkease.parkinglot.dto.response.ParkingLotResponseDTO;

import java.util.List;

public interface ParkingLotService {

    // Manager operations
    ParkingLotResponseDTO createLot(ParkingLotRequestDTO dto, String managerEmail);
    ParkingLotResponseDTO updateLot(Long id, ParkingLotRequestDTO dto, String managerEmail);
    void deleteLot(Long id, String managerEmail);
    ParkingLotResponseDTO toggleOpen(Long id, String managerEmail);
    List<ParkingLotResponseDTO> getLotsByManager(String managerEmail);

    // Public and driver operations
    ParkingLotResponseDTO getLotById(Long id);
    List<ParkingLotResponseDTO> getByCity(String city, String vehicleType, Boolean isEv, Boolean isHandicapped);
    List<ParkingLotResponseDTO> getNearbyLots(double lat, double lon, double radiusKm, String vehicleType, Boolean isEv, Boolean isHandicapped);
    List<ParkingLotResponseDTO> getOpenLots();

    // Admin operations
    ParkingLotResponseDTO approveLot(Long id);
    ParkingLotResponseDTO rejectLot(Long id);
    List<ParkingLotResponseDTO> getPendingApprovalLots();

    // Internal booking service integration operations
    void decrementSpot(Long lotId);
    void incrementSpot(Long lotId);
}
