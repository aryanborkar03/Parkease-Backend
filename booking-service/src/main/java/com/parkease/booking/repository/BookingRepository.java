package com.parkease.booking.repository;

import com.parkease.booking.entity.Booking;
import com.parkease.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // ── Basic finders ─────────────────────────────────────────────────────────

    List<Booking> findByDriverEmailOrderByCreatedAtDesc(String driverEmail);

    List<Booking> findByLotIdOrderByCreatedAtDesc(Long lotId);

    List<Booking> findBySpotId(Long spotId);

    Optional<Booking> findBySpotIdAndStatus(Long spotId, BookingStatus status);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByDriverEmailAndStatus(String driverEmail, BookingStatus status);

    List<Booking> findByLotIdAndStatus(Long lotId, BookingStatus status);

    int countByLotIdAndStatus(Long lotId, BookingStatus status);

    // ── Scheduler: auto-cancel expired PRE_BOOKINGs ───────────────────────────

    /**
     * Finds PRE_BOOKING bookings still in RESERVED status whose startTime is
     * before the cutoff (startTime + graceMinutes has already passed).
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.status = 'RESERVED'
          AND b.bookingType = 'PRE_BOOKING'
          AND b.startTime < :cutoffTime
        """)
    List<Booking> findExpiredPreBookings(@Param("cutoffTime") LocalDateTime cutoffTime);

    // ── Double-booking guard ──────────────────────────────────────────────────

    /**
     * Returns true if the given spot already has a RESERVED or ACTIVE booking
     * whose time window overlaps [startTime, endTime].
     *
     * Overlap condition (BookMyShow-style):
     *   existing.startTime < requested.endTime  AND
     *   existing.endTime   > requested.startTime
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.spotId = :spotId
          AND b.status IN ('RESERVED', 'ACTIVE')
          AND b.startTime < :endTime
          AND b.endTime   > :startTime
        """)
    boolean isSpotBookedInWindow(
            @Param("spotId")    Long          spotId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime")   LocalDateTime endTime
    );

    /**
     * Returns true if the given vehicle already has a RESERVED or ACTIVE booking
     * whose time window overlaps [startTime, endTime].
     * This prevents the same vehicle from being double-booked.
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.vehicleId = :vehicleId
          AND b.status IN ('RESERVED', 'ACTIVE')
          AND b.startTime < :endTime
          AND b.endTime   > :startTime
        """)
    boolean isVehicleBookedInWindow(
            @Param("vehicleId") Long          vehicleId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime")   LocalDateTime endTime
    );

    // ── Pre-booking: time-filtered slot discovery ─────────────────────────────

    /**
     * Returns the IDs of spots in the given lot that are already booked
     * (RESERVED or ACTIVE) within the requested [startTime, endTime] window.
     * The frontend uses this to grey-out unavailable slots.
     */
    @Query("""
        SELECT DISTINCT b.spotId FROM Booking b
        WHERE b.lotId     = :lotId
          AND b.status    IN ('RESERVED', 'ACTIVE')
          AND b.startTime < :endTime
          AND b.endTime   > :startTime
        """)
    List<Long> findBookedSpotIdsInWindow(
            @Param("lotId")     Long          lotId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime")   LocalDateTime endTime
    );

    // ── Drive-in: per-spot live reservation lookup ────────────────────────────

    /**
     * Returns all RESERVED or ACTIVE bookings for a specific spot, ordered by
     * startTime. Used by the drive-in view to find the nearest upcoming
     * reservation and label the spot "Available until HH:mm".
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.spotId = :spotId
          AND b.status IN ('RESERVED', 'ACTIVE')
        ORDER BY b.startTime ASC
        """)
    List<Booking> findActiveOrReservedBookingsForSpot(@Param("spotId") Long spotId);

    // ── Manager dashboard ─────────────────────────────────────────────────────

    /**
     * Active bookings for a lot: status = ACTIVE (driver has checked in).
     * currentTime is implicitly "now" — any ACTIVE booking is by definition
     * currently in progress.
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.lotId  = :lotId
          AND b.status = 'ACTIVE'
        ORDER BY b.startTime ASC
        """)
    List<Booking> findActiveBookingsByLot(@Param("lotId") Long lotId);

    /**
     * Count of bookings whose time window overlaps "now" for a lot.
     * Includes both RESERVED (upcoming/current) and ACTIVE (in-progress) bookings.
     */
    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.lotId = :lotId
          AND b.status IN ('RESERVED', 'ACTIVE')
          AND b.startTime <= :now
          AND b.endTime >= :now
        """)
    int countActiveBookingsForLot(
            @Param("lotId") Long lotId,
            @Param("now") LocalDateTime now
    );

    /**
     * Simple count: all RESERVED or ACTIVE bookings for a lot.
     * Ignores time windows - just counts any active reservation.
     */
    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.lotId = :lotId
          AND b.status IN ('RESERVED', 'ACTIVE')
        """)
    int countActiveBookingsForLotSimple(@Param("lotId") Long lotId);

    /**
     * Upcoming bookings for a lot: status = RESERVED, startTime > now.
     * Shows the manager who is expected to arrive next.
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.lotId     = :lotId
          AND b.status    = 'RESERVED'
          AND b.startTime > :now
        ORDER BY b.startTime ASC
        """)
    List<Booking> findUpcomingBookingsByLot(
            @Param("lotId") Long          lotId,
            @Param("now")   LocalDateTime now
    );

    // ── Analytics helper ──────────────────────────────────────────────────────

    @Query("SELECT DISTINCT b.lotId FROM Booking b")
    List<Long> findDistinctLotIds();
}
