package com.parkease.parkingspot.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.parkease.parkingspot.client.LotServiceClient;
import com.parkease.parkingspot.dto.request.BulkSpotRequestDTO;
import com.parkease.parkingspot.dto.request.SpotRequestDTO;
import com.parkease.parkingspot.dto.response.SpotResponseDTO;
import com.parkease.parkingspot.entity.ParkingSpot;
import com.parkease.parkingspot.entity.SpotStatus;
import com.parkease.parkingspot.entity.SpotType;
import com.parkease.parkingspot.entity.VehicleType;
import com.parkease.parkingspot.exception.ResourceNotFoundException;
import com.parkease.parkingspot.exception.SpotNotAvailableException;
import com.parkease.parkingspot.mapper.SpotMapper;
import com.parkease.parkingspot.repository.SpotRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotServiceImpl implements SpotService {

    private final SpotRepository repo;
    private final SpotMapper mapper;
    private final LotServiceClient lotClient;

    // Convert VehicleType enum to canonical key matching lot's format (e.g., "TWO_WHEELER")
    private static String getVehicleTypeKey(VehicleType vehicleType) {
        if (vehicleType == null) return null;
        return vehicleType.name(); // Returns "TWO_WHEELER", "FOUR_WHEELER", or "HEAVY"
    }

    // Convert canonical key to friendly label for display in error messages
    private static String getFriendlyVehicleLabel(String typeKey) {
        if (typeKey == null) return null;
        return switch (typeKey) {
            case "TWO_WHEELER" -> "two-wheeler";
            case "FOUR_WHEELER" -> "four-wheeler";
            case "HEAVY" -> "heavy vehicle";
            default -> typeKey;
        };
    }

    private void validateVehicleType(Long lotId, VehicleType vehicleType) {
        try {
            LotServiceClient.LotInfo lotInfo = lotClient.getLotById(lotId);
            if (lotInfo == null || lotInfo.getVehicleTypes() == null || lotInfo.getVehicleTypes().isEmpty()) {
                log.warn("Lot {} has no vehicle types defined, allowing spot creation", lotId);
                return;
            }

            // Use canonical key for comparison (e.g., "TWO_WHEELER")
            String lotKey = getVehicleTypeKey(vehicleType);
            List<String> supportedTypes = lotInfo.getVehicleTypes();

            if (lotKey != null && !supportedTypes.contains(lotKey)) {
                // Use friendly labels for display
                String friendlyType = getFriendlyVehicleLabel(lotKey);
                List<String> friendlySupportedList = supportedTypes.stream()
                        .map(SpotServiceImpl::getFriendlyVehicleLabel)
                        .toList();
                String friendlySupported = String.join(", ", friendlySupportedList);

                throw new IllegalArgumentException(
                    "Cannot create spot: this lot does not allow " + friendlyType + ". " +
                    "Allowed types: [" + friendlySupported + "]");
            }
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            log.warn("Could not validate vehicle type against lot {}: {}", lotId, e.getMessage());
        }
    }

    private void validateTotalSpotsLimit(Long lotId, int numberOfNewSpots) {
        try {
            LotServiceClient.LotInfo lotInfo = lotClient.getLotById(lotId);
            if (lotInfo == null) {
                log.warn("Lot {} not found, skipping total spots validation", lotId);
                return;
            }

            Integer totalSpots = lotInfo.getTotalSpots();
            // If totalSpots is null or <= 0, treat as no limit (backward compatibility)
            if (totalSpots == null || totalSpots <= 0) {
                log.debug("Lot {} has no totalSpots limit set, allowing creation", lotId);
                return;
            }

            long currentSpots = repo.countByLotId(lotId);
            int allowedRemaining = totalSpots - (int) currentSpots;

            if (numberOfNewSpots > allowedRemaining) {
                String message;
                if (allowedRemaining <= 0) {
                    message = "Cannot create spots: this lot has reached its total spot limit. " +
                             "Total spots: " + totalSpots + ", existing spots: " + currentSpots +
                             ". You cannot add any more spots.";
                } else {
                    message = "Cannot create spots: this operation would exceed the total spot limit for this lot. " +
                             "Total spots: " + totalSpots + ", existing spots: " + currentSpots +
                             ". You can only add " + allowedRemaining + " more spot(s).";
                }
                throw new IllegalArgumentException(message);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not validate total spots limit for lot {}: {}", lotId, e.getMessage());
        }
    }
 
    private ParkingSpot getSpotOrThrow(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Spot not found with id: " + id));
    }
 
    @Override
    @Transactional
    public SpotResponseDTO addSpot(SpotRequestDTO dto) {
        log.info("Adding spot {} to lot {}", dto.getSpotNumber(), dto.getLotId());

        // Validate total spots limit
        validateTotalSpotsLimit(dto.getLotId(), 1);

        // Validate vehicle type against lot's supported types
        validateVehicleType(dto.getLotId(), dto.getVehicleType());

        if (repo.existsByLotIdAndSpotNumber(dto.getLotId(), dto.getSpotNumber())) {
            throw new IllegalArgumentException(
                "Spot number '" + dto.getSpotNumber() + "' already exists in lot " + dto.getLotId());
        }

        ParkingSpot saved = repo.save(mapper.toEntity(dto));
        log.info("Spot created with id: {}", saved.getSpotId());
        return mapper.toDTO(saved);
    }
 
    @Override
    @Transactional
    public List<SpotResponseDTO> addBulkSpots(BulkSpotRequestDTO dto) {
        log.info("Bulk creating {} spots in lot {} (prefix={})", dto.getCount(), dto.getLotId(), dto.getPrefix());

        // Validate total spots limit
        validateTotalSpotsLimit(dto.getLotId(), dto.getCount());

        // Validate vehicle type against lot's supported types (rejected if invalid)
        validateVehicleType(dto.getLotId(), dto.getVehicleType());

        List<ParkingSpot> spots = new ArrayList<>();

        for (int i = 1; i <= dto.getCount(); i++) {
            String spotNumber = dto.getPrefix() + dto.getFloor() + "-" + String.format("%02d", i);
 
            if (repo.existsByLotIdAndSpotNumber(dto.getLotId(), spotNumber)) {
                log.warn("Spot {} already exists in lot {}, skipping", spotNumber, dto.getLotId());
                continue;
            }
 
            ParkingSpot spot = ParkingSpot.builder()
                    .lotId(dto.getLotId())
                    .spotNumber(spotNumber)
                    .floor(dto.getFloor())
                    .spotType(dto.getSpotType())
                    .vehicleType(dto.getVehicleType())
                    .status(SpotStatus.AVAILABLE)
                    .isEVCharging(dto.isEVCharging())
                    .isHandicapped(dto.isHandicapped())
                    .pricePerHour(dto.getPricePerHour())
                    .build();
 
            spots.add(spot);
        }
 
        List<ParkingSpot> saved = repo.saveAll(spots);
        log.info("Bulk created {} spots in lot {}", saved.size(), dto.getLotId());
        return saved.stream().map(mapper::toDTO).toList();
    }
    @Override
    @Transactional
    public SpotResponseDTO updateSpot(Long spotId, SpotRequestDTO dto) {
        log.info("Updating spot id: {}", spotId);
        ParkingSpot spot = getSpotOrThrow(spotId);

        // Validate vehicle type against lot's supported types
        validateVehicleType(spot.getLotId(), dto.getVehicleType());

        spot.setSpotNumber(dto.getSpotNumber());
        spot.setFloor(dto.getFloor());
        spot.setSpotType(dto.getSpotType());
        spot.setVehicleType(dto.getVehicleType());
        spot.setEVCharging(dto.isEVCharging());
        spot.setHandicapped(dto.isHandicapped());
        spot.setPricePerHour(dto.getPricePerHour());

        return mapper.toDTO(repo.save(spot));
    }
    @Override
    @Transactional
    public void deleteSpot(Long spotId) {
        log.info("Deleting spot id: {}", spotId);
        ParkingSpot spot = getSpotOrThrow(spotId);
 
        if (spot.getStatus() == SpotStatus.RESERVED) {
            throw new SpotNotAvailableException(
                "Cannot delete spot " + spotId + " — it is currently " + spot.getStatus());
        }
 
        repo.delete(spot);
    }
    @Override
    public SpotResponseDTO getSpotById(Long spotId) {
        return mapper.toDTO(getSpotOrThrow(spotId));
    }
 
    @Override
    public List<SpotResponseDTO> getSpotsByLot(Long lotId) {
        return repo.findByLotId(lotId).stream().map(mapper::toDTO).toList();
    }
 
    @Override
    public List<SpotResponseDTO> getAvailableSpots(Long lotId) {
        return repo.findByLotIdAndStatus(lotId, SpotStatus.AVAILABLE)
                .stream().map(mapper::toDTO).toList();
    }
 
    @Override
    public List<SpotResponseDTO> getSpotsByType(Long lotId, SpotType spotType) {
        return repo.findByLotIdAndSpotType(lotId, spotType)
                .stream().map(mapper::toDTO).toList();
    }
 
    @Override
    public List<SpotResponseDTO> getSpotsByVehicleType(Long lotId, VehicleType vehicleType) {
        return repo.findByLotIdAndVehicleType(lotId, vehicleType)
                .stream().map(mapper::toDTO).toList();
    }
 
    @Override
    public List<SpotResponseDTO> getSpotsByFloor(Long lotId, int floor) {
        return repo.findByLotIdAndFloor(lotId, floor)
                .stream().map(mapper::toDTO).toList();
    }
 
    @Override
    public List<SpotResponseDTO> getEVSpots(Long lotId) {
        return repo.findByLotIdAndIsEVChargingTrue(lotId)
                .stream().map(mapper::toDTO).toList();
    }
 
    @Override
    public List<SpotResponseDTO> getHandicappedSpots(Long lotId) {
        return repo.findByLotIdAndIsHandicappedTrue(lotId)
                .stream().map(mapper::toDTO).toList();
    }
 
    @Override
    public int countAvailableSpots(Long lotId) {
        return repo.countByLotIdAndStatus(lotId, SpotStatus.AVAILABLE);
    }

    @Override
    public long getTotalSpots(Long lotId) {
        return repo.countByLotId(lotId);
    }

    @Override
    public int countReservedSpots(Long lotId) {
        return repo.countByLotIdAndStatus(lotId, SpotStatus.RESERVED);
    }
 
    @Override
    @Transactional
    public SpotResponseDTO reserveSpot(Long spotId) {
        log.info("Reserving spot id: {}", spotId);
        ParkingSpot spot = getSpotOrThrow(spotId);
 
        if (spot.getStatus() != SpotStatus.AVAILABLE) {
            throw new SpotNotAvailableException(
                "Spot " + spotId + " is not available. Current status: " + spot.getStatus());
        }
 
        spot.setStatus(SpotStatus.RESERVED);
        return mapper.toDTO(repo.save(spot));
    }
    @Override
    @Transactional
    public SpotResponseDTO releaseSpot(Long spotId) {
        log.info("Releasing spot id: {}", spotId);
        ParkingSpot spot = getSpotOrThrow(spotId);
 
        if (spot.getStatus() == SpotStatus.AVAILABLE) {
            log.warn("Spot {} is already available", spotId);
            return mapper.toDTO(spot);
        }
 
        spot.setStatus(SpotStatus.AVAILABLE);
        return mapper.toDTO(repo.save(spot));
    }
}
