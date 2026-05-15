package com.parkease.notification.service;

import com.parkease.notification.dto.request.BroadcastRequest;
import com.parkease.notification.dto.request.SendNotificationRequest;
import com.parkease.notification.dto.response.NotificationResponseDTO;
import com.parkease.notification.entity.Notification;
import com.parkease.notification.entity.NotificationChannel;
import com.parkease.notification.entity.NotificationType;
import com.parkease.notification.exception.ResourceNotFoundException;
import com.parkease.notification.mapper.NotificationMapper;
import com.parkease.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository repo;
    @Mock private NotificationMapper mapper;
    @Mock private EmailService emailService;

    @InjectMocks
    private NotificationServiceImpl service;

    private Notification sampleNotification;
    private NotificationResponseDTO sampleDTO;

    @BeforeEach
    void setUp() {
        sampleNotification = Notification.builder()
                .notificationId(1L)
                .recipientEmail("aryan@test.com")
                .type(NotificationType.BOOKING_CONFIRMED)
                .channel(NotificationChannel.IN_APP)
                .title("Booking Confirmed")
                .message("Your booking is confirmed")
                .isRead(false)
                .build();

        sampleDTO = NotificationResponseDTO.builder()
                .notificationId(1L)
                .recipientEmail("aryan@test.com")
                .type(NotificationType.BOOKING_CONFIRMED)
                .channel(NotificationChannel.IN_APP)
                .title("Booking Confirmed")
                .message("Your booking is confirmed")
                .isRead(false)
                .build();
    }

    @Test
    void shouldSendInAppNotification() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientEmail("aryan@test.com");
        req.setType(NotificationType.BOOKING_CONFIRMED);
        req.setChannel(NotificationChannel.IN_APP);
        req.setTitle("Booking Confirmed");
        req.setMessage("Your booking is confirmed");

        when(repo.save(any(Notification.class))).thenReturn(sampleNotification);
        when(mapper.toDTO(sampleNotification)).thenReturn(sampleDTO);

        NotificationResponseDTO result = service.send(req);

        assertNotNull(result);
        assertEquals("aryan@test.com", result.getRecipientEmail());
        verify(repo).save(any(Notification.class));
        verify(emailService, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void shouldSendEmailNotification() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientEmail("aryan@test.com");
        req.setType(NotificationType.BOOKING_CONFIRMED);
        req.setChannel(NotificationChannel.EMAIL);
        req.setTitle("Booking Confirmed");
        req.setMessage("Your booking is confirmed");

        doNothing().when(emailService).sendEmail(any(), any(), any(), any());

        NotificationResponseDTO result = service.send(req);

        assertNotNull(result);
        verify(emailService).sendEmail("aryan@test.com", "Booking Confirmed", "Booking Confirmed", "Your booking is confirmed");
        verify(repo, never()).save(any());
    }

    @Test
    void shouldSendBothChannelNotification() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientEmail("aryan@test.com");
        req.setType(NotificationType.BOOKING_CONFIRMED);
        req.setChannel(NotificationChannel.BOTH);
        req.setTitle("Booking Confirmed");
        req.setMessage("Your booking is confirmed");

        when(repo.save(any(Notification.class))).thenReturn(sampleNotification);
        when(mapper.toDTO(sampleNotification)).thenReturn(sampleDTO);
        doNothing().when(emailService).sendEmail(any(), any(), any(), any());

        NotificationResponseDTO result = service.send(req);

        assertNotNull(result);
        verify(repo).save(any(Notification.class));
        verify(emailService).sendEmail(any(), any(), any(), any());
    }

    @Test
    void shouldBroadcastToMultipleRecipients() {
        BroadcastRequest req = new BroadcastRequest();
        req.setTitle("System Alert");
        req.setMessage("Maintenance scheduled");
        req.setTargetRole("DRIVER");

        List<String> emails = List.of("a@test.com", "b@test.com", "c@test.com");
        when(repo.saveAll(anyList())).thenReturn(List.of());

        int count = service.broadcast(req, emails);

        assertEquals(3, count);
        verify(repo).saveAll(anyList());
    }

    @Test
    void shouldReturnZeroForEmptyBroadcastList() {
        BroadcastRequest req = new BroadcastRequest();
        req.setTitle("Test");
        req.setMessage("Test");

        int count = service.broadcast(req, List.of());

        assertEquals(0, count);
        verify(repo, never()).saveAll(any());
    }

    @Test
    void shouldGetMyNotifications() {
        when(repo.findByRecipientEmailOrderBySentAtDesc("aryan@test.com"))
                .thenReturn(List.of(sampleNotification));
        when(mapper.toDTO(sampleNotification)).thenReturn(sampleDTO);

        List<NotificationResponseDTO> result = service.getMyNotifications("aryan@test.com");

        assertEquals(1, result.size());
        verify(repo).findByRecipientEmailOrderBySentAtDesc("aryan@test.com");
    }

    @Test
    void shouldGetUnreadNotifications() {
        when(repo.findByRecipientEmailAndIsReadFalseOrderBySentAtDesc("aryan@test.com"))
                .thenReturn(List.of(sampleNotification));
        when(mapper.toDTO(sampleNotification)).thenReturn(sampleDTO);

        List<NotificationResponseDTO> result = service.getUnread("aryan@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetUnreadCount() {
        when(repo.countByRecipientEmailAndIsReadFalse("aryan@test.com")).thenReturn(5);

        int count = service.getUnreadCount("aryan@test.com");

        assertEquals(5, count);
    }

    @Test
    void shouldMarkNotificationAsRead() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleNotification));
        when(repo.save(any())).thenReturn(sampleNotification);
        when(mapper.toDTO(any())).thenReturn(sampleDTO);

        NotificationResponseDTO result = service.markAsRead(1L, "aryan@test.com");

        assertNotNull(result);
        verify(repo).save(sampleNotification);
    }

    @Test
    void shouldNotSaveWhenAlreadyRead() {
        sampleNotification.setRead(true);
        when(repo.findById(1L)).thenReturn(Optional.of(sampleNotification));
        when(mapper.toDTO(sampleNotification)).thenReturn(sampleDTO);

        service.markAsRead(1L, "aryan@test.com");

        verify(repo, never()).save(any());
    }

    @Test
    void shouldThrowWhenMarkingOtherUserNotification() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleNotification));

        assertThrows(ResourceNotFoundException.class,
                () -> service.markAsRead(1L, "other@test.com"));
    }

    @Test
    void shouldMarkAllAsRead() {
        when(repo.markAllAsRead("aryan@test.com")).thenReturn(3);

        int count = service.markAllAsRead("aryan@test.com");

        assertEquals(3, count);
    }

    @Test
    void shouldDeleteNotification() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleNotification));
        doNothing().when(repo).delete(sampleNotification);

        assertDoesNotThrow(() -> service.deleteNotification(1L, "aryan@test.com"));
        verify(repo).delete(sampleNotification);
    }

    @Test
    void shouldThrowWhenDeletingOtherUserNotification() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleNotification));

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteNotification(1L, "other@test.com"));
    }

    @Test
    void shouldThrowWhenNotificationNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.markAsRead(99L, "aryan@test.com"));
    }
}
