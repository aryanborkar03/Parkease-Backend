package com.parkease.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parkease.booking.dto.request.CreateBookingRequest;
import com.parkease.booking.dto.request.ExtendBookingRequest;
import com.parkease.booking.dto.response.BookingResponseDTO;
import com.parkease.booking.dto.response.DriveInSpotDTO;
import com.parkease.booking.dto.response.ManagerDashboardDTO;
import com.parkease.booking.entity.BookingStatus;
import com.parkease.booking.entity.BookingType;
import com.parkease.booking.exception.BookingException;
import com.parkease.booking.exception.GlobalExceptionHandler;
import com.parkease.booking.exception.ResourceNotFoundException;
import com.parkease.booking.service.BookingService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService service;

    @InjectMocks
    private BookingController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private BookingResponseDTO sampleDTO;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        sampleDTO = BookingResponseDTO.builder()
                .bookingId(1L)
                .lotId(10L)
                .spotId(101L)
                .driverEmail("aryan@test.com")
                .bookingType(BookingType.PRE_BOOKING)
                .status(BookingStatus.RESERVED)
                .build();
    }

    private UsernamePasswordAuthenticationToken driverAuth() {
        return new UsernamePasswordAuthenticationToken(
                "aryan@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_DRIVER")));
    }

    private UsernamePasswordAuthenticationToken managerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "manager@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_LOT_MANAGER")));
    }

    // ── Slot Discovery ────────────────────────────────────────────────────────

    @Test
    void shouldGetAvailableSpotsForPreBooking() throws Exception {
        when(service.getAvailableSpotsForPreBooking(eq(10L), any(), any()))
                .thenReturn(List.of(Map.of("spotId", 101L, "spotNumber", "A1")));

        mockMvc.perform(get("/api/bookings/slots/10/available")
                        .param("startTime", "2026-06-01T10:00:00")
                        .param("endTime", "2026-06-01T12:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spotId").value(101L));
    }

    @Test
    void shouldReturnEmptyListWhenNoSpotsAvailable() throws Exception {
        when(service.getAvailableSpotsForPreBooking(eq(10L), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/slots/10/available")
                        .param("startTime", "2026-06-01T10:00:00")
                        .param("endTime", "2026-06-01T12:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldGetDriveInView() throws Exception {
        DriveInSpotDTO spot = DriveInSpotDTO.builder()
                .spotId(101L)
                .spotNumber("A1")
                .selectable(true)
                .availabilityLabel("Available Now")
                .build();
        when(service.getDriveInSpotView(10L)).thenReturn(List.of(spot));

        mockMvc.perform(get("/api/bookings/slots/10/drive-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spotId").value(101L))
                .andExpect(jsonPath("$[0].availabilityLabel").value("Available Now"));
    }

    // ── Create Booking ────────────────────────────────────────────────────────

    @Test
    void shouldCreatePreBookingSuccessfully() throws Exception {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setVehiclePlate("MP04AB1234");
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(3));

        when(service.createBooking(any(CreateBookingRequest.class), eq("aryan@test.com")))
                .thenReturn(sampleDTO);

        mockMvc.perform(post("/api/bookings")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(1L))
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void shouldCreateDriveInBookingSuccessfully() throws Exception {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setVehiclePlate("MP04AB1234");
        req.setBookingType(BookingType.DRIVE_IN);
        req.setEndTime(LocalDateTime.now().plusHours(2));

        BookingResponseDTO driveInDTO = BookingResponseDTO.builder()
                .bookingId(2L)
                .status(BookingStatus.ACTIVE)
                .bookingType(BookingType.DRIVE_IN)
                .build();
        when(service.createBooking(any(CreateBookingRequest.class), eq("aryan@test.com")))
                .thenReturn(driveInDTO);

        mockMvc.perform(post("/api/bookings")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturn409WhenSpotAlreadyBooked() throws Exception {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setVehiclePlate("MP04AB1234");
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(3));

        when(service.createBooking(any(), anyString()))
                .thenThrow(new BookingException("Spot 101 is already booked in this time window."));

        mockMvc.perform(post("/api/bookings")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Spot 101 is already booked in this time window."));
    }

    // ── Check-In ──────────────────────────────────────────────────────────────

    @Test
    void shouldCheckInSuccessfully() throws Exception {
        sampleDTO.setStatus(BookingStatus.ACTIVE);
        when(service.checkIn(1L, "aryan@test.com")).thenReturn(sampleDTO);

        mockMvc.perform(put("/api/bookings/1/checkin")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturn409WhenCheckInTooEarly() throws Exception {
        when(service.checkIn(1L, "aryan@test.com"))
                .thenThrow(new BookingException("Check-in window not yet open."));

        mockMvc.perform(put("/api/bookings/1/checkin")
                        .principal(driverAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Check-in window not yet open."));
    }

    @Test
    void shouldReturn404WhenCheckInBookingNotFound() throws Exception {
        when(service.checkIn(99L, "aryan@test.com"))
                .thenThrow(new ResourceNotFoundException("Booking not found: 99"));

        mockMvc.perform(put("/api/bookings/99/checkin")
                        .principal(driverAuth()))
                .andExpect(status().isNotFound());
    }

    // ── Check-Out ─────────────────────────────────────────────────────────────

    @Test
    void shouldCheckOutSuccessfully() throws Exception {
        sampleDTO.setStatus(BookingStatus.COMPLETED);
        sampleDTO.setTotalAmount(100.0);
        when(service.checkOut(1L, "aryan@test.com")).thenReturn(sampleDTO);

        mockMvc.perform(put("/api/bookings/1/checkout")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalAmount").value(100.0));
    }

    @Test
    void shouldReturn409WhenCheckOutNotActive() throws Exception {
        when(service.checkOut(1L, "aryan@test.com"))
                .thenThrow(new BookingException("Booking is not ACTIVE. Cannot check out."));

        mockMvc.perform(put("/api/bookings/1/checkout")
                        .principal(driverAuth()))
                .andExpect(status().isConflict());
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Test
    void shouldCancelBookingSuccessfully() throws Exception {
        sampleDTO.setStatus(BookingStatus.CANCELLED);
        when(service.cancelBooking(1L, "aryan@test.com")).thenReturn(sampleDTO);

        mockMvc.perform(put("/api/bookings/1/cancel")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldReturn409WhenCancellingActiveBooking() throws Exception {
        when(service.cancelBooking(1L, "aryan@test.com"))
                .thenThrow(new BookingException("Only RESERVED bookings can be cancelled."));

        mockMvc.perform(put("/api/bookings/1/cancel")
                        .principal(driverAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only RESERVED bookings can be cancelled."));
    }

    // ── Extend ────────────────────────────────────────────────────────────────

    @Test
    void shouldExtendBookingSuccessfully() throws Exception {
        ExtendBookingRequest req = new ExtendBookingRequest();
        req.setNewEndTime(LocalDateTime.now().plusHours(5));

        sampleDTO.setStatus(BookingStatus.RESERVED);
        when(service.extendBooking(eq(1L), any(ExtendBookingRequest.class), eq("aryan@test.com")))
                .thenReturn(sampleDTO);

        mockMvc.perform(put("/api/bookings/1/extend")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(1L));
    }

    @Test
    void shouldReturn409WhenExtendConflicts() throws Exception {
        ExtendBookingRequest req = new ExtendBookingRequest();
        req.setNewEndTime(LocalDateTime.now().plusHours(5));

        when(service.extendBooking(eq(1L), any(), anyString()))
                .thenThrow(new BookingException("Spot already booked in extended window."));

        mockMvc.perform(put("/api/bookings/1/extend")
                        .principal(driverAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ── Driver Query Endpoints ─────────────────────────────────────────────────

    @Test
    void shouldGetMyBookings() throws Exception {
        when(service.getMyBookings("aryan@test.com")).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/bookings/my")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(1L));
    }

    @Test
    void shouldGetMyBookingsEmpty() throws Exception {
        when(service.getMyBookings("aryan@test.com")).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/my")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldGetMyActiveBookings() throws Exception {
        sampleDTO.setStatus(BookingStatus.ACTIVE);
        when(service.getActiveBookings("aryan@test.com")).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/bookings/my/active")
                        .principal(driverAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void shouldGetBookingById() throws Exception {
        when(service.getBookingById(1L)).thenReturn(sampleDTO);

        mockMvc.perform(get("/api/bookings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(1L));
    }

    @Test
    void shouldReturn404WhenBookingNotFound() throws Exception {
        when(service.getBookingById(99L))
                .thenThrow(new ResourceNotFoundException("Booking not found: 99"));

        mockMvc.perform(get("/api/bookings/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetFare() throws Exception {
        when(service.calculateFare(1L)).thenReturn(125.50);

        mockMvc.perform(get("/api/bookings/1/fare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(125.50));
    }

    // ── Manager Endpoints ─────────────────────────────────────────────────────

    @Test
    void shouldGetManagerDashboard() throws Exception {
        ManagerDashboardDTO dashboard = ManagerDashboardDTO.builder()
                .lotId(10L)
                .totalActive(3)
                .totalUpcoming(5)
                .build();
        when(service.getManagerDashboard(10L)).thenReturn(dashboard);

        mockMvc.perform(get("/api/bookings/manager/10/dashboard")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotId").value(10L))
                .andExpect(jsonPath("$.totalActive").value(3))
                .andExpect(jsonPath("$.totalUpcoming").value(5));
    }

    @Test
    void shouldGetManagerActiveBookings() throws Exception {
        sampleDTO.setStatus(BookingStatus.ACTIVE);
        when(service.getActiveBookingsByLot(10L)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/bookings/manager/10/active")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void shouldGetManagerUpcomingBookings() throws Exception {
        when(service.getUpcomingBookingsByLot(10L)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/bookings/manager/10/upcoming")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(1L));
    }

    @Test
    void shouldGetBookingsByLot() throws Exception {
        when(service.getBookingsByLot(10L)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/bookings/lot/10")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotId").value(10L));
    }

    // ── Admin Endpoints ──────────────────────────────────────────────────────

    @Test
    void shouldGetAllBookings() throws Exception {
        when(service.getAllBookings()).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/bookings/admin/all")
                        .principal(managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldGetDistinctLotIds() throws Exception {
        when(service.getDistinctLotIds()).thenReturn(List.of(10L, 20L));

        mockMvc.perform(get("/api/bookings/internal/lot-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldGetActiveBookingsCount() throws Exception {
        when(service.getActiveBookingsCountForLot(10L)).thenReturn(7);

        mockMvc.perform(get("/api/bookings/lot/10/active-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(7));
    }

    @Test
    void shouldReturnZeroWhenNoActiveBookings() throws Exception {
        when(service.getActiveBookingsCountForLot(10L)).thenReturn(0);

        mockMvc.perform(get("/api/bookings/lot/10/active-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));
    }

    // ── GlobalExceptionHandler wiring ─────────────────────────────────────────

    @Test
    void shouldReturn500OnUnexpectedServiceError() throws Exception {
        when(service.getBookingById(1L))
                .thenThrow(new RuntimeException("Unexpected DB error"));

        mockMvc.perform(get("/api/bookings/1"))
                .andExpect(status().isInternalServerError());
    }
}
