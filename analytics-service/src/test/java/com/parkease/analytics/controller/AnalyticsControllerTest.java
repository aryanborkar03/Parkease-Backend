package com.parkease.analytics.controller;

import com.parkease.analytics.dto.response.*;
import com.parkease.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsController analyticsController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(analyticsController).build();
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

    // Occupancy rate retrieval tests

    @Test
    void shouldReturnOccupancyRate() throws Exception {
        OccupancyRateDTO dto = OccupancyRateDTO.builder()
                .lotId(10L)
                .occupancyRate(72.0)
                .occupiedSpots(36)
                .totalSpots(50)
                .build();

        when(analyticsService.getOccupancyRate(eq(10L), any())).thenReturn(dto);

        mockMvc.perform(get("/api/analytics/lots/10/occupancy")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotId").value(10L))
                .andExpect(jsonPath("$.occupancyRate").value(72.0));
    }

    // Hourly occupancy retrieval tests

    @Test
    void shouldReturnHourlyOccupancy() throws Exception {
        Map<Integer, Double> hourly = Map.of(9, 85.0, 10, 90.0, 18, 75.0);
        when(analyticsService.getHourlyOccupancy(eq(10L), any())).thenReturn(hourly);

        mockMvc.perform(get("/api/analytics/lots/10/hourly")
                        .principal(managerAuth()))
                .andExpect(status().isOk());
    }

    // Peak hours retrieval tests

    @Test
    void shouldReturnPeakHours() throws Exception {
        when(analyticsService.getPeakHours(10L, 3)).thenReturn(List.of(9, 18, 10));

        mockMvc.perform(get("/api/analytics/lots/10/peak-hours")
                        .param("top", "3")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(9));
    }

    // Revenue report retrieval tests

    @Test
    void shouldReturnRevenueReport() throws Exception {
        RevenueReportDTO dto = RevenueReportDTO.builder()
                .lotId(10L)
                .totalRevenue(5000.0)
                .build();

        when(analyticsService.getRevenueReport(eq(10L), any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(dto);

        mockMvc.perform(get("/api/analytics/lots/10/revenue")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-30")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(5000.0));
    }

    // Spot type utilisation retrieval tests

    @Test
    void shouldReturnSpotUtilisation() throws Exception {
        Map<String, Double> util = Map.of("FOUR_WHEELER", 80.0, "TWO_WHEELER", 20.0);
        when(analyticsService.getSpotTypeUtilisation(eq(10L), any())).thenReturn(util);

        mockMvc.perform(get("/api/analytics/lots/10/utilisation")
                        .principal(managerAuth()))
                .andExpect(status().isOk());
    }

    // Average parking duration retrieval tests

    @Test
    void shouldReturnAvgParkingDuration() throws Exception {
        when(analyticsService.getAvgParkingDuration(eq(10L), any())).thenReturn(120.0);

        mockMvc.perform(get("/api/analytics/lots/10/avg-duration")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgDurationMinutes").value(120.0));
    }

    // Lot summary retrieval tests

    @Test
    void shouldReturnLotSummary() throws Exception {
        LotSummaryDTO dto = new LotSummaryDTO();
        when(analyticsService.getLotSummary(eq(10L), any())).thenReturn(dto);

        mockMvc.perform(get("/api/analytics/lots/10/summary")
                        .principal(managerAuth()))
                .andExpect(status().isOk());
    }

    // Platform summary retrieval tests

    @Test
    void shouldReturnPlatformSummary() throws Exception {
        PlatformSummaryDTO dto = PlatformSummaryDTO.builder()
                .totalActiveLots(5)
                .totalRevenueAllTime(50000.0)
                .build();

        when(analyticsService.getPlatformSummary(any())).thenReturn(dto);

        mockMvc.perform(get("/api/analytics/platform")
                        .principal(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveLots").value(5));
    }

    // Manager summary retrieval tests

    @Test
    void shouldReturnManagerSummary() throws Exception {
        PlatformSummaryDTO dto = PlatformSummaryDTO.builder()
                .totalActiveLots(2)
                .totalRevenueAllTime(10000.0)
                .build();

        when(analyticsService.getManagerSummary(eq("manager@test.com"), any())).thenReturn(dto);

        mockMvc.perform(get("/api/analytics/manager")
                        .param("email", "manager@test.com")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveLots").value(2));
    }

    // Internal occupancy logging tests

    @Test
    void shouldLogOccupancySuccessfully() throws Exception {
        doNothing().when(analyticsService).logOccupancy(10L, 38, 50);

        mockMvc.perform(post("/api/analytics/internal/log")
                        .param("lotId", "10")
                        .param("occupied", "38")
                        .param("total", "50"))
                .andExpect(status().isOk());
    }
}
