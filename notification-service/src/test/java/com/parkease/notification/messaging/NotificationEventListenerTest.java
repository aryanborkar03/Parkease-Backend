package com.parkease.notification.messaging;

import com.parkease.notification.dto.request.SendNotificationRequest;
import com.parkease.notification.entity.NotificationChannel;
import com.parkease.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void handleNotificationEvent_Success() {
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("test@test.com")
                .title("Title")
                .message("Msg")
                .relatedId(10L)
                .relatedType("BOOKING")
                .type("BOOKING_CONFIRMED")
                .channel(NotificationChannel.IN_APP)
                .build();

        when(notificationService.send(any(SendNotificationRequest.class))).thenReturn(null);

        listener.handleNotificationEvent(event);

        verify(notificationService).send(any(SendNotificationRequest.class));
    }

    @Test
    void handleNotificationEvent_UnknownTypeDefaultsToBroadcast() {
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("test@test.com")
                .type("UNKNOWN_TYPE_XYZ")
                .build();

        when(notificationService.send(any(SendNotificationRequest.class))).thenReturn(null);

        listener.handleNotificationEvent(event);

        verify(notificationService).send(any(SendNotificationRequest.class));
    }

    @Test
    void handleNotificationEvent_NullChannelDefaultsToBoth() {
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("test@test.com")
                .type("BROADCAST")
                .channel(null)
                .build();

        when(notificationService.send(any(SendNotificationRequest.class))).thenReturn(null);

        listener.handleNotificationEvent(event);

        verify(notificationService).send(any(SendNotificationRequest.class));
    }

    @Test
    void handleNotificationEvent_ThrowsException() {
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("test@test.com")
                .type("BROADCAST")
                .build();

        doThrow(new RuntimeException("Service failure")).when(notificationService).send(any());

        assertThrows(RuntimeException.class, () -> listener.handleNotificationEvent(event));
    }
}
