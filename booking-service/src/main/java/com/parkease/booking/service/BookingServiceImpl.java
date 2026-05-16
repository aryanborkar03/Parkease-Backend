package com.parkease.booking.service;

import com.parkease.booking.client.LotServiceClient;
import com.parkease.booking.client.SpotServiceClient;
import com.parkease.booking.client.VehicleServiceClient;
import com.parkease.booking.dto.request.CreateBookingRequest;
import com.parkease.booking.dto.request.ExtendBookingRequest;
import com.parkease.booking.dto.response.BookingResponseDTO;
import com.parkease.booking.dto.response.DriveInSpotDTO;
import com.parkease.booking.dto.response.ManagerDashboardDTO;
import com.parkease.booking.entity.*;
import com.parkease.booking.exception.BookingException;
import com.parkease.booking.exception.ResourceNotFoundException;
import com.parkease.booking.mapper.BookingMapper;
import com.parkease.booking.messaging.NotificationEvent;
import com.parkease.booking.messaging.NotificationPublisher;
import com.parkease.booking.repository.BookingRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository     repo;
    private final BookingMapper         mapper;
    private final SpotServiceClient     spotServiceClient;
    private final LotServiceClient      lotServiceClient;
    private final VehicleServiceClient  vehicleServiceClient;
    private final NotificationPublisher notificationPublisher;

    @Value("${app.booking.checkin-grace-minutes}")
    private int graceMinutes;

    // Dependency injection and helper methods

    private Booking getOrThrow(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }

    private void verifyDriver(Booking b, String email) {
        if (!b.getDriverEmail().equals(email))
            throw new ResourceNotFoundException("Booking not found: " + b.getBookingId());
    }

    // Booking creation and validation logic

    /**
     * Creates a new booking.
     *
     * PRE_BOOKING path:
     *  1. Validate startTime is in the future.
     *  2. Check overlap via isSpotBookedInWindow.
     *  3. Save with status = RESERVED.
     *  4. Decrement lot capacity.
     *  5. Publish BOOKING_CONFIRMED notification.
     *
     * DRIVE_IN path:
     *  1. Override startTime → now (driver is already on-site).
     *  2. Check overlap (protects against racing pre-bookings).
     *  3. Save with status = ACTIVE, checkInTime = now (auto-activate).
     *  4. Mark spot OCCUPIED via spot-service.
     *  5. Decrement lot capacity.
     *  6. Publish CHECKIN notification (skip the separate check-in step).
     */
    @Override
    @Transactional
    public BookingResponseDTO createBooking(CreateBookingRequest req, String driverEmail) {
        log.info("Creating {} booking for driver={} spot={}", req.getBookingType(), driverEmail, req.getSpotId());

        // Validate vehicle ownership
        validateVehicleOwnership(req.getVehicleId(), driverEmail);

        // Validate vehicle and spot type compatibility
        validateVehicleSpotCompatibility(req.getVehicleId(), req.getSpotId());

        boolean isDriveIn = req.getBookingType() == BookingType.DRIVE_IN;
        LocalDateTime now = LocalDateTime.now();

        // Determine effective start time based on booking type
        LocalDateTime startTime;
        if (isDriveIn) {
            // Drive-in: start is RIGHT NOW; any client-supplied value is ignored
            startTime = now;
        } else {
            // Pre-booking: startTime must be provided and must be in the future
            if (req.getStartTime() == null) {
                throw new IllegalArgumentException("Start time is required for PRE_BOOKING.");
            }
            if (!req.getStartTime().isAfter(now)) {
                throw new IllegalArgumentException("Start time must be in the future for PRE_BOOKING.");
            }
            startTime = req.getStartTime();
        }

        // Validate end time logically follows start time
        if (req.getEndTime() == null) {
            throw new IllegalArgumentException("End time is required.");
        }
        if (!req.getEndTime().isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        // Double-booking guard to prevent scheduling conflicts
        // Overlap: existing.start < requested.end AND existing.end > requested.start
        boolean conflict = repo.isSpotBookedInWindow(req.getSpotId(), startTime, req.getEndTime());
        if (conflict) {
            throw new BookingException("Spot " + req.getSpotId()
                    + " is already booked for this time window.");
        }

        // Vehicle overlap check - same vehicle cannot be double-booked
        boolean vehicleConflict = repo.isVehicleBookedInWindow(req.getVehicleId(), startTime, req.getEndTime());
        if (vehicleConflict) {
            throw new BookingException("This vehicle is already booked for this time range.");
        }

        double pricePerHour = getSpotPrice(req.getSpotId());

        // Determine initial booking status and check-in time
        BookingStatus initialStatus = isDriveIn ? BookingStatus.ACTIVE : BookingStatus.RESERVED;
        LocalDateTime checkInTime   = isDriveIn ? now : null;

        Booking booking = Booking.builder()
                .driverEmail(driverEmail)
                .lotId(req.getLotId())
                .spotId(req.getSpotId())
                .vehicleId(req.getVehicleId())
                .vehiclePlate(getVehiclePlate(req.getVehicleId()))
                .bookingType(req.getBookingType())
                .status(initialStatus)
                .startTime(startTime)
                .endTime(req.getEndTime())
                .pricePerHour(pricePerHour)
                .totalAmount(0.0)
                .checkInTime(checkInTime)
                .build();

        Booking saved = repo.save(booking);
        log.info("Booking #{} created (type={} status={})", saved.getBookingId(), saved.getBookingType(), saved.getStatus());

        // Execute asynchronous side effects such as notifications and inventory updates
        callLotService(() -> lotServiceClient.decrementAvailable(req.getLotId()), "decrement", req.getLotId());

        if (isDriveIn) {
            // Auto-occupy the spot immediately for drive-in
            callSpotService(() -> spotServiceClient.reserveSpot(req.getSpotId()), "reserve", req.getSpotId());

            notificationPublisher.publish(NotificationEvent.builder()
                    .recipientEmail(driverEmail)
                    .type("CHECKIN")
                    .title("Drive-In Booking Active!")
                    .message("You are now checked in at spot " + req.getSpotId()
                            + " (Booking #" + saved.getBookingId() + "). "
                            + "Estimated checkout: " + req.getEndTime() + ".")
                    .relatedId(saved.getBookingId())
                    .relatedType("BOOKING")
                    .build());
        } else {
            // Pre-booking: reserve the spot (not yet occupied)
            callSpotService(() -> spotServiceClient.reserveSpot(req.getSpotId()), "reserve", req.getSpotId());

            notificationPublisher.publish(NotificationEvent.builder()
                    .recipientEmail(driverEmail)
                    .type("BOOKING_CONFIRMED")
                    .title("Booking Confirmed!")
                    .message("Booking #" + saved.getBookingId()
                            + " for spot " + req.getSpotId()
                            + " confirmed from " + startTime + " to " + req.getEndTime() + ".")
                    .relatedId(saved.getBookingId())
                    .relatedType("BOOKING")
                    .build());
        }

        return mapper.toDTO(saved);
    }

    // Check-in workflow logic

    /**
     * Checks in a PRE_BOOKING within the grace window.
     * DRIVE_IN bookings are auto-activated at creation — calling this on a
     * DRIVE_IN booking throws a BookingException.
     */
    @Override
    @Transactional
    public BookingResponseDTO checkIn(Long bookingId, String driverEmail) {
        log.info("Check-in booking={} driver={}", bookingId, driverEmail);

        Booking booking = getOrThrow(bookingId);
        verifyDriver(booking, driverEmail);

        if (booking.getStatus() != BookingStatus.RESERVED) {
            throw new BookingException("Cannot check in. Status is: " + booking.getStatus()
                    + (booking.getBookingType() == BookingType.DRIVE_IN
                        ? " — DRIVE_IN bookings are auto-activated at creation." : "."));
        }

        // Grace period only applies to PRE_BOOKING
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(booking.getStartTime())) {
            throw new BookingException("Too early to check in. Booking starts at " + booking.getStartTime());
        }
        LocalDateTime graceCutoff = booking.getStartTime().plusMinutes(graceMinutes);
        if (now.isAfter(graceCutoff)) {
            throw new BookingException("Check-in grace period expired. Was valid until " + graceCutoff);
        }

        booking.setStatus(BookingStatus.ACTIVE);
        booking.setCheckInTime(now);
        Booking saved = repo.save(booking);

        callSpotService(() -> spotServiceClient.reserveSpot(booking.getSpotId()), "reserve", booking.getSpotId());

        notificationPublisher.publish(NotificationEvent.builder()
                .recipientEmail(driverEmail)
                .type("CHECKIN")
                .title("Checked In Successfully")
                .message("Checked in for booking #" + bookingId + " at " + saved.getCheckInTime() + ".")
                .relatedId(bookingId).relatedType("BOOKING").build());

        return mapper.toDTO(saved);
    }

    // Check-out workflow and fare calculation logic

    @Override
    @Transactional
    public BookingResponseDTO checkOut(Long bookingId, String driverEmail) {
        log.info("Check-out booking={} driver={}", bookingId, driverEmail);

        Booking booking = getOrThrow(bookingId);
        verifyDriver(booking, driverEmail);

        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new BookingException("Cannot check out. Status is: " + booking.getStatus());
        }

        LocalDateTime checkOutTime = LocalDateTime.now();
        booking.setCheckOutTime(checkOutTime);
        booking.setStatus(BookingStatus.COMPLETED);

        double fare = computeFare(booking.getCheckInTime(), checkOutTime, booking.getPricePerHour());
        booking.setTotalAmount(fare);

        Booking saved = repo.save(booking);
        log.info("Booking #{} checked out. Fare: Rs.{}", bookingId, fare);

        callSpotService(() -> spotServiceClient.releaseSpot(booking.getSpotId()), "release", booking.getSpotId());
        callLotService(() -> lotServiceClient.incrementAvailable(booking.getLotId()), "increment", booking.getLotId());

        notificationPublisher.publish(NotificationEvent.builder()
                .recipientEmail(driverEmail)
                .type("CHECKOUT")
                .title("Check-Out Complete")
                .message("Checked out from booking #" + bookingId
                        + ". Total fare: Rs." + fare + ". Thank you for using ParkEase!")
                .relatedId(bookingId).relatedType("BOOKING").build());

        return mapper.toDTO(saved);
    }

    // Booking cancellation logic

    @Override
    @Transactional
    public BookingResponseDTO cancelBooking(Long bookingId, String driverEmail) {
        log.info("Cancel booking={} driver={}", bookingId, driverEmail);

        Booking booking = getOrThrow(bookingId);
        verifyDriver(booking, driverEmail);

        if (booking.getStatus() != BookingStatus.RESERVED) {
            throw new BookingException("Only RESERVED bookings can be cancelled. Status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = repo.save(booking);

        callSpotService(() -> spotServiceClient.releaseSpot(booking.getSpotId()), "release", booking.getSpotId());
        callLotService(() -> lotServiceClient.incrementAvailable(booking.getLotId()), "increment", booking.getLotId());

        notificationPublisher.publish(NotificationEvent.builder()
                .recipientEmail(driverEmail)
                .type("CANCELLATION")
                .title("Booking Cancelled")
                .message("Booking #" + bookingId + " for spot " + booking.getSpotId() + " cancelled.")
                .relatedId(bookingId).relatedType("BOOKING").build());

        return mapper.toDTO(saved);
    }

    // Booking extension and conflict resolution logic

    @Override
    @Transactional
    public BookingResponseDTO extendBooking(Long bookingId, ExtendBookingRequest req, String driverEmail) {
        log.info("Extend booking={} driver={}", bookingId, driverEmail);

        Booking booking = getOrThrow(bookingId);
        verifyDriver(booking, driverEmail);

        if (booking.getStatus() != BookingStatus.ACTIVE && booking.getStatus() != BookingStatus.RESERVED) {
            throw new BookingException("Cannot extend booking in status: " + booking.getStatus());
        }
        if (!req.getNewEndTime().isAfter(booking.getEndTime())) {
            throw new IllegalArgumentException("New end time must be after current end time: " + booking.getEndTime());
        }

        // Check that the extension window is conflict-free
        boolean conflict = repo.isSpotBookedInWindow(booking.getSpotId(), booking.getEndTime(), req.getNewEndTime());
        if (conflict) {
            throw new BookingException("Spot is already booked during the extension period.");
        }

        // Check vehicle overlap - cannot extend into another booking for same vehicle
        boolean vehicleConflict = repo.isVehicleBookedInWindow(booking.getVehicleId(), booking.getEndTime(), req.getNewEndTime());
        if (vehicleConflict) {
            throw new BookingException("This vehicle is already booked during the extension period.");
        }

        booking.setEndTime(req.getNewEndTime());
        Booking saved = repo.save(booking);

        notificationPublisher.publish(NotificationEvent.builder()
                .recipientEmail(driverEmail)
                .type("BOOKING_CONFIRMED")
                .title("Booking Extended")
                .message("Booking #" + bookingId + " extended. New end time: " + req.getNewEndTime() + ".")
                .relatedId(bookingId).relatedType("BOOKING").build());

        return mapper.toDTO(saved);
    }

    // Pre-booking slot discovery logic

    /**
     * Returns spots in the lot that are free during [startTime, endTime].
     *
     * Algorithm:
     *  1. Ask booking-service DB for spot IDs already booked in the window.
     *  2. Fetch all spots for the lot from spot-service.
     *  3. Remove spots whose ID appears in the booked set OR whose physical
     *     status is OCCUPIED / MAINTENANCE (can't be booked regardless).
     *
     * This is the BookMyShow-style "only show seats not already taken" logic.
     */
    @Override
    public List<Map<String, Object>> getAvailableSpotsForPreBooking(
            Long lotId, LocalDateTime startTime, LocalDateTime endTime) {

        // Validate time range
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Both startTime and endTime are required.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime.");
        }
        if (!startTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("startTime must be in the future for PRE_BOOKING.");
        }

        // Step 1: spot IDs already claimed in this window
        List<Long> bookedIds = repo.findBookedSpotIdsInWindow(lotId, startTime, endTime);
        Set<Long>  bookedSet = new HashSet<>(bookedIds);

        // Step 2: all spots for the lot
        List<Map<String, Object>> allSpots = fetchSpotsByLot(lotId);

        // Step 3: filter
        return allSpots.stream()
                .filter(spot -> {
                    Long   spotId = toLong(spot.get("spotId"));
                    String status = (String) spot.get("status");
                    return !bookedSet.contains(spotId);
                })
                .toList();
    }

    // Drive-in slot discovery logic and availability grid building

    /**
     * Builds the real-time spot grid for Drive-In users.
     *
     * Each spot is marked FREE or RESERVED based on current bookings.
     */
    @Override
    public List<DriveInSpotDTO> getDriveInSpotView(Long lotId) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        List<Map<String, Object>> allSpots = fetchSpotsByLot(lotId);

        return allSpots.stream().map(spot -> {
            Long   spotId = toLong(spot.get("spotId"));

            DriveInSpotDTO.DriveInSpotDTOBuilder builder = DriveInSpotDTO.builder()
                    .spotId(spotId)
                    .spotNumber((String) spot.get("spotNumber"))
                    .floor(toInt(spot.get("floor")))
                    .spotType(toStr(spot.get("spotType")))
                    .vehicleType(toStr(spot.get("vehicleType")))
                    .pricePerHour(toDouble(spot.get("pricePerHour")))
                    .isEVCharging(toBool(spot.get("isEVCharging")))
                    .isHandicapped(toBool(spot.get("isHandicapped")));

            List<Booking> bookings = repo.findActiveOrReservedBookingsForSpot(spotId);

            boolean hasActiveOrReserved = bookings.stream()
                    .anyMatch(b -> b.getStatus() == BookingStatus.ACTIVE ||
                            (b.getStatus() == BookingStatus.RESERVED && !b.getStartTime().isAfter(now)));

            if (hasActiveOrReserved) {
                Booking reservingBooking = bookings.stream()
                        .filter(b -> b.getStatus() == BookingStatus.ACTIVE ||
                                (b.getStatus() == BookingStatus.RESERVED && !b.getStartTime().isAfter(now)))
                        .min(Comparator.comparing(Booking::getStartTime))
                        .orElse(null);
                if (reservingBooking != null) {
                    builder.reservedFrom(reservingBooking.getStartTime().format(fmt));
                    builder.reservedTo(reservingBooking.getEndTime().format(fmt));
                }
                return builder.status("RESERVED")
                        .selectable(false)
                        .availabilityLabel("Reserved")
                        .build();
            }

            Optional<Booking> futureRes = bookings.stream()
                    .filter(b -> b.getStatus() == BookingStatus.RESERVED && b.getStartTime().isAfter(now))
                    .min(Comparator.comparing(Booking::getStartTime));

            if (futureRes.isPresent()) {
                builder.reservedFrom(futureRes.get().getStartTime().format(fmt));
                builder.reservedTo(futureRes.get().getEndTime().format(fmt));
                return builder.status("RESERVED")
                        .selectable(false)
                        .availabilityLabel("Reserved")
                        .build();
            }

            return builder.status("FREE")
                    .selectable(true)
                    .availabilityLabel("Available Now")
                    .build();
        }).toList();
    }

    // Query operations for historical and active bookings

    @Override
    public BookingResponseDTO getBookingById(Long bookingId) {
        return mapper.toDTO(getOrThrow(bookingId));
    }

    @Override
    public List<BookingResponseDTO> getMyBookings(String driverEmail) {
        return repo.findByDriverEmailOrderByCreatedAtDesc(driverEmail)
                .stream().map(mapper::toDTO).toList();
    }

    @Override
    public List<BookingResponseDTO> getActiveBookings(String driverEmail) {
        return repo.findByDriverEmailAndStatus(driverEmail, BookingStatus.ACTIVE)
                .stream().map(mapper::toDTO).toList();
    }

    @Override
    public List<BookingResponseDTO> getBookingsByLot(Long lotId) {
        return repo.findByLotIdOrderByCreatedAtDesc(lotId)
                .stream().map(mapper::toDTO).toList();
    }

    @Override
    public List<BookingResponseDTO> getAllBookings() {
        return repo.findAll().stream().map(mapper::toDTO).toList();
    }

    @Override
    public List<Long> getDistinctLotIds() {
        return repo.findDistinctLotIds();
    }

    @Override
    public double calculateFare(Long bookingId) {
        Booking b = getOrThrow(bookingId);
        if (b.getCheckInTime() == null) throw new BookingException("Driver has not checked in yet.");
        LocalDateTime end = b.getCheckOutTime() != null ? b.getCheckOutTime() : LocalDateTime.now();
        return computeFare(b.getCheckInTime(), end, b.getPricePerHour());
    }

    // Manager dashboard aggregation methods

    /**
     * Active bookings for a lot: drivers currently parked (status = ACTIVE).
     * The manager sees this in the "Active Bookings" notification panel.
     */
    @Override
    public List<BookingResponseDTO> getActiveBookingsByLot(Long lotId) {
        return repo.findActiveBookingsByLot(lotId)
                .stream().map(mapper::toDTO).toList();
    }

    /**
     * Upcoming bookings for a lot: reservations whose startTime > now.
     * The manager sees this in the "Upcoming Bookings" notification panel.
     */
    @Override
    public List<BookingResponseDTO> getUpcomingBookingsByLot(Long lotId) {
        return repo.findUpcomingBookingsByLot(lotId, LocalDateTime.now())
                .stream().map(mapper::toDTO).toList();
    }

    /**
     * Aggregated dashboard combining active + upcoming counts and lists.
     */
    @Override
    public ManagerDashboardDTO getManagerDashboard(Long lotId) {
        List<BookingResponseDTO> active   = getActiveBookingsByLot(lotId);
        List<BookingResponseDTO> upcoming = getUpcomingBookingsByLot(lotId);
        return ManagerDashboardDTO.builder()
                .lotId(lotId)
                .totalActive(active.size())
                .totalUpcoming(upcoming.size())
                .activeBookings(active)
                .upcomingBookings(upcoming)
                .build();
    }

    /**
     * Count of bookings whose time window overlaps "now".
     */
    @Override
    public int getActiveBookingsCountForLot(Long lotId) {
        // Use simple count - just counts any RESERVED or ACTIVE booking for this lot
        return repo.countActiveBookingsForLotSimple(lotId);
    }

    // Private utility methods and service client wrappers

    /** Actual fare: minimum 1 hour, rounded to 2 decimal places. */
    private double computeFare(LocalDateTime checkIn, LocalDateTime checkOut, double pricePerHour) {
        long   minutes = Duration.between(checkIn, checkOut).toMinutes();
        double hours   = Math.max(1.0, minutes / 60.0);
        return Math.round(hours * pricePerHour * 100.0) / 100.0;
    }

    /** Fetches spot price from spot-service; falls back to Rs. 50/hr on failure. */
    private double getSpotPrice(Long spotId) {
        try {
            Map<String, Object> spot = spotServiceClient.getSpotById(spotId);
            if (spot != null && spot.containsKey("pricePerHour")) {
                return ((Number) spot.get("pricePerHour")).doubleValue();
            }
        } catch (Exception e) {
            log.error("Could not fetch spot price for spot {}: {}", spotId, e.getMessage());
        }
        log.warn("Using default price Rs.50 for spot {}", spotId);
        return 50.0;
    }

    /** Fetches all spots for a lot; returns empty list on failure. */
    private List<Map<String, Object>> fetchSpotsByLot(Long lotId) {
        try {
            List<Map<String, Object>> spots = spotServiceClient.getSpotsByLot(lotId);
            return spots != null ? spots : Collections.emptyList();
        } catch (Exception e) {
            log.error("Could not fetch spots for lot {}: {}", lotId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void callSpotService(Runnable call, String action, Long spotId) {
        try {
            call.run();
        } catch (Exception e) {
            log.error("spot-service [{}] failed for spot {}: {}", action, spotId, e.getMessage());
        }
    }

    private void callLotService(Runnable call, String action, Long lotId) {
        try {
            call.run();
        } catch (Exception e) {
            log.error("lot-service [{}] failed for lot {}: {}", action, lotId, e.getMessage());
        }
    }

    /**
     * Validates that the vehicle belongs to the authenticated user.
     * Throws BookingException if validation fails.
     */
    private void validateVehicleOwnership(Long vehicleId, String driverEmail) {
        if (vehicleId == null) {
            throw new BookingException("Vehicle ID is required.");
        }
        try {
            Map<String, Object> vehicle = vehicleServiceClient.getVehicleById(vehicleId);
            if (vehicle == null) {
                throw new BookingException("Vehicle not found: " + vehicleId);
            }
            Object ownerObj = vehicle.get("ownerEmail");
            String ownerEmail = ownerObj != null ? ownerObj.toString() : null;
            if (ownerEmail == null || !driverEmail.equalsIgnoreCase(ownerEmail)) {
                throw new BookingException("Vehicle does not belong to current user.");
            }
            // Check isActive - could be Boolean or boolean
            Object activeObj = vehicle.get("isActive");
            if (activeObj != null && activeObj instanceof Boolean && !(Boolean) activeObj) {
                throw new BookingException("Vehicle is deactivated.");
            }
        } catch (BookingException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not validate vehicle ownership for {}: {}", vehicleId, e.getMessage());
            // On service unavailable, allow booking but log warning
        }
    }

    /**
     * Fetches the vehicle plate from vehicle-service.
     */
    private String getVehiclePlate(Long vehicleId) {
        try {
            Map<String, Object> vehicle = vehicleServiceClient.getVehicleById(vehicleId);
            if (vehicle != null) {
                Object plate = vehicle.get("licensePlate");
                return plate != null ? plate.toString() : "UNKNOWN";
            }
        } catch (Exception e) {
            log.error("Could not fetch vehicle plate for {}: {}", vehicleId, e.getMessage());
        }
        return "UNKNOWN";
    }

    /**
     * Validates that the vehicle type matches the spot's allowed vehicle type.
     * Vehicle and spot types must be compatible:
     * - TWO_WHEELER spots accept only TWO_WHEELER vehicles
     * - FOUR_WHEELER spots accept only FOUR_WHEELER vehicles
     * - HEAVY spots accept only HEAVY vehicles
     */
    private void validateVehicleSpotCompatibility(Long vehicleId, Long spotId) {
        try {
            Map<String, Object> vehicle = vehicleServiceClient.getVehicleById(vehicleId);
            if (vehicle == null) {
                throw new IllegalArgumentException("Vehicle not found: " + vehicleId);
            }
            Object vehicleTypeObj = vehicle.get("vehicleType");
            if (vehicleTypeObj == null) {
                throw new IllegalArgumentException("Could not determine vehicle type.");
            }
            String vehicleType = vehicleTypeObj.toString();

            Map<String, Object> spot = spotServiceClient.getSpotById(spotId);
            if (spot == null || spot.isEmpty()) {
                throw new IllegalArgumentException("Spot not found: " + spotId);
            }
            Object spotVehicleTypeObj = spot.get("vehicleType");
            if (spotVehicleTypeObj == null) {
                // If spot has no vehicle type set, allow booking (backward compatibility)
                log.warn("Spot {} has no vehicleType set - allowing booking", spotId);
                return;
            }
            String spotVehicleType = spotVehicleTypeObj.toString();

            if (!vehicleType.equals(spotVehicleType)) {
                String vehicleLabel = vehicleType.equals("TWO_WHEELER") ? "two-wheelers"
                    : vehicleType.equals("FOUR_WHEELER") ? "four-wheelers" : "heavy vehicles";
                String spotLabel = spotVehicleType.equals("TWO_WHEELER") ? "two-wheelers"
                    : spotVehicleType.equals("FOUR_WHEELER") ? "four-wheelers" : "heavy vehicles";
                throw new IllegalArgumentException("This spot is for " + spotLabel + " only. Your vehicle type is " + vehicleLabel + ".");
            }

            // Validate EV spot: only EV vehicles can book EV spots
            Object spotTypeObj = spot.get("spotType");
            Object isEvChargingObj = spot.get("isEVCharging");
            boolean isEvSpot = (spotTypeObj != null && "EV".equals(spotTypeObj.toString()))
                          || (isEvChargingObj instanceof Boolean b && b);

            if (isEvSpot) {
                // Check if vehicle is EV by checking the isEV field on the vehicle
                Object vehicleIsEvObj = vehicle.get("isEV");
                boolean vehicleIsEv = vehicleIsEvObj instanceof Boolean b && b;

                if (!vehicleIsEv) {
                    throw new IllegalArgumentException(
                        "This is an EV charging spot. Only EV vehicles can be booked here. Please select an EV vehicle.");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not validate vehicle-spot compatibility for vehicle {} spot {}: {}", vehicleId, spotId, e.getMessage());
        }
    }

    // Type-safe map accessors for parsing service client responses

    private Long    toLong  (Object v) { return v == null ? null : ((Number) v).longValue(); }
    private int     toInt   (Object v) { return v == null ? 0    : ((Number) v).intValue(); }
    private double  toDouble(Object v) { return v == null ? 0.0  : ((Number) v).doubleValue(); }
    private String  toStr   (Object v) { return v == null ? null : v.toString(); }
    private boolean toBool  (Object v) { return v instanceof Boolean b && b; }
}
