package com.parkease.booking.mapper;

import com.parkease.booking.dto.response.BookingResponseDTO;
import com.parkease.booking.entity.Booking;
import com.parkease.booking.entity.BookingStatus;
import com.parkease.booking.entity.BookingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BookingMapperTest {

    private BookingMapper mapper;
    private Booking booking;

    @BeforeEach
    void setUp() {
        mapper = new BookingMapper();

        booking = Booking.builder()
                .bookingId(1L)
                .driverEmail("aryan@test.com")
                .lotId(10L)
                .spotId(101L)
                .vehiclePlate("MP04AB1234")
                .bookingType(BookingType.PRE_BOOKING)
                .status(BookingStatus.RESERVED)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(3))
                .pricePerHour(50.0)
                .totalAmount(0.0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldMapAllFieldsCorrectly() {
        BookingResponseDTO dto = mapper.toDTO(booking);

        assertNotNull(dto);
        assertEquals(booking.getBookingId(), dto.getBookingId());
        assertEquals(booking.getDriverEmail(), dto.getDriverEmail());
        assertEquals(booking.getLotId(), dto.getLotId());
        assertEquals(booking.getSpotId(), dto.getSpotId());
        assertEquals(booking.getVehiclePlate(), dto.getVehiclePlate());
        assertEquals(booking.getBookingType(), dto.getBookingType());
        assertEquals(booking.getStatus(), dto.getStatus());
        assertEquals(booking.getPricePerHour(), dto.getPricePerHour());
    }

    @Test
    void shouldCalculateEstimatedAmountForTwoHours() {
        booking.setStartTime(LocalDateTime.now());
        booking.setEndTime(LocalDateTime.now().plusHours(2));
        booking.setPricePerHour(50.0);

        BookingResponseDTO dto = mapper.toDTO(booking);

        assertEquals(100.0, dto.getEstimatedAmount(), 0.1);
    }

    @Test
    void shouldApplyMinimumOneHourCharge() {
        booking.setStartTime(LocalDateTime.now());
        booking.setEndTime(LocalDateTime.now().plusMinutes(30));
        booking.setPricePerHour(50.0);

        BookingResponseDTO dto = mapper.toDTO(booking);

        assertEquals(50.0, dto.getEstimatedAmount(), 0.1);
    }

    @Test
    void shouldReturnZeroEstimateWhenTimesNull() {
        booking.setStartTime(null);
        booking.setEndTime(null);

        BookingResponseDTO dto = mapper.toDTO(booking);

        assertEquals(0.0, dto.getEstimatedAmount());
    }

    @Test
    void shouldMapCheckInAndCheckOutTimes() {
        LocalDateTime checkIn = LocalDateTime.now().minusHours(2);
        LocalDateTime checkOut = LocalDateTime.now();
        booking.setCheckInTime(checkIn);
        booking.setCheckOutTime(checkOut);

        BookingResponseDTO dto = mapper.toDTO(booking);

        assertEquals(checkIn, dto.getCheckInTime());
        assertEquals(checkOut, dto.getCheckOutTime());
    }

    @Test
    void shouldMapCreatedAt() {
        BookingResponseDTO dto = mapper.toDTO(booking);
        assertEquals(booking.getCreatedAt(), dto.getCreatedAt());
    }
}
