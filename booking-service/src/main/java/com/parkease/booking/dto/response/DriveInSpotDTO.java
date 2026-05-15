package com.parkease.booking.dto.response;

import lombok.*;

/**
 * Represents a parking spot as seen by a Drive-In user.
 *
 * Two possible availability states:
 *
 *  FREE              – No existing booking; fully selectable.
 *  RESERVED          – Spot has a reservation; NOT selectable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveInSpotDTO {

    /** Spot identifier */
    private Long   spotId;

    /** Human-readable spot number (e.g. "A1-03") */
    private String spotNumber;

    /** Floor level inside the parking lot */
    private int    floor;

    /** REGULAR, COMPACT, PREMIUM, etc. */
    private String spotType;

    /** CAR, BIKE, TRUCK, etc. */
    private String vehicleType;

    /** FREE or RESERVED */
    private String status;

    private double  pricePerHour;
    private boolean isEVCharging;
    private boolean isHandicapped;

    /**
     * Whether the drive-in user can select this spot right now.
     * False when the spot is RESERVED.
     */
    private boolean selectable;

    /**
     * Human-readable availability hint shown in the UI:
     *   "Available Now"   → FREE
     *   "Reserved"       → RESERVED
     */
    private String availabilityLabel;

    /**
     * The start time of the booking that currently makes this spot RESERVED.
     * Formatted as "dd-MM-yyyy HH:mm". Null for FREE spots.
     */
    private String reservedFrom;

    /**
     * The end time of the booking that currently makes this spot RESERVED.
     * Formatted as "dd-MM-yyyy HH:mm". Null for FREE spots.
     */
    private String reservedTo;
}
