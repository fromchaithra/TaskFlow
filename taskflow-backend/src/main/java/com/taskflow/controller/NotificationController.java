package com.taskflow.controller;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.notification.NotificationResponse;
import com.taskflow.service.NotificationService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long recipientId = SecurityUtils.currentUser().getId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(notificationService.listNotifications(recipientId, unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount() {
        Long recipientId = SecurityUtils.currentUser().getId();
        return ResponseEntity.ok(notificationService.unreadCount(recipientId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long id) {
        Long recipientId = SecurityUtils.currentUser().getId();
        return ResponseEntity.ok(notificationService.markRead(id, recipientId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Integer> markAllRead() {
        Long recipientId = SecurityUtils.currentUser().getId();
        return ResponseEntity.ok(notificationService.markAllRead(recipientId));
    }
}
