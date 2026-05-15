package com.parkease.analytics.service;

import com.parkease.analytics.client.BookingServiceClient;
import com.parkease.analytics.client.LotServiceClient;
import com.parkease.analytics.client.PaymentServiceClient;
import com.parkease.analytics.dto.response.*;
import com.parkease.analytics.entity.OccupancyLog;
import com.parkease.analytics.repository.OccupancyLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private OccupancyLogRepository logRepo;

    @Mock
    private BookingServiceClient bookingServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Mock
    private LotServiceClient lotServiceClient;

    @InjectMocks
    private AnalyticsServiceImpl service;

    @Test
    void shouldReturnZeroOccupancyWhenNoLogsFound() {
        when(logRepo.findTopByLotIdOrderByTimestampDesc(1L)).thenReturn(null);
        when(bookingServiceClient.getBookingsByLot(eq(1L), any())).thenReturn(List.of());

        OccupancyRateDTO result = service.getOccupancyRate(1L, "token");

        assertNotNull(result);
        assertEquals(0.0, result.getOccupancyRate());
        assertEquals(0, result.getOccupiedSpots());
    }

    @Test
    void shouldReturnOccupancyRateSuccessfully() {
        OccupancyLog log = OccupancyLog.builder()
                .lotId(1L)
                .occupiedSpots(8)
                .totalSpots(10)
                .occupancyRate(0.8)
                .build();

        when(logRepo.findTopByLotIdOrderByTimestampDesc(1L)).thenReturn(log);
        when(bookingServiceClient.getBookingsByLot(eq(1L), any())).thenReturn(List.of());

        OccupancyRateDTO result = service.getOccupancyRate(1L, "token");

        assertEquals(0.8, result.getOccupancyRate());
        assertEquals(10, result.getTotalSpots());
        assertEquals(2, result.getAvailableSpots());
    }

    @Test
    void shouldReturnHourlyOccupancyWithDefaults() {
        List<Object[]> rows = List.of(
                new Object[]{9, 0.85},
                new Object[]{10, 0.92}
        );

        when(logRepo.getHourlyOccupancy(1L)).thenReturn(rows);

        Map<Integer, Double> result = service.getHourlyOccupancy(1L, "token");

        assertEquals(24, result.size());
        assertEquals(0.85, result.get(9));
        assertEquals(0.92, result.get(10));
        assertEquals(0.0, result.get(0));
    }

    @Test
    void shouldReturnPeakHoursSuccessfully() {
        List<Object[]> rows = List.of(
                new Object[]{10, 0.95},
                new Object[]{11, 0.90},
                new Object[]{9, 0.88}
        );

        when(logRepo.getPeakHours(1L)).thenReturn(rows);

        List<Integer> result = service.getPeakHours(1L, 2);

        assertEquals(List.of(10, 11), result);
    }

    @Test
    void shouldReturnRevenueReportSuccessfully() {
        List<Map<String, Object>> bookings = List.of(
                Map.of("status", "COMPLETED", "createdAt", "2026-04-14T10:00:00", "totalAmount", 150.0),
                Map.of("status", "COMPLETED", "createdAt", "2026-04-14T12:00:00", "totalAmount", 50.0)
        );

        when(bookingServiceClient.getBookingsByLot(eq(1L), any())).thenReturn(bookings);

        RevenueReportDTO result = service.getRevenueReport(
                1L,
                LocalDate.of(2026, 4, 14),
                LocalDate.of(2026, 4, 14),
                "Bearer token"
        );

        assertEquals(200.0, result.getTotalRevenue());
        assertEquals(2, result.getCompletedBookings());
    }

    @Test
    void shouldReturnAverageParkingDurationSuccessfully() {
        List<Map<String, Object>> bookings = List.of(
                Map.of(
                        "status", "COMPLETED",
                        "checkInTime", "2026-04-14T10:00:00",
                        "checkOutTime", "2026-04-14T12:00:00"
                )
        );

        when(bookingServiceClient.getBookingsByLot(eq(1L), any())).thenReturn(bookings);

        double result = service.getAvgParkingDuration(1L, "Bearer token");

        assertEquals(120.0, result);
    }

    @Test
    void shouldLogOccupancySuccessfully() {
        service.logOccupancy(1L, 8, 10);
        verify(logRepo).save(any(OccupancyLog.class));
    }

    @Test
    void shouldReturnPlatformSummarySuccessfully() {
        List<Object[]> latestLogs = List.of(
                new Object[]{1L, LocalDateTime.now(), 8, 10},
                new Object[]{2L, LocalDateTime.now(), 5, 10}
        );

        List<Map<String, Object>> allBookings = List.of(
                Map.of("createdAt", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T10:00:00", "vehicleType", "CAR", "status", "COMPLETED")
        );

        List<Map<String, Object>> payments = List.of(
                Map.of("status", "PAID", "paidAt", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T12:00:00", "amount", 200.0)
        );

        when(logRepo.getLatestOccupancyAllLots()).thenReturn(latestLogs);
        when(bookingServiceClient.getAllBookings(any())).thenReturn(allBookings);
        when(paymentServiceClient.getAllPayments(any())).thenReturn(payments);

        PlatformSummaryDTO result = service.getPlatformSummary("Bearer token");

        assertEquals(2, result.getTotalActiveLots());
        assertEquals(20, result.getTotalSpots());
        assertEquals(13, result.getTotalOccupiedSpots());
        assertEquals(1, result.getTotalBookingsToday());
    }

    @Test
    void shouldReturnOccupancyRateWithNoLogsAndNoBookings() {
        when(logRepo.findTopByLotIdOrderByTimestampDesc(1L)).thenReturn(null);
        when(bookingServiceClient.getBookingsByLot(eq(1L), any())).thenThrow(new RuntimeException("Booking service down"));

        OccupancyRateDTO result = service.getOccupancyRate(1L, "token");
        assertEquals(20, result.getTotalSpots());
        assertEquals(0, result.getOccupiedSpots());
    }

    @Test
    void shouldReturnSpotTypeUtilisation() {
        List<Map<String, Object>> bookings = List.of(
                Map.of("status", "COMPLETED", "vehicleType", "CAR"),
                Map.of("status", "COMPLETED", "vehicleType", "CAR"),
                Map.of("status", "COMPLETED", "vehicleType", "BIKE"),
                Map.of("status", "ACTIVE", "vehicleType", "CAR")
        );
        when(bookingServiceClient.getBookingsByLot(eq(1L), any())).thenReturn(bookings);

        Map<String, Double> result = service.getSpotTypeUtilisation(1L, "token");
        assertEquals(66.67, result.get("CAR"));
        assertEquals(33.33, result.get("BIKE"));
    }

    @Test
    void shouldReturnSpotTypeUtilisationEmpty() {
        when(bookingServiceClient.getBookingsByLot(eq(1L), any())).thenReturn(List.of());
        assertTrue(service.getSpotTypeUtilisation(1L, "token").isEmpty());
    }

    @Test
    void shouldReturnAvgParkingDurationException() {
        List<Map<String, Object>> bookings = List.of(
                Map.of("status", "COMPLETED", "checkInTime", "invalid", "checkOutTime", "invalid")
        );
        when(bookingServiceClient.getBookingsByLot(eq(1L), any())).thenReturn(bookings);
        assertEquals(0.0, service.getAvgParkingDuration(1L, "token"));
    }

    @Test
    void shouldReturnLotSummary() {
        // Verifies the method returns a valid LotSummaryDTO without error
        when(logRepo.findTopByLotIdOrderByTimestampDesc(1L)).thenReturn(null);
        when(bookingServiceClient.getBookingsByLot(eq(1L), any())).thenReturn(List.of());
        when(logRepo.getPeakHours(1L)).thenReturn(List.of());

        LotSummaryDTO result = service.getLotSummary(1L, "token");
        assertNotNull(result);
        assertEquals(1L, result.getLotId());
    }

    @Test
    void shouldReturnManagerSummary() {
        List<Map<String, Object>> lots = List.of(Map.of("lotId", 1L, "totalSpots", 50));
        when(lotServiceClient.getLotsByManager(anyString(), anyString())).thenReturn(lots);
        
        List<Map<String, Object>> bookings = List.of(
                Map.of("lotId", 1L, "status", "ACTIVE", "createdAt", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T10:00")
        );
        when(bookingServiceClient.getAllBookings(anyString())).thenReturn(bookings);

        PlatformSummaryDTO result = service.getManagerSummary("mgr@test.com", "token");
        assertEquals(1, result.getTotalActiveLots());
        assertEquals(50, result.getTotalSpots());
        assertEquals(1, result.getTotalOccupiedSpots());
        assertEquals(1, result.getTotalBookingsToday());
    }

    @Test
    void shouldHandleExceptionsInFetchMethods() {
        when(bookingServiceClient.getAllBookings(anyString())).thenThrow(new RuntimeException("Booking fail"));
        when(lotServiceClient.getLotsByManager(anyString(), anyString())).thenThrow(new RuntimeException("Lot fail"));
        when(paymentServiceClient.getAllPayments(anyString())).thenThrow(new RuntimeException("Payment fail"));

        PlatformSummaryDTO mgr = service.getManagerSummary("mgr", "token");
        assertEquals(0, mgr.getTotalActiveLots());

        PlatformSummaryDTO plt = service.getPlatformSummary("token");
        assertEquals(0.0, plt.getTotalRevenueAllTime());
    }

    @Test
    void shouldReturnHourlyOccupancyMergeToday() {
        List<Object[]> rows = java.util.Collections.singletonList(new Object[]{10, 0.5});
        when(logRepo.getHourlyOccupancy(1L)).thenReturn(rows);

        List<Map<String, Object>> bookings = List.of(
                Map.of("status", "COMPLETED", "startTime", LocalDateTime.now().withHour(10).toString(), "endTime", LocalDateTime.now().withHour(11).toString())
        );
        when(bookingServiceClient.getBookingsByLot(eq(1L), anyString())).thenReturn(bookings);

        OccupancyLog log = OccupancyLog.builder().totalSpots(10).build();
        when(logRepo.findTopByLotIdOrderByTimestampDesc(1L)).thenReturn(log);

        Map<Integer, Double> result = service.getHourlyOccupancy(1L, "token");
        assertTrue(result.get(10) > 0);
    }

    // JWT utility and filter coverage tests

    @Test
    void testJwtUtil() {
        com.parkease.analytics.util.JwtUtil jwt = new com.parkease.analytics.util.JwtUtil();
        String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        org.springframework.test.util.ReflectionTestUtils.setField(jwt, "secret", secret);
        String token = io.jsonwebtoken.Jwts.builder()
            .setSubject("test@test.com")
            .claim("role", "ROLE_USER")
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes()))
            .compact();
        
        assertTrue(jwt.validate(token));
        assertEquals("test@test.com", jwt.getEmail(token));
        assertEquals("ROLE_USER", jwt.getRole(token));
        
        assertFalse(jwt.validate("invalidToken"));
    }

    @Test
    void testJwtAuthFilter() throws Exception {
        com.parkease.analytics.util.JwtUtil jwt = mock(com.parkease.analytics.util.JwtUtil.class);
        com.parkease.analytics.security.JwtAuthFilter filter = new com.parkease.analytics.security.JwtAuthFilter(jwt);

        jakarta.servlet.http.HttpServletRequest req = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse res = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwt.validate("token")).thenReturn(true);
        when(jwt.getEmail("token")).thenReturn("t@t.com");
        when(jwt.getRole("token")).thenReturn("ROLE_ADMIN");

        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }

    @Test
    void testJwtAuthFilterInvalidToken() throws Exception {
        com.parkease.analytics.util.JwtUtil jwt = mock(com.parkease.analytics.util.JwtUtil.class);
        com.parkease.analytics.security.JwtAuthFilter filter = new com.parkease.analytics.security.JwtAuthFilter(jwt);

        jakarta.servlet.http.HttpServletRequest req = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse res = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwt.validate("token")).thenThrow(new RuntimeException("invalid"));

        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }

    // DTO, exception handler, and Feign client fallback coverage tests
    @Test
    void testDTOsAndExceptions() {
        OccupancyRateDTO dto = OccupancyRateDTO.builder().lotId(1L).build();
        assertNotNull(dto.toString());

        LotSummaryDTO lot = LotSummaryDTO.builder().lotId(1L).build();
        assertNotNull(lot.toString());

        PlatformSummaryDTO plat = PlatformSummaryDTO.builder().totalActiveLots(1).build();
        assertNotNull(plat.toString());

        RevenueReportDTO rev = RevenueReportDTO.builder().lotId(1L).build();
        assertNotNull(rev.toString());

        com.parkease.analytics.exception.GlobalExceptionHandler handler = new com.parkease.analytics.exception.GlobalExceptionHandler();
        assertNotNull(handler.handleGeneral(new Exception("test")));
        assertNotNull(handler.handleBadArg(new IllegalArgumentException("test")));
        
        com.parkease.analytics.client.BookingServiceClient.BookingServiceFallback bookingFallback = new com.parkease.analytics.client.BookingServiceClient.BookingServiceFallback();
        assertTrue(bookingFallback.getAllBookings("token").isEmpty());
        assertTrue(bookingFallback.getBookingsByLot(1L, "token").isEmpty());

        com.parkease.analytics.client.LotServiceClient.LotServiceFallback lotFallback = new com.parkease.analytics.client.LotServiceClient.LotServiceFallback();
        assertTrue(lotFallback.getLotsByManager("e", "t").isEmpty());

        com.parkease.analytics.client.PaymentServiceClient.PaymentServiceFallback payFallback = new com.parkease.analytics.client.PaymentServiceClient.PaymentServiceFallback();
        assertTrue(payFallback.getAllPayments("t").isEmpty());

        com.parkease.analytics.client.SpotServiceClient.SpotServiceFallback spotFallback = new com.parkease.analytics.client.SpotServiceClient.SpotServiceFallback();
        assertTrue(spotFallback.getSpotsByLot(1L).isEmpty());
        assertEquals(0, spotFallback.getAvailableSpotCount(1L));
    }
}
