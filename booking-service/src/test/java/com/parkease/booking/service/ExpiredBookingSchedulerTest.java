package com.parkease.booking.service;

import com.parkease.booking.client.LotServiceClient;
import com.parkease.booking.client.SpotServiceClient;
import com.parkease.booking.entity.Booking;
import com.parkease.booking.entity.BookingStatus;
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

import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiredBookingSchedulerTest {

    @Mock
    private BookingRepository repo;

    @Mock
    private SpotServiceClient spotServiceClient;

    @Mock
    private LotServiceClient lotServiceClient;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private ExpiredBookingScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "graceMinutes", 15);
    }

    @Test
    void shouldDoNothingWhenNoExpiredBookingsFound() {
        when(repo.findExpiredPreBookings(any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.cancelExpiredBookings();

        verify(repo, never()).save(any());
        verify(spotServiceClient, never()).releaseSpot(anyLong());
    }

    @Test
    void shouldCancelExpiredBookingsAndReleaseResources() {
        Booking booking = Booking.builder()
                .bookingId(1L)
                .spotId(101L)
                .lotId(10L)
                .status(BookingStatus.RESERVED)
                .build();

        when(repo.findExpiredPreBookings(any(LocalDateTime.class)))
                .thenReturn(List.of(booking));
        doNothing().when(notificationPublisher).publish(any());

        scheduler.cancelExpiredBookings();

        verify(repo).save(booking);
        verify(spotServiceClient).releaseSpot(101L);
        verify(lotServiceClient).incrementAvailable(10L);
    }

    @Test
    void shouldContinueProcessingEvenIfOneBookingFails() {
        Booking booking1 = Booking.builder()
                .bookingId(1L)
                .spotId(101L)
                .lotId(10L)
                .status(BookingStatus.RESERVED)
                .build();

        Booking booking2 = Booking.builder()
                .bookingId(2L)
                .spotId(102L)
                .lotId(11L)
                .status(BookingStatus.RESERVED)
                .build();

        when(repo.findExpiredPreBookings(any(LocalDateTime.class)))
                .thenReturn(List.of(booking1, booking2));

        doThrow(new RuntimeException("spot-service down"))
                .when(spotServiceClient).releaseSpot(101L);
        doNothing().when(notificationPublisher).publish(any());

        scheduler.cancelExpiredBookings();

        verify(repo, times(2)).save(any(Booking.class));
    }

    @Test
    void shouldMarkBookingAsCancelledBeforeSaving() {
        Booking booking = Booking.builder()
                .bookingId(1L)
                .spotId(101L)
                .lotId(10L)
                .status(BookingStatus.RESERVED)
                .build();

        when(repo.findExpiredPreBookings(any(LocalDateTime.class)))
                .thenReturn(List.of(booking));
        doNothing().when(notificationPublisher).publish(any());

        scheduler.cancelExpiredBookings();

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(repo).save(booking);
    }
}
