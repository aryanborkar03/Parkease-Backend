package com.parkease.notification.mapper;

import com.parkease.notification.dto.response.NotificationResponseDTO;
import com.parkease.notification.entity.Notification;
import com.parkease.notification.entity.NotificationChannel;
import com.parkease.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationMapperTest {

    private final NotificationMapper mapper = new NotificationMapper();

    @Test
    void toDTO() {
        LocalDateTime now = LocalDateTime.now();
        Notification n = Notification.builder()
                .notificationId(1L)
                .recipientEmail("test@test.com")
                .type(NotificationType.BOOKING_CONFIRMED)
                .channel(NotificationChannel.IN_APP)
                .title("Title")
                .message("Msg")
                .relatedId(10L)
                .relatedType("BOOKING")
                .isRead(true)
                .sentAt(now)
                .readAt(now)
                .build();

        NotificationResponseDTO dto = mapper.toDTO(n);

        assertEquals(1L, dto.getNotificationId());
        assertEquals("test@test.com", dto.getRecipientEmail());
        assertEquals(NotificationType.BOOKING_CONFIRMED, dto.getType());
        assertEquals(NotificationChannel.IN_APP, dto.getChannel());
        assertEquals("Title", dto.getTitle());
        assertEquals("Msg", dto.getMessage());
        assertEquals(10L, dto.getRelatedId());
        assertEquals("BOOKING", dto.getRelatedType());
        assertEquals(true, dto.isRead());
        assertEquals(now, dto.getSentAt());
        assertEquals(now, dto.getReadAt());
    }
}
