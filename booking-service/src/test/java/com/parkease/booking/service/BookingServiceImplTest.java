package com.parkease.booking.service;

import com.parkease.booking.dto.request.CreateBookingRequest;
import com.parkease.booking.dto.request.ExtendBookingRequest;
import com.parkease.booking.dto.response.BookingResponseDTO;
import com.parkease.booking.entity.*;
import com.parkease.booking.exception.BookingException;
import com.parkease.booking.mapper.BookingMapper;
import com.parkease.booking.messaging.NotificationPublisher;
import com.parkease.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import com.parkease.booking.dto.response.ManagerDashboardDTO;
import com.parkease.booking.dto.response.DriveInSpotDTO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository repo;

    @Mock
    private BookingMapper mapper;

    @Mock
    private com.parkease.booking.client.SpotServiceClient spotServiceClient;

    @Mock
    private com.parkease.booking.client.LotServiceClient lotServiceClient;

    @Mock
    private com.parkease.booking.client.VehicleServiceClient vehicleServiceClient;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private BookingServiceImpl service;

    private Booking booking;
    private BookingResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "graceMinutes", 15);

        booking = Booking.builder()
                .bookingId(1L)
                .driverEmail("aryan@test.com")
                .lotId(10L)
                .spotId(101L)
                .vehiclePlate("MP04AB1234")
                .bookingType(BookingType.PRE_BOOKING)
                .status(BookingStatus.RESERVED)
                .startTime(LocalDateTime.now().plusMinutes(5))
                .endTime(LocalDateTime.now().plusHours(2))
                .pricePerHour(50.0)
                .build();

        responseDTO = new BookingResponseDTO();
    }

    @Test
    void shouldCreateBookingSuccessfully() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setVehiclePlate("mp04ab1234");
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusMinutes(5));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "aryan@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(spotServiceClient.getSpotById(anyLong()))
                .thenReturn(Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.isVehicleBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.save(any(Booking.class))).thenReturn(booking);
        when(mapper.toDTO(any(Booking.class))).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        BookingResponseDTO result = service.createBooking(req, "aryan@test.com");

        assertNotNull(result);
        verify(repo).save(any(Booking.class));
        verify(spotServiceClient).reserveSpot(anyLong());
        verify(lotServiceClient).decrementAvailable(anyLong());
    }

    @Test
    void shouldThrowWhenSpotAlreadyBooked() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusMinutes(5));
        req.setEndTime(LocalDateTime.now().plusHours(1));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "aryan@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(spotServiceClient.getSpotById(anyLong()))
                .thenReturn(Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(true);

        assertThrows(BookingException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }

    @Test
    void shouldCheckInSuccessfully() {
        // Booking start time set in the past to allow check-in within the grace window
        booking.setStartTime(LocalDateTime.now().minusMinutes(5));

        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        when(repo.save(any())).thenReturn(booking);
        when(mapper.toDTO(any())).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        BookingResponseDTO result = service.checkIn(1L, "aryan@test.com");

        assertNotNull(result);
        assertEquals(BookingStatus.ACTIVE, booking.getStatus());
    }

    @Test
    void shouldCheckOutSuccessfully() {
        booking.setStatus(BookingStatus.ACTIVE);
        booking.setCheckInTime(LocalDateTime.now().minusHours(2));

        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        when(repo.save(any())).thenReturn(booking);
        when(mapper.toDTO(any())).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        BookingResponseDTO result = service.checkOut(1L, "aryan@test.com");

        assertNotNull(result);
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertTrue(booking.getTotalAmount() > 0);
        verify(spotServiceClient).releaseSpot(anyLong());
        verify(lotServiceClient).incrementAvailable(anyLong());
    }

    @Test
    void shouldCancelBookingSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        when(repo.save(any())).thenReturn(booking);
        when(mapper.toDTO(any())).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        BookingResponseDTO result = service.cancelBooking(1L, "aryan@test.com");

        assertNotNull(result);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(spotServiceClient).releaseSpot(anyLong());
        verify(lotServiceClient).incrementAvailable(anyLong());
    }

    @Test
    void shouldExtendBookingSuccessfully() {
        ExtendBookingRequest req = new ExtendBookingRequest();
        req.setNewEndTime(booking.getEndTime().plusHours(2));

        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.save(any())).thenReturn(booking);
        when(mapper.toDTO(any())).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        BookingResponseDTO result = service.extendBooking(1L, req, "aryan@test.com");

        assertNotNull(result);
        assertEquals(req.getNewEndTime(), booking.getEndTime());
    }

    @Test
    void shouldCalculateFareSuccessfully() {
        booking.setCheckInTime(LocalDateTime.now().minusHours(2));
        booking.setCheckOutTime(LocalDateTime.now());

        when(repo.findById(1L)).thenReturn(Optional.of(booking));

        double fare = service.calculateFare(1L);

        assertEquals(100.0, fare);
    }

    @Test
    void shouldThrowWhenDriverMismatch() {
        when(repo.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(RuntimeException.class,
                () -> service.checkIn(1L, "other@test.com"));
    }

    // Additional service-layer tests for retrieval and query operations

    @Test
    void testGetBookingById() {
        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        when(mapper.toDTO(booking)).thenReturn(new BookingResponseDTO());
        assertNotNull(service.getBookingById(1L));
    }

    @Test
    void testGetMyBookings() {
        when(repo.findByDriverEmailOrderByCreatedAtDesc("test@test.com")).thenReturn(List.of(booking));
        when(mapper.toDTO(booking)).thenReturn(new BookingResponseDTO());
        assertFalse(service.getMyBookings("test@test.com").isEmpty());
    }

    @Test
    void testGetActiveBookings() {
        when(repo.findByDriverEmailAndStatus("test@test.com", BookingStatus.ACTIVE)).thenReturn(List.of(booking));
        when(mapper.toDTO(booking)).thenReturn(new BookingResponseDTO());
        assertFalse(service.getActiveBookings("test@test.com").isEmpty());
    }

    @Test
    void testGetBookingsByLot() {
        when(repo.findByLotIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(booking));
        when(mapper.toDTO(booking)).thenReturn(new BookingResponseDTO());
        assertFalse(service.getBookingsByLot(10L).isEmpty());
    }

    @Test
    void testGetAllBookings() {
        when(repo.findAll()).thenReturn(List.of(booking));
        when(mapper.toDTO(booking)).thenReturn(new BookingResponseDTO());
        assertFalse(service.getAllBookings().isEmpty());
    }

    @Test
    void testGetDistinctLotIds() {
        when(repo.findDistinctLotIds()).thenReturn(List.of(10L));
        assertFalse(service.getDistinctLotIds().isEmpty());
    }

    @Test
    void testGetActiveBookingsByLot() {
        when(repo.findActiveBookingsByLot(10L)).thenReturn(List.of(booking));
        when(mapper.toDTO(booking)).thenReturn(new BookingResponseDTO());
        assertFalse(service.getActiveBookingsByLot(10L).isEmpty());
    }

    @Test
    void testGetUpcomingBookingsByLot() {
        when(repo.findUpcomingBookingsByLot(eq(10L), any(LocalDateTime.class))).thenReturn(List.of(booking));
        when(mapper.toDTO(booking)).thenReturn(new BookingResponseDTO());
        assertFalse(service.getUpcomingBookingsByLot(10L).isEmpty());
    }

    @Test
    void testGetManagerDashboard() {
        when(repo.findActiveBookingsByLot(10L)).thenReturn(List.of(booking));
        when(repo.findUpcomingBookingsByLot(eq(10L), any(LocalDateTime.class))).thenReturn(List.of(booking));
        ManagerDashboardDTO dashboard = service.getManagerDashboard(10L);
        assertNotNull(dashboard);
        assertEquals(10L, dashboard.getLotId());
        assertEquals(1, dashboard.getTotalActive());
        assertEquals(1, dashboard.getTotalUpcoming());
    }

    @Test
    void testGetAvailableSpotsForPreBooking() {
        when(repo.findBookedSpotIdsInWindow(anyLong(), any(), any())).thenReturn(List.of(101L));
        when(spotServiceClient.getSpotsByLot(10L)).thenReturn(List.of(
                Map.of("spotId", 101L, "status", "AVAILABLE"),
                Map.of("spotId", 102L, "status", "AVAILABLE")
        ));
        
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);
        
        List<Map<String, Object>> spots = service.getAvailableSpotsForPreBooking(10L, start, end);
        assertEquals(1, spots.size());
        assertEquals(102L, spots.get(0).get("spotId"));
    }

    @Test
    void testGetDriveInSpotView() {
        when(spotServiceClient.getSpotsByLot(10L)).thenReturn(List.of(
                Map.of("spotId", 101L, "status", "AVAILABLE", "spotNumber", "A1", "pricePerHour", 50.0),
                Map.of("spotId", 102L, "status", "RESERVED", "spotNumber", "A2", "pricePerHour", 50.0)
        ));
        booking.setStatus(BookingStatus.ACTIVE);
        when(repo.findActiveOrReservedBookingsForSpot(101L)).thenReturn(List.of(booking));
        when(repo.findActiveOrReservedBookingsForSpot(102L)).thenReturn(List.of());

        List<DriveInSpotDTO> views = service.getDriveInSpotView(10L);
        assertEquals(2, views.size());
    }

    // Branch coverage tests for edge cases and error conditions

    @Test
    void testCreateDriveInBooking() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setVehiclePlate("XYZ");
        req.setBookingType(BookingType.DRIVE_IN);
        req.setEndTime(LocalDateTime.now().plusHours(1));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "test@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(spotServiceClient.getSpotById(anyLong())).thenReturn(
                Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.isVehicleBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.save(any(Booking.class))).thenReturn(booking);
        when(mapper.toDTO(any(Booking.class))).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        BookingResponseDTO result = service.createBooking(req, "test@test.com");
        assertNotNull(result);
    }

    @Test
    void testCreateBookingMissingStartTime() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setVehicleId(1L);
        req.setEndTime(LocalDateTime.now().plusHours(1));
        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "test@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(spotServiceClient.getSpotById(anyLong())).thenReturn(
                Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(req, "test@test.com"));
    }

    @Test
    void testCreateBookingPastStartTime() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setVehicleId(1L);
        req.setStartTime(LocalDateTime.now().minusMinutes(5));
        req.setEndTime(LocalDateTime.now().plusHours(1));
        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "test@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(spotServiceClient.getSpotById(anyLong())).thenReturn(
                Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(req, "test@test.com"));
    }

    @Test
    void testCheckInNotReserved() {
        booking.setStatus(BookingStatus.COMPLETED);
        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        assertThrows(BookingException.class, () -> service.checkIn(1L, "aryan@test.com"));
    }

    @Test
    void testCheckInTooEarly() {
        booking.setStartTime(LocalDateTime.now().plusHours(1));
        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        assertThrows(BookingException.class, () -> service.checkIn(1L, "aryan@test.com"));
    }

    @Test
    void testCheckOutNotActive() {
        booking.setStatus(BookingStatus.RESERVED);
        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        assertThrows(BookingException.class, () -> service.checkOut(1L, "aryan@test.com"));
    }

    @Test
    void testCancelNotReserved() {
        booking.setStatus(BookingStatus.ACTIVE);
        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        assertThrows(BookingException.class, () -> service.cancelBooking(1L, "aryan@test.com"));
    }

    @Test
    void testExtendBookingConflict() {
        ExtendBookingRequest req = new ExtendBookingRequest();
        req.setNewEndTime(LocalDateTime.now().plusHours(3));
        booking.setStatus(BookingStatus.ACTIVE);
        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(true);
        assertThrows(BookingException.class, () -> service.extendBooking(1L, req, "aryan@test.com"));
    }

    @Test
    void testCreateBookingConflict() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "test@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(spotServiceClient.getSpotById(anyLong())).thenReturn(
                Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(true);
        assertThrows(BookingException.class, () -> service.createBooking(req, "test@test.com"));
    }

    @Test
    void testCreateBookingSpotServiceException() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setVehiclePlate("XYZ");
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "test@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.isVehicleBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        // Simulates spot service unavailability to exercise the fallback price path
        when(spotServiceClient.getSpotById(anyLong())).thenThrow(new RuntimeException("Spot service down"));
        // Simulates lot service unavailability to verify the call does not propagate the exception
        doThrow(new RuntimeException("Lot service down")).when(lotServiceClient).decrementAvailable(anyLong());
        // Simulates spot service unavailability on reservation to verify graceful handling
        doThrow(new RuntimeException("Spot service down")).when(spotServiceClient).reserveSpot(anyLong());
        
        when(repo.save(any(Booking.class))).thenReturn(booking);
        when(mapper.toDTO(any(Booking.class))).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        BookingResponseDTO result = service.createBooking(req, "test@test.com");
        assertNotNull(result);
    }

    @Test
    void testGetAvailableSpotsForPreBookingWithException() {
        // Simulates spot service failure to verify an empty list is returned as a fallback
        when(spotServiceClient.getSpotsByLot(anyLong())).thenThrow(new RuntimeException("Spot service down"));
        
        List<Map<String, Object>> result = service.getAvailableSpotsForPreBooking(10L, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDriveInSpotViewWithNullValues() {
        Map<String, Object> spotWithNulls = new HashMap<>();
        spotWithNulls.put("spotId", 101L);
        spotWithNulls.put("spotNumber", null);
        spotWithNulls.put("floor", null);
        spotWithNulls.put("spotType", null);
        spotWithNulls.put("vehicleType", null);
        spotWithNulls.put("pricePerHour", null);
        spotWithNulls.put("isEVCharging", null);
        spotWithNulls.put("isHandicapped", null);
        spotWithNulls.put("status", "AVAILABLE");

        when(spotServiceClient.getSpotsByLot(10L)).thenReturn(List.of(spotWithNulls));
        when(repo.findActiveOrReservedBookingsForSpot(101L)).thenReturn(List.of());

        List<DriveInSpotDTO> views = service.getDriveInSpotView(10L);
        assertEquals(1, views.size());
        assertNull(views.get(0).getSpotNumber());
        assertEquals(0, views.get(0).getFloor());
        assertEquals(0.0, views.get(0).getPricePerHour());
    }

    // ── validateVehicleOwnership branches ────────────────────────────────────

    @Test
    void shouldThrowWhenVehicleIdIsNull() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setVehicleId(null);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        assertThrows(com.parkease.booking.exception.BookingException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenVehicleNotFoundInService() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setVehicleId(99L);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(99L)).thenReturn(null);

        assertThrows(com.parkease.booking.exception.BookingException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenVehicleOwnerMismatch() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setVehicleId(1L);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "other@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));

        assertThrows(com.parkease.booking.exception.BookingException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenVehicleIsDeactivated() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setVehicleId(1L);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "aryan@test.com", "isActive", false, "vehicleType", "FOUR_WHEELER"));

        assertThrows(com.parkease.booking.exception.BookingException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }

    @Test
    void shouldAllowBookingWhenVehicleServiceIsDown() {
        // Vehicle service unavailable → ownership check skipped → booking proceeds
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(1L))
                .thenThrow(new RuntimeException("vehicle-service down"));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.isVehicleBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.save(any(Booking.class))).thenReturn(booking);
        when(mapper.toDTO(any(Booking.class))).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        // Should not throw — service-down is swallowed with a warning
        BookingResponseDTO result = service.createBooking(req, "aryan@test.com");
        assertNotNull(result);
    }

    // ── validateVehicleSpotCompatibility branches ────────────────────────────

    @Test
    void shouldThrowWhenVehicleTypeMismatchWithSpot() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        // Vehicle is TWO_WHEELER, spot is for FOUR_WHEELER
        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "aryan@test.com", "isActive", true, "vehicleType", "TWO_WHEELER"));
        when(spotServiceClient.getSpotById(101L)).thenReturn(
                Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenNonEvVehicleTriesEvSpot() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "aryan@test.com", "isActive", true,
                        "vehicleType", "FOUR_WHEELER", "isEV", false));
        when(spotServiceClient.getSpotById(101L)).thenReturn(
                Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER",
                        "spotType", "EV", "isEVCharging", true));

        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }

    @Test
    void shouldAllowBookingWhenSpotHasNoVehicleType() {
        // Spot has no vehicleType → backward-compatible, booking is allowed
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "aryan@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        // Spot has pricePerHour but no vehicleType key
        when(spotServiceClient.getSpotById(101L)).thenReturn(
                Map.of("pricePerHour", 60.0));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.isVehicleBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.save(any(Booking.class))).thenReturn(booking);
        when(mapper.toDTO(any(Booking.class))).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        BookingResponseDTO result = service.createBooking(req, "aryan@test.com");
        assertNotNull(result);
    }

    // ── extendBooking additional branches ────────────────────────────────────

    @Test
    void shouldExtendReservedBookingSuccessfully() {
        // RESERVED (not yet active) booking is also a valid state for extension
        ExtendBookingRequest req = new ExtendBookingRequest();
        req.setNewEndTime(booking.getEndTime().plusHours(1));

        // Set vehicleId so isVehicleBookedInWindow receives a non-null value
        booking.setVehicleId(1L);
        // booking is RESERVED by default in setUp()
        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.isVehicleBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.save(any())).thenReturn(booking);
        when(mapper.toDTO(any())).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        BookingResponseDTO result = service.extendBooking(1L, req, "aryan@test.com");
        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenExtendNewEndTimeNotAfterCurrentEnd() {
        ExtendBookingRequest req = new ExtendBookingRequest();
        req.setNewEndTime(booking.getEndTime().minusMinutes(30));  // before current end

        when(repo.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalArgumentException.class,
                () -> service.extendBooking(1L, req, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenExtendVehicleConflict() {
        ExtendBookingRequest req = new ExtendBookingRequest();
        req.setNewEndTime(booking.getEndTime().plusHours(2));

        booking.setStatus(BookingStatus.ACTIVE);
        booking.setVehicleId(1L);  // must not be null for isVehicleBookedInWindow
        when(repo.findById(1L)).thenReturn(Optional.of(booking));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.isVehicleBookedInWindow(anyLong(), any(), any())).thenReturn(true);

        assertThrows(BookingException.class,
                () -> service.extendBooking(1L, req, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenExtendStatusIsCompleted() {
        ExtendBookingRequest req = new ExtendBookingRequest();
        req.setNewEndTime(booking.getEndTime().plusHours(1));

        booking.setStatus(BookingStatus.COMPLETED);
        when(repo.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(BookingException.class,
                () -> service.extendBooking(1L, req, "aryan@test.com"));
    }

    // ── checkIn additional branches ───────────────────────────────────────────

    @Test
    void shouldThrowWhenCheckInGracePeriodExpired() {
        // Start time was 20 minutes ago; grace is 15 minutes → cutoff already passed
        booking.setStartTime(LocalDateTime.now().minusMinutes(20));
        when(repo.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(BookingException.class,
                () -> service.checkIn(1L, "aryan@test.com"));
    }

    @Test
    void shouldThrowWithDriveInMessageWhenCheckInCalledOnDriveIn() {
        // DRIVE_IN booking is auto-activated at creation; calling checkIn must throw with informative message
        booking.setStatus(BookingStatus.ACTIVE);
        booking.setBookingType(BookingType.DRIVE_IN);
        when(repo.findById(1L)).thenReturn(Optional.of(booking));

        BookingException ex = assertThrows(BookingException.class,
                () -> service.checkIn(1L, "aryan@test.com"));
        // The message must contain the DRIVE_IN hint
        assertTrue(ex.getMessage().contains("DRIVE_IN"));
    }

    // ── calculateFare additional branches ────────────────────────────────────

    @Test
    void shouldThrowWhenCalculateFareWithNoCheckIn() {
        booking.setCheckInTime(null);
        when(repo.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(BookingException.class, () -> service.calculateFare(1L));
    }

    @Test
    void shouldCalculateFareUsingNowWhenNoCheckOut() {
        // checkInTime set but checkOutTime is null → uses LocalDateTime.now()
        booking.setCheckInTime(LocalDateTime.now().minusMinutes(90));
        booking.setCheckOutTime(null);
        when(repo.findById(1L)).thenReturn(Optional.of(booking));

        double fare = service.calculateFare(1L);

        // 90 minutes → 1.5 hours → 1.5 * 50 = 75.0
        assertTrue(fare >= 75.0);
    }

    // ── getAvailableSpotsForPreBooking input validation ───────────────────────

    @Test
    void shouldThrowWhenPreBookingStartTimeIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getAvailableSpotsForPreBooking(10L, null, LocalDateTime.now().plusHours(1)));
    }

    @Test
    void shouldThrowWhenPreBookingEndTimeIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getAvailableSpotsForPreBooking(10L, LocalDateTime.now().plusHours(1), null));
    }

    @Test
    void shouldThrowWhenPreBookingEndTimeNotAfterStart() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end   = LocalDateTime.now().plusHours(1);  // before start
        assertThrows(IllegalArgumentException.class,
                () -> service.getAvailableSpotsForPreBooking(10L, start, end));
    }

    @Test
    void shouldThrowWhenPreBookingStartTimeIsInThePast() {
        LocalDateTime start = LocalDateTime.now().minusHours(1); // past
        LocalDateTime end   = LocalDateTime.now().plusHours(1);
        assertThrows(IllegalArgumentException.class,
                () -> service.getAvailableSpotsForPreBooking(10L, start, end));
    }

    // ── getDriveInSpotView — future-RESERVED branch ───────────────────────────

    @Test
    void shouldMarkSpotAsReservedWhenFutureReservationExists() {
        // Spot has a RESERVED booking that starts in the FUTURE — spot is marked "RESERVED"
        Booking futureBooking = Booking.builder()
                .bookingId(99L)
                .spotId(101L)
                .status(BookingStatus.RESERVED)
                .startTime(LocalDateTime.now().plusHours(2))   // future
                .endTime(LocalDateTime.now().plusHours(4))
                .build();

        when(spotServiceClient.getSpotsByLot(10L)).thenReturn(List.of(
                Map.of("spotId", 101L, "status", "AVAILABLE",
                        "spotNumber", "B1", "pricePerHour", 40.0)));
        when(repo.findActiveOrReservedBookingsForSpot(101L)).thenReturn(List.of(futureBooking));

        List<DriveInSpotDTO> views = service.getDriveInSpotView(10L);

        assertEquals(1, views.size());
        assertEquals("RESERVED", views.get(0).getStatus());
        assertFalse(views.get(0).isSelectable());
    }

    // ── getActiveBookingsCountForLot ─────────────────────────────────────────

    @Test
    void shouldReturnActiveBookingsCountForLot() {
        when(repo.countActiveBookingsForLotSimple(10L)).thenReturn(5);
        assertEquals(5, service.getActiveBookingsCountForLot(10L));
    }

    // ── getBookingById not-found ──────────────────────────────────────────────

    @Test
    void shouldThrowWhenGetBookingByIdNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(com.parkease.booking.exception.ResourceNotFoundException.class,
                () -> service.getBookingById(99L));
    }

    // ── vehicle conflict on createBooking ─────────────────────────────────────

    @Test
    void shouldThrowWhenVehicleAlreadyBookedForWindow() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(10L);
        req.setSpotId(101L);
        req.setVehicleId(1L);
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(2));

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "aryan@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(spotServiceClient.getSpotById(101L)).thenReturn(
                Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));
        when(repo.isSpotBookedInWindow(anyLong(), any(), any())).thenReturn(false);
        when(repo.isVehicleBookedInWindow(anyLong(), any(), any())).thenReturn(true); // vehicle conflict

        assertThrows(BookingException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }

    // ── endTime validation on createBooking ───────────────────────────────────

    @Test
    void shouldThrowWhenEndTimeIsNull() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setVehicleId(1L);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(null);  // missing

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "aryan@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(spotServiceClient.getSpotById(anyLong())).thenReturn(
                Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenEndTimeNotAfterStartTime() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setBookingType(BookingType.PRE_BOOKING);
        req.setVehicleId(1L);
        req.setStartTime(LocalDateTime.now().plusHours(2));
        req.setEndTime(LocalDateTime.now().plusHours(1));  // before start

        when(vehicleServiceClient.getVehicleById(1L)).thenReturn(
                Map.of("ownerEmail", "aryan@test.com", "isActive", true, "vehicleType", "FOUR_WHEELER"));
        when(spotServiceClient.getSpotById(anyLong())).thenReturn(
                Map.of("pricePerHour", 50.0, "vehicleType", "FOUR_WHEELER"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(req, "aryan@test.com"));
    }
}

