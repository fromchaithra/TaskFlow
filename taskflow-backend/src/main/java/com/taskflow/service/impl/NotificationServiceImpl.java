package com.taskflow.service.impl;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.notification.NotificationResponse;
import com.taskflow.entity.Notification;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.service.NotificationService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> listNotifications(Long recipientId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(recipientId, pageable)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        return PagedResponse.from(page.map(n -> new NotificationResponse(
                n.getId(), n.getType().name(), n.getTitle(), n.getMessage(),
                n.getEntityType(), n.getEntityId(), n.isRead(), n.getReadAt(), n.getCreatedAt()
        )));
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long notificationId, Long recipientId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Notification not found: " + notificationId));
        if (!n.getRecipient().getId().equals(recipientId)) {
            throw new com.taskflow.exception.UnauthorizedResourceAccessException("Not your notification");
        }
        n.markAsRead();
        notificationRepository.save(n);
        return new NotificationResponse(n.getId(), n.getType().name(), n.getTitle(), n.getMessage(),
                n.getEntityType(), n.getEntityId(), n.isRead(), n.getReadAt(), n.getCreatedAt());
    }

    @Override
    @Transactional
    public int markAllRead(Long recipientId) {
        return notificationRepository.markAllAsRead(recipientId, java.time.Instant.now());
    }
}
