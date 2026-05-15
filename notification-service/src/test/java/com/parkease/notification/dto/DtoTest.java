package com.parkease.notification.dto;

import com.parkease.notification.dto.request.BroadcastRequest;
import com.parkease.notification.dto.request.SendNotificationRequest;
import com.parkease.notification.dto.response.ApiResponse;
import com.parkease.notification.dto.response.NotificationResponseDTO;
import com.parkease.notification.entity.Notification;
import com.parkease.notification.entity.NotificationChannel;
import com.parkease.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void testSendNotificationRequest() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientEmail("a@b.com");
        req.setType(NotificationType.BOOKING_CONFIRMED);
        req.setChannel(NotificationChannel.EMAIL);
        req.setTitle("T");
        req.setMessage("M");
        req.setRelatedId(1L);
        req.setRelatedType("R");

        assertEquals("a@b.com", req.getRecipientEmail());
        assertEquals(NotificationType.BOOKING_CONFIRMED, req.getType());
        assertEquals(NotificationChannel.EMAIL, req.getChannel());
        assertEquals("T", req.getTitle());
        assertEquals("M", req.getMessage());
        assertEquals(1L, req.getRelatedId());
        assertEquals("R", req.getRelatedType());
    }

    @Test
    void testBroadcastRequest() {
        BroadcastRequest req = new BroadcastRequest();
        req.setTitle("T");
        req.setMessage("M");
        req.setTargetRole("R");

        assertEquals("T", req.getTitle());
        assertEquals("M", req.getMessage());
        assertEquals("R", req.getTargetRole());
    }

    @Test
    void testApiResponse() {
        ApiResponse res = ApiResponse.ok("msg");
        assertTrue(res.isSuccess());
        assertEquals("msg", res.getMessage());

        ApiResponse fail = ApiResponse.fail("err");
        assertFalse(fail.isSuccess());
        assertEquals("err", fail.getMessage());
        
        ApiResponse built = ApiResponse.builder()
            .success(true)
            .message("m")
            .build();
        assertTrue(built.isSuccess());
        assertEquals("m", built.getMessage());
    }

    @Test
    void testNotificationResponseDTO() {
        LocalDateTime now = LocalDateTime.now();
        NotificationResponseDTO dto = NotificationResponseDTO.builder()
                .notificationId(1L)
                .recipientEmail("e")
                .type(NotificationType.BROADCAST)
                .channel(NotificationChannel.IN_APP)
                .title("t")
                .message("m")
                .relatedId(2L)
                .relatedType("r")
                .isRead(true)
                .sentAt(now)
                .readAt(now)
                .build();
        
        assertEquals(1L, dto.getNotificationId());
        assertEquals("e", dto.getRecipientEmail());
        assertEquals(NotificationType.BROADCAST, dto.getType());
        assertEquals(NotificationChannel.IN_APP, dto.getChannel());
        assertEquals("t", dto.getTitle());
        assertEquals("m", dto.getMessage());
        assertEquals(2L, dto.getRelatedId());
        assertEquals("r", dto.getRelatedType());
        assertTrue(dto.isRead());
        assertEquals(now, dto.getSentAt());
        assertEquals(now, dto.getReadAt());
    }

    @Test
    void testNotificationEntity() {
        LocalDateTime now = LocalDateTime.now();
        Notification n = Notification.builder()
                .notificationId(1L)
                .recipientEmail("e")
                .type(NotificationType.BROADCAST)
                .channel(NotificationChannel.IN_APP)
                .title("t")
                .message("m")
                .relatedId(2L)
                .relatedType("r")
                .isRead(true)
                .sentAt(now)
                .readAt(now)
                .build();
        
        n.setNotificationId(2L);
        n.setRecipientEmail("e2");
        n.setType(NotificationType.PAYMENT);
        n.setChannel(NotificationChannel.EMAIL);
        n.setTitle("t2");
        n.setMessage("m2");
        n.setRelatedId(3L);
        n.setRelatedType("r2");
        n.setRead(false);
        n.setSentAt(now.minusDays(1));
        n.setReadAt(now.minusDays(1));

        assertEquals(2L, n.getNotificationId());
        assertEquals("e2", n.getRecipientEmail());
        assertEquals(NotificationType.PAYMENT, n.getType());
        assertEquals(NotificationChannel.EMAIL, n.getChannel());
        assertEquals("t2", n.getTitle());
        assertEquals("m2", n.getMessage());
        assertEquals(3L, n.getRelatedId());
        assertEquals("r2", n.getRelatedType());
        assertFalse(n.isRead());
        assertNotNull(n.getSentAt());
        assertNotNull(n.getReadAt());
    }
}
