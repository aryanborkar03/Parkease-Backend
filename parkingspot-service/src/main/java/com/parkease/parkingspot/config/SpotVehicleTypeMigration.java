package com.parkease.parkingspot.config;

import com.parkease.parkingspot.entity.ParkingSpot;
import com.parkease.parkingspot.entity.SpotType;
import com.parkease.parkingspot.entity.VehicleType;
import com.parkease.parkingspot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time migration to set default vehicle type for existing spots.
 * This runs once on startup and does NOT touch spots that already have a non-default vehicle type.
 *
 * Mapping: STANDARD spots (with null/standard default) → TWO_WHEELER
 * Other spot types are left unchanged.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpotVehicleTypeMigration implements CommandLineRunner {

    private final SpotRepository repo;

    @Override
    @Transactional
    public void run(String... args) {
        // Only migrate spots that are STANDARD and have no vehicle type set (null)
        List<ParkingSpot> spotsToMigrate = repo.findAll().stream()
                .filter(s -> s.getSpotType() == SpotType.STANDARD && s.getVehicleType() == null)
                .toList();

        if (spotsToMigrate.isEmpty()) {
            log.info("No STANDARD spots with null vehicleType found - migration skipped.");
            return;
        }

        log.warn("Migrating {} existing STANDARD spots to TWO_WHEELER vehicle type...", spotsToMigrate.size());
        for (ParkingSpot spot : spotsToMigrate) {
            spot.setVehicleType(VehicleType.TWO_WHEELER);
            repo.save(spot);
        }
        log.info("Migration complete: {} spots updated to TWO_WHEELER.", spotsToMigrate.size());
    }
}