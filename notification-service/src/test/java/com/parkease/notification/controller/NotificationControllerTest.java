package com.parkease.notification.controller;

import com.parkease.notification.dto.request.BroadcastRequest;
import com.parkease.notification.dto.request.SendNotificationRequest;
import com.parkease.notification.dto.response.ApiResponse;
import com.parkease.notification.dto.response.NotificationResponseDTO;
import com.parkease.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService service;

    @InjectMocks
    private NotificationController controller;

    @Mock
    private Authentication auth;

    @Test
    void send() {
        SendNotificationRequest req = new SendNotificationRequest();
        NotificationResponseDTO dto = new NotificationResponseDTO();
        when(service.send(req)).thenReturn(dto);

        ResponseEntity<NotificationResponseDTO> res = controller.send(req);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertEquals(dto, res.getBody());
    }

    @Test
    void getMyNotifications() {
        when(auth.getPrincipal()).thenReturn("test@test.com");
        when(service.getMyNotifications("test@test.com")).thenReturn(List.of(new NotificationResponseDTO()));

        ResponseEntity<List<NotificationResponseDTO>> res = controller.getMyNotifications(auth);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());
    }

    @Test
    void getUnread() {
        when(auth.getPrincipal()).thenReturn("test@test.com");
        when(service.getUnread("test@test.com")).thenReturn(List.of(new NotificationResponseDTO()));

        ResponseEntity<List<NotificationResponseDTO>> res = controller.getUnread(auth);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());
    }

    @Test
    void getUnreadCount() {
        when(auth.getPrincipal()).thenReturn("test@test.com");
        when(service.getUnreadCount("test@test.com")).thenReturn(5);

        ResponseEntity<Map<String, Integer>> res = controller.getUnreadCount(auth);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(5, res.getBody().get("unreadCount"));
    }

    @Test
    void markAsRead() {
        when(auth.getPrincipal()).thenReturn("test@test.com");
        NotificationResponseDTO dto = new NotificationResponseDTO();
        when(service.markAsRead(1L, "test@test.com")).thenReturn(dto);

        ResponseEntity<NotificationResponseDTO> res = controller.markAsRead(1L, auth);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(dto, res.getBody());
    }

    @Test
    void markAllAsRead() {
        when(auth.getPrincipal()).thenReturn("test@test.com");
        when(service.markAllAsRead("test@test.com")).thenReturn(3);

        ResponseEntity<ApiResponse> res = controller.markAllAsRead(auth);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().isSuccess());
    }

    @Test
    void delete() {
        when(auth.getPrincipal()).thenReturn("test@test.com");
        doNothing().when(service).deleteNotification(1L, "test@test.com");

        ResponseEntity<ApiResponse> res = controller.delete(1L, auth);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().isSuccess());
    }

    @Test
    void broadcast() {
        BroadcastRequest req = new BroadcastRequest();
        List<String> emails = List.of("test@test.com");
        when(service.broadcast(req, emails)).thenReturn(1);

        ResponseEntity<ApiResponse> res = controller.broadcast(req, emails);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().isSuccess());
    }
}
