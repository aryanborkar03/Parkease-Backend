package com.parkease.notification.service;

import com.parkease.notification.dto.request.BroadcastRequest;
import com.parkease.notification.dto.request.SendNotificationRequest;
import com.parkease.notification.dto.response.NotificationResponseDTO;

import java.util.List;

public interface NotificationService {

    // Notification sending operations
    NotificationResponseDTO send(SendNotificationRequest request);
    int broadcast(BroadcastRequest request, List<String> recipientEmails);

    // Notification retrieval operations
    List<NotificationResponseDTO> getMyNotifications(String email);
    List<NotificationResponseDTO> getUnread(String email);
    int getUnreadCount(String email);

    // Notification state management
    NotificationResponseDTO markAsRead(Long notificationId, String email);
    int markAllAsRead(String email);
    void deleteNotification(Long notificationId, String email);
}
