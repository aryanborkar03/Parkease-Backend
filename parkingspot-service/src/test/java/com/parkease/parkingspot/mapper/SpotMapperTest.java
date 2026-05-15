package com.parkease.parkingspot.mapper;

import com.parkease.parkingspot.dto.request.SpotRequestDTO;
import com.parkease.parkingspot.dto.response.SpotResponseDTO;
import com.parkease.parkingspot.entity.ParkingSpot;
import com.parkease.parkingspot.entity.SpotStatus;
import com.parkease.parkingspot.entity.SpotType;
import com.parkease.parkingspot.entity.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpotMapperTest {

    private SpotMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SpotMapper();
    }

    @Test
    void shouldMapRequestToEntity() {
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(10L);
        req.setSpotNumber("A1-01");
        req.setFloor(1);
        req.setSpotType(SpotType.STANDARD);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setEVCharging(false);
        req.setHandicapped(false);
        req.setPricePerHour(50.0);

        ParkingSpot entity = mapper.toEntity(req);

        assertNotNull(entity);
        assertEquals(10L, entity.getLotId());
        assertEquals("A1-01", entity.getSpotNumber());
        assertEquals(1, entity.getFloor());
        assertEquals(SpotType.STANDARD, entity.getSpotType());
        assertEquals(VehicleType.FOUR_WHEELER, entity.getVehicleType());
        assertEquals(SpotStatus.AVAILABLE, entity.getStatus());
        assertFalse(entity.isEVCharging());
        assertFalse(entity.isHandicapped());
        assertEquals(50.0, entity.getPricePerHour());
    }

    @Test
    void shouldMapEntityToDTO() {
        ParkingSpot spot = ParkingSpot.builder()
                .spotId(1L)
                .lotId(10L)
                .spotNumber("B2-05")
                .floor(2)
                .spotType(SpotType.LARGE)
                .vehicleType(VehicleType.FOUR_WHEELER)
                .status(SpotStatus.RESERVED)
                .isEVCharging(true)
                .isHandicapped(false)
                .pricePerHour(100.0)
                .build();

        SpotResponseDTO dto = mapper.toDTO(spot);

        assertNotNull(dto);
        assertEquals(1L, dto.getSpotId());
        assertEquals(10L, dto.getLotId());
        assertEquals("B2-05", dto.getSpotNumber());
        assertEquals(2, dto.getFloor());
        assertEquals(SpotType.LARGE, dto.getSpotType());
        assertEquals(VehicleType.FOUR_WHEELER, dto.getVehicleType());
        assertEquals(SpotStatus.RESERVED, dto.getStatus());
        assertTrue(dto.isEVCharging());
        assertFalse(dto.isHandicapped());
        assertEquals(100.0, dto.getPricePerHour());
    }

    @Test
    void shouldMapEVAndHandicappedSpot() {
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(5L);
        req.setSpotNumber("H1-01");
        req.setFloor(0);
        req.setSpotType(SpotType.STANDARD);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setEVCharging(true);
        req.setHandicapped(true);
        req.setPricePerHour(30.0);

        ParkingSpot entity = mapper.toEntity(req);

        assertTrue(entity.isEVCharging());
        assertTrue(entity.isHandicapped());
        assertEquals(SpotType.STANDARD, entity.getSpotType());
        assertEquals(SpotStatus.AVAILABLE, entity.getStatus());
    }
}
