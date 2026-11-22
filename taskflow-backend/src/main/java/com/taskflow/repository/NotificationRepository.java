package com.taskflow.repository;

import com.taskflow.entity.Notification;
import com.taskflow.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    boolean existsByRecipientIdAndTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
            Long recipientId,
            NotificationType type,
            String entityType,
            Long entityId,
            Instant since
    );

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.recipient.id = :recipientId AND n.read = false")
    int markAllAsRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);
}
