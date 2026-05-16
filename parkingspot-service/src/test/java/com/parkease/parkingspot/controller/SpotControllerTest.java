package com.parkease.parkingspot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkease.parkingspot.dto.request.BulkSpotRequestDTO;
import com.parkease.parkingspot.dto.request.SpotRequestDTO;
import com.parkease.parkingspot.dto.response.ApiResponse;
import com.parkease.parkingspot.dto.response.SpotResponseDTO;
import com.parkease.parkingspot.entity.SpotStatus;
import com.parkease.parkingspot.entity.SpotType;
import com.parkease.parkingspot.entity.VehicleType;
import com.parkease.parkingspot.exception.GlobalExceptionHandler;
import com.parkease.parkingspot.exception.ResourceNotFoundException;
import com.parkease.parkingspot.exception.SpotNotAvailableException;
import com.parkease.parkingspot.service.SpotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SpotControllerTest {

    @Mock
    private SpotService spotService;

    @InjectMocks
    private SpotController spotController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private SpotResponseDTO sampleDTO;

    @BeforeEach
    void setUp() {
        // Wire in the global exception handler so HTTP error-status tests work
        mockMvc = MockMvcBuilders.standaloneSetup(spotController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();

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

    private UsernamePasswordAuthenticationToken managerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "manager@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_LOT_MANAGER")));
    }

    // ── GET endpoints ────────────────────────────────────────────────────────

    @Test
    void shouldGetSpotById() throws Exception {
        when(spotService.getSpotById(1L)).thenReturn(sampleDTO);

        mockMvc.perform(get("/api/spots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spotId").value(1L))
                .andExpect(jsonPath("$.spotNumber").value("A1-01"));
    }

    @Test
    void shouldReturn404WhenSpotNotFound() throws Exception {
        when(spotService.getSpotById(99L)).thenThrow(new ResourceNotFoundException("Spot not found with id: 99"));

        mockMvc.perform(get("/api/spots/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Spot not found with id: 99"));
    }

    @Test
    void shouldGetSpotsByLot() throws Exception {
        when(spotService.getSpotsByLot(10L)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/spots/lot/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotId").value(10L));
    }

    @Test
    void shouldGetAvailableSpots() throws Exception {
        when(spotService.getAvailableSpots(10L)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/spots/lot/10/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void shouldGetSpotsByFloor() throws Exception {
        when(spotService.getSpotsByFloor(10L, 1)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/spots/lot/10/floor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].floor").value(1));
    }

    @Test
    void shouldGetSpotsByType() throws Exception {
        when(spotService.getSpotsByType(10L, SpotType.STANDARD)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/spots/lot/10/type/STANDARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spotType").value("STANDARD"));
    }

    @Test
    void shouldGetSpotsByVehicleType() throws Exception {
        when(spotService.getSpotsByVehicleType(10L, VehicleType.FOUR_WHEELER)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/spots/lot/10/vehicle/FOUR_WHEELER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleType").value("FOUR_WHEELER"));
    }

    @Test
    void shouldGetEVSpots() throws Exception {
        sampleDTO.setEVCharging(true);
        when(spotService.getEVSpots(10L)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/spots/lot/10/ev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldGetHandicappedSpots() throws Exception {
        sampleDTO.setHandicapped(true);
        when(spotService.getHandicappedSpots(10L)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/spots/lot/10/handicapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldGetAvailableCount() throws Exception {
        when(spotService.countAvailableSpots(10L)).thenReturn(5);

        mockMvc.perform(get("/api/spots/lot/10/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(5));
    }

    @Test
    void shouldGetTotalSpots() throws Exception {
        when(spotService.getTotalSpots(10L)).thenReturn(20L);

        mockMvc.perform(get("/api/spots/lot/10/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(20));
    }

    @Test
    void shouldGetReservedCount() throws Exception {
        when(spotService.countReservedSpots(10L)).thenReturn(3);

        mockMvc.perform(get("/api/spots/lot/10/reserved-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(3));
    }

    // ── POST endpoints ───────────────────────────────────────────────────────

    @Test
    void shouldAddSpotSuccessfully() throws Exception {
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(10L);
        req.setSpotNumber("A1-01");
        req.setFloor(1);
        req.setSpotType(SpotType.STANDARD);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setPricePerHour(50.0);

        when(spotService.addSpot(any(SpotRequestDTO.class))).thenReturn(sampleDTO);

        mockMvc.perform(post("/api/spots")
                        .principal(managerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spotId").value(1L))
                .andExpect(jsonPath("$.spotNumber").value("A1-01"));
    }

    @Test
    void shouldReturn400WhenAddSpotWithDuplicateNumber() throws Exception {
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(10L);
        req.setSpotNumber("A1-01");
        req.setFloor(1);
        req.setSpotType(SpotType.STANDARD);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setPricePerHour(50.0);

        when(spotService.addSpot(any(SpotRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("Spot number 'A1-01' already exists in lot 10"));

        mockMvc.perform(post("/api/spots")
                        .principal(managerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Spot number 'A1-01' already exists in lot 10"));
    }

    @Test
    void shouldAddBulkSpotsSuccessfully() throws Exception {
        BulkSpotRequestDTO req = new BulkSpotRequestDTO();
        req.setLotId(10L);
        req.setCount(3);
        req.setPrefix("A");
        req.setFloor(1);
        req.setSpotType(SpotType.STANDARD);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setPricePerHour(50.0);

        when(spotService.addBulkSpots(any(BulkSpotRequestDTO.class)))
                .thenReturn(List.of(sampleDTO, sampleDTO, sampleDTO));

        mockMvc.perform(post("/api/spots/bulk")
                        .principal(managerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3));
    }

    // ── PUT endpoints ────────────────────────────────────────────────────────

    @Test
    void shouldUpdateSpotSuccessfully() throws Exception {
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(10L);           // required @NotNull
        req.setSpotNumber("A1-02");  // required @NotBlank
        req.setFloor(2);
        req.setSpotType(SpotType.LARGE);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setPricePerHour(80.0);

        sampleDTO.setSpotNumber("A1-02");
        when(spotService.updateSpot(eq(1L), any(SpotRequestDTO.class))).thenReturn(sampleDTO);

        mockMvc.perform(put("/api/spots/1")
                        .principal(managerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spotNumber").value("A1-02"));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentSpot() throws Exception {
        SpotRequestDTO req = new SpotRequestDTO();
        req.setLotId(10L);           // required @NotNull
        req.setSpotNumber("X99");    // required @NotBlank
        req.setFloor(1);
        req.setSpotType(SpotType.STANDARD);
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setPricePerHour(50.0);

        when(spotService.updateSpot(eq(99L), any(SpotRequestDTO.class)))
                .thenThrow(new ResourceNotFoundException("Spot not found with id: 99"));

        mockMvc.perform(put("/api/spots/99")
                        .principal(managerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReserveSpot() throws Exception {
        sampleDTO.setStatus(SpotStatus.RESERVED);
        when(spotService.reserveSpot(1L)).thenReturn(sampleDTO);

        mockMvc.perform(put("/api/spots/1/reserve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void shouldReturn409WhenReservingUnavailableSpot() throws Exception {
        when(spotService.reserveSpot(1L))
                .thenThrow(new SpotNotAvailableException("Spot 1 is not available. Current status: RESERVED"));

        mockMvc.perform(put("/api/spots/1/reserve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Spot 1 is not available. Current status: RESERVED"));
    }

    @Test
    void shouldReleaseSpot() throws Exception {
        when(spotService.releaseSpot(1L)).thenReturn(sampleDTO);

        mockMvc.perform(put("/api/spots/1/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // ── DELETE endpoints ─────────────────────────────────────────────────────

    @Test
    void shouldDeleteSpotSuccessfully() throws Exception {
        doNothing().when(spotService).deleteSpot(1L);

        mockMvc.perform(delete("/api/spots/1")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Spot deleted successfully."));
    }

    @Test
    void shouldReturn409WhenDeletingReservedSpot() throws Exception {
        doThrow(new SpotNotAvailableException("Cannot delete spot 1 — it is currently RESERVED"))
                .when(spotService).deleteSpot(1L);

        mockMvc.perform(delete("/api/spots/1")
                        .principal(managerAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete spot 1 — it is currently RESERVED"));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentSpot() throws Exception {
        doThrow(new ResourceNotFoundException("Spot not found with id: 99"))
                .when(spotService).deleteSpot(99L);

        mockMvc.perform(delete("/api/spots/99")
                        .principal(managerAuth()))
                .andExpect(status().isNotFound());
    }

    // ── Empty list corner cases ───────────────────────────────────────────────

    @Test
    void shouldReturnEmptyListForLotWithNoSpots() throws Exception {
        when(spotService.getSpotsByLot(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/spots/lot/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
