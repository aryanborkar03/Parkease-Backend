package com.parkease.parkinglot.mapper;

import com.parkease.parkinglot.dto.request.ParkingLotRequestDTO;
import com.parkease.parkinglot.dto.response.ParkingLotResponseDTO;
import com.parkease.parkinglot.entity.ParkingLot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParkingLotMapperTest {

    private ParkingLotMapper mapper;
    private ParkingLot lot;

    @BeforeEach
    void setUp() {
        mapper = new ParkingLotMapper();

        lot = ParkingLot.builder()
                .lotId(1L)
                .name("Central Park Lot")
                .address("MG Road")
                .city("Mumbai")
                .latitude(19.076)
                .longitude(72.877)
                .totalSpots(100)
                .availableSpots(50)
                .managerEmail("manager@test.com")
                .managerName("Test Manager")
                .isOpen(true)
                .isApproved(true)
                .openTime(LocalTime.of(8, 0))
                .closeTime(LocalTime.of(22, 0))
                .vehicleTypes(List.of("2W", "4W"))
                .isEv(true)
                .isHandicapped(false)
                .build();
    }

    @Test
    void shouldMapEntityToDTOCorrectly() {
        ParkingLotResponseDTO dto = mapper.toDTO(lot);

        assertNotNull(dto);
        assertEquals(lot.getLotId(), dto.getLotId());
        assertEquals(lot.getName(), dto.getName());
        assertEquals(lot.getCity(), dto.getCity());
        assertEquals(lot.getLatitude(), dto.getLatitude());
        assertEquals(lot.getLongitude(), dto.getLongitude());
        assertEquals(lot.getTotalSpots(), dto.getTotalSpots());
        assertEquals(lot.getAvailableSpots(), dto.getAvailableSpots());
        assertEquals(lot.getManagerEmail(), dto.getManagerEmail());
        assertTrue(dto.isOpen());
        assertTrue(dto.isApproved());
    }

    @Test
    void shouldMapToDTOWithDistance() {
        ParkingLotResponseDTO dto = mapper.toDTO(lot, 3.456789);

        assertEquals(3.46, dto.getDistanceKm());
    }

    @Test
    void shouldUseManagerNameWhenPresent() {
        ParkingLotResponseDTO dto = mapper.toDTO(lot);
        assertEquals("Test Manager", dto.getManagerName());
    }

    @Test
    void shouldFallbackToEmailPrefixWhenManagerNameEmpty() {
        lot.setManagerName("");
        ParkingLotResponseDTO dto = mapper.toDTO(lot);
        assertEquals("manager", dto.getManagerName());
    }

    @Test
    void shouldMapRequestDTOToEntity() {
        ParkingLotRequestDTO req = new ParkingLotRequestDTO();
        req.setName("New Lot");
        req.setAddress("FC Road");
        req.setCity("Pune");
        req.setLatitude(18.52);;
        req.setLongitude(73.85);
        req.setTotalSpots(50);
        req.setOpenTime(LocalTime.of(9, 0));
        req.setCloseTime(LocalTime.of(21, 0));

        ParkingLot entity = mapper.toEntity(req, "manager@test.com");

        assertNotNull(entity);
        assertEquals("New Lot", entity.getName());
        assertEquals("Pune", entity.getCity());
        assertEquals(50, entity.getTotalSpots());
        assertEquals(50, entity.getAvailableSpots());
        assertEquals("manager@test.com", entity.getManagerEmail());
        assertFalse(entity.isOpen());
        assertFalse(entity.isApproved());
    }

    @Test
    void shouldMapEvAndHandicappedFlags() {
        ParkingLotResponseDTO dto = mapper.toDTO(lot);
        assertTrue(dto.isEv());
        assertFalse(dto.isHandicapped());
    }
}
