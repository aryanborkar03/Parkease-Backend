package com.parkease.analytics.service;

import com.parkease.analytics.client.BookingServiceClient;
import com.parkease.analytics.client.SpotServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OccupancyLogSchedulerTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private SpotServiceClient spotServiceClient;

    @Mock
    private BookingServiceClient bookingServiceClient;

    @InjectMocks
    private OccupancyLogScheduler scheduler;

    @Test
    void shouldLogAllLotOccupanciesSuccessfully() {
        List<Map<String, Object>> bookings = List.of(
                Map.of("lotId", 1),
                Map.of("lotId", 2)
        );

        List<Map<String, Object>> spotsLot1 = List.of(
                Map.of("spotId", 1),
                Map.of("spotId", 2),
                Map.of("spotId", 3)
        );

        List<Map<String, Object>> spotsLot2 = List.of(
                Map.of("spotId", 1),
                Map.of("spotId", 2)
        );

        when(bookingServiceClient.getDistinctLotIds()).thenReturn(List.of(1L, 2L));
        when(spotServiceClient.getAvailableSpotCount(1L)).thenReturn(1);
        when(spotServiceClient.getSpotsByLot(1L)).thenReturn(spotsLot1);
        when(spotServiceClient.getAvailableSpotCount(2L)).thenReturn(1);
        when(spotServiceClient.getSpotsByLot(2L)).thenReturn(spotsLot2);

        scheduler.logAllLotOccupancies();

        verify(analyticsService).logOccupancy(1L, 2, 3);
        verify(analyticsService).logOccupancy(2L, 1, 2);
    }

    @Test
    void shouldDoNothingWhenNoActiveLotsFound() {
        when(bookingServiceClient.getDistinctLotIds()).thenReturn(List.of());

        scheduler.logAllLotOccupancies();

        verify(analyticsService, never()).logOccupancy(anyLong(), anyInt(), anyInt());
    }

    @Test
    void shouldContinueWhenOneLotFails() {
        when(bookingServiceClient.getDistinctLotIds()).thenReturn(List.of(1L, 2L));
        when(spotServiceClient.getAvailableSpotCount(1L)).thenThrow(new RuntimeException("spot service down"));
        when(spotServiceClient.getAvailableSpotCount(2L)).thenReturn(1);
        when(spotServiceClient.getSpotsByLot(2L)).thenReturn(List.of(
                Map.of("spotId", 1),
                Map.of("spotId", 2)
        ));

        scheduler.logAllLotOccupancies();

        verify(analyticsService).logOccupancy(2L, 1, 2);
    }

    @Test
    void shouldHandleBookingServiceFailureGracefully() {
        when(bookingServiceClient.getDistinctLotIds()).thenThrow(new RuntimeException("booking service down"));

        scheduler.logAllLotOccupancies();

        verify(analyticsService, never()).logOccupancy(anyLong(), anyInt(), anyInt());
    }
}
