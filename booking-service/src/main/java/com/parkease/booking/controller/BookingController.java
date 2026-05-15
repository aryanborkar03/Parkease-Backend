package com.parkease.booking.controller;

import com.parkease.booking.dto.request.CreateBookingRequest;
import com.parkease.booking.dto.request.ExtendBookingRequest;
import com.parkease.booking.dto.response.*;
import com.parkease.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Booking REST API.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  BOOKING FLOW                                                           │
 * │                                                                         │
 * │  1. User chooses: PRE_BOOKING or DRIVE_IN                               │
 * │                                                                         │
 * │  PRE_BOOKING:                                                           │
 * │   a) GET /api/bookings/slots/{lotId}/available?startTime=&endTime=      │
 * │      → Time-filtered available spots (only free in that window)         │
 * │   b) POST /api/bookings  { bookingType: PRE_BOOKING, spotId, times }    │
 * │      → Booking created as RESERVED                                      │
 * │   c) PUT /api/bookings/{id}/checkin  (within grace period)              │
 * │   d) PUT /api/bookings/{id}/checkout                                    │
 * │                                                                         │
 * │  DRIVE_IN:                                                              │
 * │   a) GET /api/bookings/slots/{lotId}/drive-in                           │
 * │      → Real-time grid with FREE / RESERVED_AVAILABLE / OCCUPIED labels  │
 * │   b) POST /api/bookings  { bookingType: DRIVE_IN, spotId, endTime }     │
 * │      → Booking created as ACTIVE immediately (auto check-in)            │
 * │   c) PUT /api/bookings/{id}/checkout                                    │
 * │                                                                         │
 * │  MANAGER DASHBOARD:                                                     │
 * │   GET /api/bookings/manager/{lotId}/dashboard                           │
 * │   GET /api/bookings/manager/{lotId}/active                              │
 * │   GET /api/bookings/manager/{lotId}/upcoming                            │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService service;

    // ══════════════════════════════════════════════════════════════════════════
    //  SLOT DISCOVERY
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * PRE-BOOKING: Returns spots that are available for the requested time window.
     *
     * GET /api/bookings/slots/{lotId}/available?startTime=2025-05-01T10:00&endTime=2025-05-01T12:00
     *
     * A spot is returned only if NO existing booking overlaps [startTime, endTime]:
     *   overlap ⟺ existing.start < requested.end AND existing.end > requested.start
     *
     * The frontend should render these as selectable slots (BookMyShow-style).
     */
    @GetMapping("/slots/{lotId}/available")
    public ResponseEntity<List<Map<String, Object>>> getAvailableForPreBooking(
            @PathVariable Long lotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        log.info("GET /slots/{}/available?startTime={}&endTime={}", lotId, startTime, endTime);
        return ResponseEntity.ok(service.getAvailableSpotsForPreBooking(lotId, startTime, endTime));
    }

    /**
     * DRIVE-IN: Returns real-time spot grid for on-site users.
     *
     * GET /api/bookings/slots/{lotId}/drive-in
     *
     * Each spot has:
     *   status:            FREE | RESERVED
     *   selectable:        true/false
     *   availabilityLabel: "Available Now" | "Reserved"
     */
    @GetMapping("/slots/{lotId}/drive-in")
    public ResponseEntity<List<DriveInSpotDTO>> getDriveInView(
            @PathVariable Long lotId) {
        log.info("GET /slots/{}/drive-in", lotId);
        return ResponseEntity.ok(service.getDriveInSpotView(lotId));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CORE BOOKING OPERATIONS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a booking.
     * PRE_BOOKING → status RESERVED (driver must check in later within grace period).
     * DRIVE_IN    → status ACTIVE immediately (auto check-in, spot occupied at once).
     */
    @PostMapping
    public ResponseEntity<BookingResponseDTO> create(
            @Valid @RequestBody CreateBookingRequest request,
            Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("POST /api/bookings type={} driver={} spot={}", request.getBookingType(), email, request.getSpotId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createBooking(request, email));
    }

    /** PRE_BOOKING only: check in within the configured grace window. */
    @PutMapping("/{id}/checkin")
    public ResponseEntity<BookingResponseDTO> checkIn(
            @PathVariable Long id, Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("PUT /api/bookings/{}/checkin driver={}", id, email);
        return ResponseEntity.ok(service.checkIn(id, email));
    }

    /** Check out (both booking types). Computes actual fare from checkIn→checkOut duration. */
    @PutMapping("/{id}/checkout")
    public ResponseEntity<BookingResponseDTO> checkOut(
            @PathVariable Long id, Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("PUT /api/bookings/{}/checkout driver={}", id, email);
        return ResponseEntity.ok(service.checkOut(id, email));
    }

    /** Cancel a RESERVED (pre-)booking. Releases spot and lot capacity. */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingResponseDTO> cancel(
            @PathVariable Long id, Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("PUT /api/bookings/{}/cancel driver={}", id, email);
        return ResponseEntity.ok(service.cancelBooking(id, email));
    }

    /** Extend the endTime of an ACTIVE or RESERVED booking (conflict-checked). */
    @PutMapping("/{id}/extend")
    public ResponseEntity<BookingResponseDTO> extend(
            @PathVariable Long id,
            @Valid @RequestBody ExtendBookingRequest request,
            Authentication auth) {
        String email = (String) auth.getPrincipal();
        log.info("PUT /api/bookings/{}/extend driver={}", id, email);
        return ResponseEntity.ok(service.extendBooking(id, request, email));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  QUERY ENDPOINTS (Driver)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/my")
    public ResponseEntity<List<BookingResponseDTO>> getMyBookings(Authentication auth) {
        return ResponseEntity.ok(service.getMyBookings((String) auth.getPrincipal()));
    }

    @GetMapping("/my/active")
    public ResponseEntity<List<BookingResponseDTO>> getMyActive(Authentication auth) {
        return ResponseEntity.ok(service.getActiveBookings((String) auth.getPrincipal()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getBookingById(id));
    }

    @GetMapping("/{id}/fare")
    public ResponseEntity<Double> getFare(@PathVariable Long id) {
        return ResponseEntity.ok(service.calculateFare(id));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MANAGER DASHBOARD (LOT_MANAGER / ADMIN)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Manager notification dashboard: returns both active and upcoming bookings
     * for a lot in a single response.
     *
     * GET /api/bookings/manager/{lotId}/dashboard
     *
     * activeBookings   – drivers currently parked (status = ACTIVE)
     * upcomingBookings – future reservations (status = RESERVED, startTime > now)
     */
    @GetMapping("/manager/{lotId}/dashboard")
    @PreAuthorize("hasAnyRole('LOT_MANAGER', 'ADMIN')")
    public ResponseEntity<ManagerDashboardDTO> getManagerDashboard(@PathVariable Long lotId) {
        log.info("GET /api/bookings/manager/{}/dashboard", lotId);
        return ResponseEntity.ok(service.getManagerDashboard(lotId));
    }

    /**
     * Active bookings only (status = ACTIVE).
     * currentTime is between booking.startTime and booking.endTime.
     */
    @GetMapping("/manager/{lotId}/active")
    @PreAuthorize("hasAnyRole('LOT_MANAGER', 'ADMIN')")
    public ResponseEntity<List<BookingResponseDTO>> getManagerActive(@PathVariable Long lotId) {
        log.info("GET /api/bookings/manager/{}/active", lotId);
        return ResponseEntity.ok(service.getActiveBookingsByLot(lotId));
    }

    /**
     * Upcoming bookings only (status = RESERVED, startTime > now).
     */
    @GetMapping("/manager/{lotId}/upcoming")
    @PreAuthorize("hasAnyRole('LOT_MANAGER', 'ADMIN')")
    public ResponseEntity<List<BookingResponseDTO>> getManagerUpcoming(@PathVariable Long lotId) {
        log.info("GET /api/bookings/manager/{}/upcoming", lotId);
        return ResponseEntity.ok(service.getUpcomingBookingsByLot(lotId));
    }

    /** All bookings for a lot (manager history view). */
    @GetMapping("/lot/{lotId}")
    @PreAuthorize("hasAnyRole('LOT_MANAGER', 'ADMIN')")
    public ResponseEntity<List<BookingResponseDTO>> getByLot(@PathVariable Long lotId) {
        log.info("GET /api/bookings/lot/{}", lotId);
        return ResponseEntity.ok(service.getBookingsByLot(lotId));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ADMIN
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOT_MANAGER')")
    public ResponseEntity<List<BookingResponseDTO>> getAll() {
        log.info("GET /api/bookings/admin/all");
        return ResponseEntity.ok(service.getAllBookings());
    }

    @GetMapping("/internal/lot-ids")
    public ResponseEntity<List<Long>> getDistinctLotIds() {
        return ResponseEntity.ok(service.getDistinctLotIds());
    }

    /**
     * Returns count of active bookings (time window overlaps now) for a lot.
     * Used by parkinglot-service for occupancy calculation.
     */
    @GetMapping("/lot/{lotId}/active-count")
    public ResponseEntity<Integer> getActiveBookingsCount(@PathVariable Long lotId) {
        return ResponseEntity.ok(service.getActiveBookingsCountForLot(lotId));
    }
}
