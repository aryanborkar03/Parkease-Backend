package com.parkease.booking.dto;

import com.parkease.booking.dto.request.CreateBookingRequest;
import com.parkease.booking.dto.request.ExtendBookingRequest;
import com.parkease.booking.dto.response.ApiResponse;
import com.parkease.booking.dto.response.BookingResponseDTO;
import com.parkease.booking.dto.response.DriveInSpotDTO;
import com.parkease.booking.dto.response.ManagerDashboardDTO;
import com.parkease.booking.entity.Booking;
import com.parkease.booking.entity.BookingStatus;
import com.parkease.booking.entity.BookingType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void testCreateBookingRequest() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setLotId(1L);
        req.setSpotId(2L);
        req.setVehiclePlate("XYZ");
        req.setStartTime(LocalDateTime.now());
        req.setEndTime(LocalDateTime.now().plusHours(1));

        assertEquals(1L, req.getLotId());
        assertEquals(2L, req.getSpotId());
        assertEquals("XYZ", req.getVehiclePlate());
        assertNotNull(req.getStartTime());
        assertNotNull(req.getEndTime());
    }

    @Test
    void testExtendBookingRequest() {
        ExtendBookingRequest req = new ExtendBookingRequest();
        req.setNewEndTime(LocalDateTime.now());
        assertNotNull(req.getNewEndTime());
    }

    @Test
    void testApiResponse() {
        ApiResponse res = ApiResponse.ok("msg");
        assertTrue(res.isSuccess());
        assertEquals("msg", res.getMessage());

        ApiResponse fail = ApiResponse.fail("err");
        assertFalse(fail.isSuccess());
        assertEquals("err", fail.getMessage());
    }

    @Test
    void testBookingResponseDTO() {
        BookingResponseDTO dto = BookingResponseDTO.builder()
                .bookingId(1L)
                .lotId(2L)
                .spotId(3L)
                .driverEmail("e")
                .vehiclePlate("v")
                .status(BookingStatus.ACTIVE)
                .bookingType(BookingType.PRE_BOOKING)
                .totalAmount(100.0)
                .build();

        assertEquals(1L, dto.getBookingId());
        assertEquals(2L, dto.getLotId());
        assertEquals(3L, dto.getSpotId());
        assertEquals("e", dto.getDriverEmail());
        assertEquals("v", dto.getVehiclePlate());
        assertEquals(BookingStatus.ACTIVE, dto.getStatus());
        assertEquals(BookingType.PRE_BOOKING, dto.getBookingType());
        assertEquals(100.0, dto.getTotalAmount());
    }

    @Test
    void testBookingEntity() {
        Booking b = Booking.builder()
                .bookingId(1L)
                .lotId(2L)
                .spotId(3L)
                .driverEmail("e")
                .vehiclePlate("v")
                .status(BookingStatus.ACTIVE)
                .bookingType(BookingType.PRE_BOOKING)
                .totalAmount(100.0)
                .build();

        b.setBookingId(10L);
        assertEquals(10L, b.getBookingId());
    }

    @Test
    void testDriveInSpotDTO() {
        DriveInSpotDTO dto = DriveInSpotDTO.builder()
                .spotId(1L)
                .spotNumber("A1")
                .status("AVAILABLE")
                .build();
        assertEquals(1L, dto.getSpotId());
        assertEquals("A1", dto.getSpotNumber());
        assertEquals("AVAILABLE", dto.getStatus());
    }

    @Test
    void testManagerDashboardDTO() {
        ManagerDashboardDTO dto = ManagerDashboardDTO.builder()
                .lotId(10L)
                .totalActive(5)
                .totalUpcoming(10)
                .activeBookings(List.of())
                .upcomingBookings(List.of())
                .build();

        assertEquals(10L, dto.getLotId());
        assertEquals(5, dto.getTotalActive());
        assertEquals(10, dto.getTotalUpcoming());
        assertTrue(dto.getActiveBookings().isEmpty());
        assertTrue(dto.getUpcomingBookings().isEmpty());
    }

    @Test
    void testLombokGeneratedMethods() {
        Booking b1 = Booking.builder().bookingId(1L).build();
        Booking b2 = Booking.builder().bookingId(1L).build();
        assertEquals(b1, b2);
        assertEquals(b1.hashCode(), b2.hashCode());
        assertNotNull(b1.toString());

        BookingResponseDTO br1 = BookingResponseDTO.builder().bookingId(1L).build();
        BookingResponseDTO br2 = BookingResponseDTO.builder().bookingId(1L).build();
        assertEquals(br1, br2);
        assertEquals(br1.hashCode(), br2.hashCode());
        assertNotNull(br1.toString());

        DriveInSpotDTO di1 = DriveInSpotDTO.builder().spotId(1L).build();
        DriveInSpotDTO di2 = DriveInSpotDTO.builder().spotId(1L).build();
        assertEquals(di1, di2);
        assertEquals(di1.hashCode(), di2.hashCode());
        assertNotNull(di1.toString());

        CreateBookingRequest cb1 = new CreateBookingRequest();
        cb1.setSpotId(1L);
        CreateBookingRequest cb2 = new CreateBookingRequest();
        cb2.setSpotId(1L);
        assertEquals(cb1, cb2);
        assertEquals(cb1.hashCode(), cb2.hashCode());
        assertNotNull(cb1.toString());

        ManagerDashboardDTO md1 = ManagerDashboardDTO.builder().lotId(1L).build();
        ManagerDashboardDTO md2 = ManagerDashboardDTO.builder().lotId(1L).build();
        assertEquals(md1, md2);
        assertEquals(md1.hashCode(), md2.hashCode());
        assertNotNull(md1.toString());

        ExtendBookingRequest eb1 = new ExtendBookingRequest();
        ExtendBookingRequest eb2 = new ExtendBookingRequest();
        assertEquals(eb1, eb2);
        assertEquals(eb1.hashCode(), eb2.hashCode());
        assertNotNull(eb1.toString());

        ApiResponse ar1 = ApiResponse.builder().success(true).build();
        ApiResponse ar2 = ApiResponse.builder().success(true).build();
        assertEquals(ar1, ar2);
        assertEquals(ar1.hashCode(), ar2.hashCode());
        assertNotNull(ar1.toString());

        com.parkease.booking.messaging.NotificationEvent ne1 = com.parkease.booking.messaging.NotificationEvent.builder()
                .recipientEmail("e")
                .type("t")
                .title("t")
                .message("m")
                .relatedId(1L)
                .relatedType("rt")
                .build();
        com.parkease.booking.messaging.NotificationEvent ne2 = com.parkease.booking.messaging.NotificationEvent.builder()
                .recipientEmail("e")
                .type("t")
                .title("t")
                .message("m")
                .relatedId(1L)
                .relatedType("rt")
                .build();
        assertEquals(ne1, ne2);
        assertEquals(ne1.hashCode(), ne2.hashCode());
        assertNotNull(ne1.toString());
        assertEquals("e", ne1.getRecipientEmail());
    }
}
