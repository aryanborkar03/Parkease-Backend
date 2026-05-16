package com.parkease.parkingspot.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpotServiceImplTest {

    @Mock private SpotRepository repo;
    @Mock private SpotMapper mapper;
    @Mock private com.parkease.parkingspot.client.LotServiceClient lotClient;

    @InjectMocks
    private SpotServiceImpl service;

    private ParkingSpot sampleSpot;
    private SpotResponseDTO sampleDTO;

    @BeforeEach
    void setUp() {
        sampleSpot = ParkingSpot.builder()
                .spotId(1L)
                .lotId(10L)
                .spotNumber("A1-01")
                .floor(1)
                .spotType(SpotType.STANDARD)
                .vehicleType(VehicleType.FOUR_WHEELER)
                .status(SpotStatus.AVAILABLE)
                .isEVCharging(false)
                .isHandicapped(false)
                .pricePerHour(50.0)
                .build();

        sampleDTO = SpotResponseDTO.builder()
                .spotId(1L)
                .lotId(10L)
                .spotNumber("A1-01")
                .floor(1)
                .spotType(SpotType.STANDARD)
                .vehicleType(VehicleType.FOUR_WHEELER)
                .status(SpotStatus.AVAILABLE)
                .pricePerHour(50.0)
                .build();
    }

    @Test
    void shouldAddSpotSuccessfully() {
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(10L);
        req.setSpotNumber("A1-01");
        req.setFloor(1);
        req.setSpotType(SpotType.STANDARD);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setPricePerHour(50.0);

        when(repo.existsByLotIdAndSpotNumber(10L, "A1-01")).thenReturn(false);
        when(mapper.toEntity(req)).thenReturn(sampleSpot);
        when(repo.save(sampleSpot)).thenReturn(sampleSpot);
        when(mapper.toDTO(sampleSpot)).thenReturn(sampleDTO);

        SpotResponseDTO result = service.addSpot(req);

        assertNotNull(result);
        assertEquals("A1-01", result.getSpotNumber());
        verify(repo).save(sampleSpot);
    }

    @Test
    void shouldThrowWhenSpotAlreadyExists() {
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(10L);
        req.setSpotNumber("A1-01");

        when(repo.existsByLotIdAndSpotNumber(10L, "A1-01")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.addSpot(req));
    }

    @Test
    void shouldAddBulkSpotsSuccessfully() {
        BulkSpotRequestDTO req = new BulkSpotRequestDTO();
        req.setLotId(10L);
        req.setCount(3);
        req.setPrefix("A");
        req.setFloor(1);
        req.setSpotType(SpotType.STANDARD);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setPricePerHour(50.0);

        when(repo.existsByLotIdAndSpotNumber(anyLong(), anyString())).thenReturn(false);
        when(repo.saveAll(anyList())).thenReturn(List.of(sampleSpot, sampleSpot, sampleSpot));
        when(mapper.toDTO(any())).thenReturn(sampleDTO);

        List<SpotResponseDTO> result = service.addBulkSpots(req);

        assertEquals(3, result.size());
        verify(repo).saveAll(anyList());
    }

    @Test
    void shouldSkipExistingSpotsDuringBulkAdd() {
        BulkSpotRequestDTO req = new BulkSpotRequestDTO();
        req.setLotId(10L);
        req.setCount(2);
        req.setPrefix("B");
        req.setFloor(2);
        req.setSpotType(SpotType.STANDARD);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setPricePerHour(50.0);

        when(repo.existsByLotIdAndSpotNumber(10L, "B2-01")).thenReturn(true);
        when(repo.existsByLotIdAndSpotNumber(10L, "B2-02")).thenReturn(false);
        when(repo.saveAll(anyList())).thenReturn(List.of(sampleSpot));
        when(mapper.toDTO(any())).thenReturn(sampleDTO);

        List<SpotResponseDTO> result = service.addBulkSpots(req);

        assertEquals(1, result.size());
    }

    @Test
    void shouldUpdateSpot() {
        SpotRequestDTO req = new SpotRequestDTO();
        req.setSpotNumber("A1-02");
        req.setFloor(2);
        req.setSpotType(SpotType.LARGE);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setPricePerHour(80.0);

        when(repo.findById(1L)).thenReturn(Optional.of(sampleSpot));
        when(repo.save(sampleSpot)).thenReturn(sampleSpot);
        when(mapper.toDTO(sampleSpot)).thenReturn(sampleDTO);

        SpotResponseDTO result = service.updateSpot(1L, req);

        assertNotNull(result);
        verify(repo).save(sampleSpot);
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentSpot() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateSpot(99L, new SpotRequestDTO()));
    }

    @Test
    void shouldDeleteSpotSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleSpot));
        doNothing().when(repo).delete(sampleSpot);

        assertDoesNotThrow(() -> service.deleteSpot(1L));
        verify(repo).delete(sampleSpot);
    }

    @Test
    void shouldThrowWhenDeletingReservedSpot() {
        sampleSpot.setStatus(SpotStatus.RESERVED);
        when(repo.findById(1L)).thenReturn(Optional.of(sampleSpot));

        assertThrows(SpotNotAvailableException.class, () -> service.deleteSpot(1L));
    }

    @Test
    void shouldGetSpotById() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleSpot));
        when(mapper.toDTO(sampleSpot)).thenReturn(sampleDTO);

        SpotResponseDTO result = service.getSpotById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getSpotId());
    }

    @Test
    void shouldGetSpotsByLot() {
        when(repo.findByLotId(10L)).thenReturn(List.of(sampleSpot));
        when(mapper.toDTO(sampleSpot)).thenReturn(sampleDTO);

        List<SpotResponseDTO> result = service.getSpotsByLot(10L);

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetAvailableSpots() {
        when(repo.findByLotIdAndStatus(10L, SpotStatus.AVAILABLE))
                .thenReturn(List.of(sampleSpot));
        when(mapper.toDTO(sampleSpot)).thenReturn(sampleDTO);

        List<SpotResponseDTO> result = service.getAvailableSpots(10L);

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetSpotsByType() {
        when(repo.findByLotIdAndSpotType(10L, SpotType.STANDARD))
                .thenReturn(List.of(sampleSpot));
        when(mapper.toDTO(sampleSpot)).thenReturn(sampleDTO);

        List<SpotResponseDTO> result = service.getSpotsByType(10L, SpotType.STANDARD);

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetSpotsByVehicleType() {
        when(repo.findByLotIdAndVehicleType(10L, VehicleType.FOUR_WHEELER))
                .thenReturn(List.of(sampleSpot));
        when(mapper.toDTO(sampleSpot)).thenReturn(sampleDTO);

        List<SpotResponseDTO> result = service.getSpotsByVehicleType(10L, VehicleType.FOUR_WHEELER);

        assertEquals(1, result.size());
    }



    @Test
    void shouldCountAvailableSpots() {
        when(repo.countByLotIdAndStatus(10L, SpotStatus.AVAILABLE)).thenReturn(5);

        int count = service.countAvailableSpots(10L);

        assertEquals(5, count);
    }

    @Test
    void shouldReserveSpot() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleSpot));
        when(repo.save(sampleSpot)).thenReturn(sampleSpot);
        when(mapper.toDTO(sampleSpot)).thenReturn(sampleDTO);

        SpotResponseDTO result = service.reserveSpot(1L);

        assertNotNull(result);
        assertEquals(SpotStatus.RESERVED, sampleSpot.getStatus());
    }

    @Test
    void shouldThrowWhenReservingUnavailableSpot() {
        sampleSpot.setStatus(SpotStatus.RESERVED);
        when(repo.findById(1L)).thenReturn(Optional.of(sampleSpot));

        assertThrows(SpotNotAvailableException.class, () -> service.reserveSpot(1L));
    }

    @Test
    void shouldReleaseSpot() {
        sampleSpot.setStatus(SpotStatus.RESERVED);
        when(repo.findById(1L)).thenReturn(Optional.of(sampleSpot));
        when(repo.save(sampleSpot)).thenReturn(sampleSpot);
        when(mapper.toDTO(sampleSpot)).thenReturn(sampleDTO);

        SpotResponseDTO result = service.releaseSpot(1L);

        assertNotNull(result);
        assertEquals(SpotStatus.AVAILABLE, sampleSpot.getStatus());
    }


    // LotServiceClient validation branch coverage

    @Test
    void shouldThrowWhenVehicleTypeNotSupportedByLot() {
        // Lot only supports TWO_WHEELER — adding a FOUR_WHEELER spot should fail
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(10L);
        req.setSpotNumber("B1-01");
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setFloor(1);
        req.setSpotType(SpotType.STANDARD);
        req.setPricePerHour(40.0);

        com.parkease.parkingspot.client.LotServiceClient.LotInfo lotInfo =
                new com.parkease.parkingspot.client.LotServiceClient.LotInfo();
        lotInfo.setLotId(10L);
        lotInfo.setTotalSpots(50);
        lotInfo.setVehicleTypes(List.of("TWO_WHEELER"));

        when(lotClient.getLotById(10L)).thenReturn(lotInfo);
        when(repo.countByLotId(10L)).thenReturn(5L);

        assertThrows(IllegalArgumentException.class, () -> service.addSpot(req));
    }


    @Test
    void shouldThrowWhenLotIsAtFullCapacity() {
        // Lot already has all spots filled — any addition should fail
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(10L);
        req.setSpotNumber("F1-01");
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setFloor(1);
        req.setSpotType(SpotType.STANDARD);
        req.setPricePerHour(50.0);

        com.parkease.parkingspot.client.LotServiceClient.LotInfo lotInfo =
                new com.parkease.parkingspot.client.LotServiceClient.LotInfo();
        lotInfo.setLotId(10L);
        lotInfo.setTotalSpots(5);
        lotInfo.setVehicleTypes(List.of("FOUR_WHEELER"));

        when(lotClient.getLotById(10L)).thenReturn(lotInfo);
        when(repo.countByLotId(10L)).thenReturn(5L); // lot is full

        assertThrows(IllegalArgumentException.class, () -> service.addSpot(req));
    }

    @Test
    void shouldGetTotalSpots() {
        when(repo.countByLotId(10L)).thenReturn(15L);

        long total = service.getTotalSpots(10L);

        assertEquals(15L, total);
    }

    @Test
    void shouldCountReservedSpots() {
        when(repo.countByLotIdAndStatus(10L, SpotStatus.RESERVED)).thenReturn(4);

        int reserved = service.countReservedSpots(10L);

        assertEquals(4, reserved);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentSpot() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteSpot(999L));
    }
}

