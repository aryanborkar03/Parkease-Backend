package com.parkease.booking.service;

import com.parkease.booking.dto.request.CreateBookingRequest;
import com.parkease.booking.dto.request.ExtendBookingRequest;
import com.parkease.booking.dto.response.BookingResponseDTO;
import com.parkease.booking.dto.response.DriveInSpotDTO;
import com.parkease.booking.dto.response.ManagerDashboardDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Core booking service contract.
 *
 * Two booking modes are supported:
 *
 *  PRE_BOOKING – Advance reservation with time-window-based slot filtering.
 *                The driver must check in within the configured grace period.
 *
 *  DRIVE_IN    – Immediate on-arrival booking. The booking is auto-activated;
 *                the driver selects from a real-time spot availability view.
 */
public interface BookingService {

    // ── Driver operations ─────────────────────────────────────────────────────

    /**
     * Creates a new booking.
     * - PRE_BOOKING: validates future startTime, checks overlap, status → RESERVED.
     * - DRIVE_IN   : overrides startTime to now(), status → ACTIVE immediately
     *                (auto check-in); endTime = estimated departure.
     */
    BookingResponseDTO createBooking(CreateBookingRequest request, String driverEmail);

    /**
     * Checks in a PRE_BOOKING.  Not applicable to DRIVE_IN (auto-activated).
     * Validates the grace window: startTime ≤ now ≤ startTime + graceMinutes.
     */
    BookingResponseDTO checkIn(Long bookingId, String driverEmail);

    /** Checks out an ACTIVE booking; computes actual fare based on duration. */
    BookingResponseDTO checkOut(Long bookingId, String driverEmail);

    /** Cancels a RESERVED booking; releases spot and lot capacity. */
    BookingResponseDTO cancelBooking(Long bookingId, String driverEmail);

    /** Extends the endTime of an ACTIVE or RESERVED booking if no conflict exists. */
    BookingResponseDTO extendBooking(Long bookingId, ExtendBookingRequest request, String driverEmail);

    // ── Slot discovery ────────────────────────────────────────────────────────

    /**
     * PRE-BOOKING FLOW – Time-filtered slot availability.
     *
     * Returns all spots in the lot that are NOT booked during [startTime, endTime].
     * Excludes spots with status OCCUPIED or MAINTENANCE from the spot-service.
     *
     * Overlap rule (BookMyShow-style):
     *   A spot is unavailable when:
     *     existing.startTime < requested.endTime  AND
     *     existing.endTime   > requested.startTime
     *
     * @param lotId     Lot to query
     * @param startTime Requested start of the parking window
     * @param endTime   Requested end of the parking window
     * @return List of spot maps (raw from spot-service) that are free in the window
     */
    List<Map<String, Object>> getAvailableSpotsForPreBooking(
            Long lotId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * DRIVE-IN FLOW – Real-time spot grid.
     *
     * Returns all spots in the lot with an enriched availability status:
     *   FREE     → Fully available, selectable.
     *   RESERVED → Has a reservation; NOT selectable.
     *
     * @param lotId Lot to query
     * @return List of DriveInSpotDTO with live availability data
     */
    List<DriveInSpotDTO> getDriveInSpotView(Long lotId);

    // ── Query operations ──────────────────────────────────────────────────────

    BookingResponseDTO       getBookingById(Long bookingId);
    List<BookingResponseDTO> getMyBookings(String driverEmail);
    List<BookingResponseDTO> getActiveBookings(String driverEmail);
    List<BookingResponseDTO> getBookingsByLot(Long lotId);
    List<BookingResponseDTO> getAllBookings();   // admin only
    List<Long>               getDistinctLotIds();

    // ── Manager Dashboard ─────────────────────────────────────────────────────

    /**
     * Returns bookings currently in progress for a lot (status = ACTIVE).
     * For the manager notification section: "Active Bookings".
     */
    List<BookingResponseDTO> getActiveBookingsByLot(Long lotId);

    /**
     * Returns future reservations for a lot (status = RESERVED, startTime > now).
     * For the manager notification section: "Upcoming Bookings".
     */
    List<BookingResponseDTO> getUpcomingBookingsByLot(Long lotId);

    /**
     * Aggregated manager dashboard: active + upcoming bookings with counts.
     */
    ManagerDashboardDTO getManagerDashboard(Long lotId);

    // ── Occupancy calculation ───────────────────────────────────────────────────

    /**
     * Returns count of bookings whose time window overlaps "now" for a lot.
     * Used by parkinglot-service to calculate occupancy on Find Parking page.
     */
    int getActiveBookingsCountForLot(Long lotId);

    // ── Fare calculation ──────────────────────────────────────────────────────

    double calculateFare(Long bookingId);
}
