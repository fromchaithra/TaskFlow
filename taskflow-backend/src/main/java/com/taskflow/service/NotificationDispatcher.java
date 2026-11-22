package com.taskflow.service;

import com.taskflow.entity.User;
import com.taskflow.enums.NotificationType;

public interface NotificationDispatcher {

    void notifyUser(
            User recipient,
            NotificationType type,
            String title,
            String message,
            String entityType,
            Long entityId
    );
}
