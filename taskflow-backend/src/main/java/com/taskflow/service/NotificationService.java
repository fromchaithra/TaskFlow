package com.taskflow.service;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.notification.NotificationResponse;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    PagedResponse<NotificationResponse> listNotifications(Long recipientId, boolean unreadOnly, Pageable pageable);
    long unreadCount(Long recipientId);
    NotificationResponse markRead(Long notificationId, Long recipientId);
    int markAllRead(Long recipientId);
}
