package com.taskflow.service.impl;

import com.taskflow.entity.Notification;
import com.taskflow.entity.User;
import com.taskflow.enums.NotificationType;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.service.NotificationDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDispatcherImpl implements NotificationDispatcher {

    private final NotificationRepository notificationRepository;

    public NotificationDispatcherImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyUser(
            User recipient,
            NotificationType type,
            String title,
            String message,
            String entityType,
            Long entityId
    ) {
        if (recipient == null || !recipient.isActive()) {
            return;
        }
        notificationRepository.save(new Notification(recipient, type, title, message, entityType, entityId));
    }
}
