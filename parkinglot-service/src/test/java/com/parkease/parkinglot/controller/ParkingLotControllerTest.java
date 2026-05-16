package com.parkease.parkinglot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parkease.parkinglot.dto.request.ParkingLotRequestDTO;
import com.parkease.parkinglot.dto.response.ParkingLotResponseDTO;
import com.parkease.parkinglot.exception.GlobalExceptionHandler;
import com.parkease.parkinglot.exception.ResourceNotFoundException;
import com.parkease.parkinglot.exception.UnauthorizedException;
import com.parkease.parkinglot.service.ParkingLotService;
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

import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ParkingLotControllerTest {

    @Mock
    private ParkingLotService parkingLotService;

    @InjectMocks
    private ParkingLotController parkingLotController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ParkingLotResponseDTO sampleLot;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(parkingLotController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sampleLot = ParkingLotResponseDTO.builder()
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
                .build();
    }

    private UsernamePasswordAuthenticationToken managerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "manager@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_LOT_MANAGER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // Retrieve all open lots tests

    @Test
    void shouldReturnAllOpenLots() throws Exception {
        when(parkingLotService.getOpenLots()).thenReturn(List.of(sampleLot));

        mockMvc.perform(get("/api/lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotId").value(1L))
                .andExpect(jsonPath("$[0].name").value("Central Park Lot"));
    }

    // Retrieve a lot by its ID tests

    @Test
    void shouldReturnLotById() throws Exception {
        when(parkingLotService.getLotById(1L)).thenReturn(sampleLot);

        mockMvc.perform(get("/api/lots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotId").value(1L))
                .andExpect(jsonPath("$.city").value("Mumbai"));
    }

    // Retrieve lots filtered by city tests

    @Test
    void shouldReturnLotsByCity() throws Exception {
        when(parkingLotService.getByCity(eq("Mumbai"), any(), any(), any()))
                .thenReturn(List.of(sampleLot));

        mockMvc.perform(get("/api/lots/city/Mumbai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("Mumbai"));
    }

    // Retrieve nearby lots by geographic coordinates tests

    @Test
    void shouldReturnNearbyLots() throws Exception {
        sampleLot.setDistanceKm(2.5);
        when(parkingLotService.getNearbyLots(anyDouble(), anyDouble(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of(sampleLot));

        mockMvc.perform(get("/api/lots/nearby")
                        .param("lat", "19.076")
                        .param("lon", "72.877")
                        .param("radius", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].distanceKm").value(2.5));
    }

    // Create lot tests

    @Test
    void shouldCreateLotSuccessfully() throws Exception {
        ParkingLotRequestDTO req = new ParkingLotRequestDTO();
        req.setName("Central Park Lot");
        req.setManagerName("Test Manager");
        req.setAddress("MG Road");
        req.setCity("Mumbai");
        req.setLatitude(19.076);
        req.setLongitude(72.877);
        req.setTotalSpots(100);

        when(parkingLotService.createLot(any(ParkingLotRequestDTO.class), eq("manager@test.com")))
                .thenReturn(sampleLot);

        mockMvc.perform(post("/api/lots")
                        .principal(managerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotId").value(1L));
    }

    // Toggle lot open/closed status tests

    @Test
    void shouldToggleLotOpenStatus() throws Exception {
        sampleLot.setOpen(false);
        when(parkingLotService.toggleOpen(1L, "manager@test.com")).thenReturn(sampleLot);

        mockMvc.perform(put("/api/lots/1/toggle")
                        .principal(managerAuth()))
                .andExpect(status().isOk());
    }

    // Retrieve lots owned by the authenticated manager

    @Test
    void shouldReturnMyLots() throws Exception {
        when(parkingLotService.getLotsByManager("manager@test.com")).thenReturn(List.of(sampleLot));

        mockMvc.perform(get("/api/lots/my-lots")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].managerEmail").value("manager@test.com"));
    }

    // Admin lot approval tests

    @Test
    void shouldApproveLotSuccessfully() throws Exception {
        when(parkingLotService.approveLot(1L)).thenReturn(sampleLot);

        mockMvc.perform(put("/api/lots/admin/1/approve")
                        .principal(adminAuth()))
                .andExpect(status().isOk());
    }

    // Admin lot rejection tests

    @Test
    void shouldRejectLotSuccessfully() throws Exception {
        sampleLot.setApproved(false);
        when(parkingLotService.rejectLot(1L)).thenReturn(sampleLot);

        mockMvc.perform(put("/api/lots/admin/1/reject")
                        .principal(adminAuth()))
                .andExpect(status().isOk());
    }

    // Retrieve lots pending admin approval

    @Test
    void shouldReturnPendingLots() throws Exception {
        sampleLot.setApproved(false);
        when(parkingLotService.getPendingApprovalLots()).thenReturn(List.of(sampleLot));

        mockMvc.perform(get("/api/lots/admin/pending")
                        .principal(adminAuth()))
                .andExpect(status().isOk());
    }

    // Available spot count adjustment tests

    @Test
    void shouldDecrementAvailableSpots() throws Exception {
        doNothing().when(parkingLotService).decrementSpot(1L);

        mockMvc.perform(put("/api/lots/1/decrement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Available spots decremented."));
    }

    @Test
    void shouldIncrementAvailableSpots() throws Exception {
        doNothing().when(parkingLotService).incrementSpot(1L);

        mockMvc.perform(put("/api/lots/1/increment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Available spots incremented."));
    }

    // Update lot tests

    @Test
    void shouldUpdateLotSuccessfully() throws Exception {
        ParkingLotRequestDTO req = new ParkingLotRequestDTO();
        req.setName("Updated Lot");
        req.setManagerName("Test Manager");
        req.setAddress("New Address");
        req.setCity("Mumbai");
        req.setLatitude(19.076);
        req.setLongitude(72.877);
        req.setTotalSpots(120);

        sampleLot.setName("Updated Lot");
        when(parkingLotService.updateLot(eq(1L), any(ParkingLotRequestDTO.class), eq("manager@test.com")))
                .thenReturn(sampleLot);

        mockMvc.perform(put("/api/lots/1")
                        .principal(managerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Lot"));
    }

    // Delete lot tests

    @Test
    void shouldDeleteLotSuccessfully() throws Exception {
        doNothing().when(parkingLotService).deleteLot(1L, "manager@test.com");

        mockMvc.perform(delete("/api/lots/1")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lot deleted successfully."));
    }

    // Nearby lots with optional filter params

    @Test
    void shouldReturnNearbyLotsWithFilters() throws Exception {
        when(parkingLotService.getNearbyLots(anyDouble(), anyDouble(), anyDouble(), eq("CAR"), eq(true), eq(false)))
                .thenReturn(List.of(sampleLot));

        mockMvc.perform(get("/api/lots/nearby")
                        .param("lat", "19.076")
                        .param("lon", "72.877")
                        .param("radius", "3.0")
                        .param("vehicleType", "CAR")
                        .param("isEv", "true")
                        .param("isHandicapped", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotId").value(1L));
    }

    // Lots by city with optional query params

    @Test
    void shouldReturnLotsByCityWithFilters() throws Exception {
        when(parkingLotService.getByCity(eq("Mumbai"), eq("CAR"), eq(true), eq(true)))
                .thenReturn(List.of(sampleLot));

        mockMvc.perform(get("/api/lots/city/Mumbai")
                        .param("vehicleType", "CAR")
                        .param("isEv", "true")
                        .param("isHandicapped", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("Mumbai"));
    }

    // Empty results — service returns empty list

    @Test
    void shouldReturnEmptyListWhenNoOpenLots() throws Exception {
        when(parkingLotService.getOpenLots()).thenReturn(List.of());

        mockMvc.perform(get("/api/lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldReturnEmptyNearbyLots() throws Exception {
        when(parkingLotService.getNearbyLots(anyDouble(), anyDouble(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/lots/nearby")
                        .param("lat", "0.0")
                        .param("lon", "0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldReturn404WhenLotNotFound() throws Exception {
        when(parkingLotService.getLotById(99L))
                .thenThrow(new ResourceNotFoundException("Lot not found: 99"));

        mockMvc.perform(get("/api/lots/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Lot not found: 99"));
    }

    @Test
    void shouldReturn500OnUnexpectedError() throws Exception {
        when(parkingLotService.getOpenLots())
                .thenThrow(new RuntimeException("DB connection lost"));

        mockMvc.perform(get("/api/lots"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }
}

