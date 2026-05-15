package com.parkease.vehicle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parkease.vehicle.dto.request.VehicleRequestDTO;
import com.parkease.vehicle.dto.response.VehicleResponseDTO;
import com.parkease.vehicle.entity.VehicleType;
import com.parkease.vehicle.service.VehicleService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private VehicleController vehicleController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private VehicleResponseDTO sampleVehicle;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(vehicleController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sampleVehicle = VehicleResponseDTO.builder()
                .vehicleId(1L)
                .ownerEmail("aryan@test.com")
                .licensePlate("MP04AB1234")
                .make("Honda")
                .model("City")
                .color("White")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .isEV(false)
                .isActive(true)
                .registeredAt(LocalDateTime.now())
                .build();
    }

    private UsernamePasswordAuthenticationToken driverAuth() {
        return new UsernamePasswordAuthenticationToken(
                "aryan@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_DRIVER")));
    }

    // Register vehicle tests

    @Test
    void shouldRegisterVehicleSuccessfully() throws Exception {
        VehicleRequestDTO req = new VehicleRequestDTO();
        req.setLicensePlate("MP04AB1234");
        req.setMake("Honda");
        req.setModel("City");
        req.setVehicleType(VehicleType.FOUR_WHEELER);

        when(vehicleService.registerVehicle(any(VehicleRequestDTO.class), eq("aryan@test.com")))
                .thenReturn(sampleVehicle);

        mockMvc.perform(post("/api/vehicles")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleId").value(1L))
                .andExpect(jsonPath("$.licensePlate").value("MP04AB1234"));
    }

    // Retrieve all vehicles owned by the authenticated driver

    @Test
    void shouldReturnMyVehicles() throws Exception {
        when(vehicleService.getMyVehicles("aryan@test.com")).thenReturn(List.of(sampleVehicle));

        mockMvc.perform(get("/api/vehicles/my")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value(1L))
                .andExpect(jsonPath("$[0].ownerEmail").value("aryan@test.com"));
    }

    // Retrieve a vehicle by its ID tests

    @Test
    void shouldReturnVehicleById() throws Exception {
        when(vehicleService.getVehicleById(1L)).thenReturn(sampleVehicle);

        mockMvc.perform(get("/api/vehicles/1")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(1L));
    }

    // Retrieve a vehicle by its license plate tests

    @Test
    void shouldReturnVehicleByPlate() throws Exception {
        when(vehicleService.getByLicensePlate("MP04AB1234")).thenReturn(sampleVehicle);

        mockMvc.perform(get("/api/vehicles/plate/MP04AB1234")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("MP04AB1234"));
    }

    // Update vehicle details tests

    @Test
    void shouldUpdateVehicleSuccessfully() throws Exception {
        VehicleRequestDTO req = new VehicleRequestDTO();
        req.setLicensePlate("MP04AB1234");
        req.setMake("Honda");
        req.setModel("Civic");
        req.setVehicleType(VehicleType.FOUR_WHEELER);

        sampleVehicle.setModel("Civic");
        when(vehicleService.updateVehicle(eq(1L), any(VehicleRequestDTO.class), eq("aryan@test.com")))
                .thenReturn(sampleVehicle);

        mockMvc.perform(put("/api/vehicles/1")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Civic"));
    }

    // Delete vehicle tests

    @Test
    void shouldDeleteVehicleSuccessfully() throws Exception {
        doNothing().when(vehicleService).deleteVehicle(1L, "aryan@test.com");

        mockMvc.perform(delete("/api/vehicles/1")
                        .principal(driverAuth()))
                .andExpect(status().isOk());
    }

    // Deactivate vehicle tests

    @Test
    void shouldDeactivateVehicleSuccessfully() throws Exception {
        doNothing().when(vehicleService).deactivateVehicle(1L, "aryan@test.com");

        mockMvc.perform(put("/api/vehicles/1/deactivate")
                        .principal(driverAuth()))
                .andExpect(status().isOk());
    }
}
