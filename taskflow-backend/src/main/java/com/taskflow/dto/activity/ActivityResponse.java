package com.taskflow.dto.activity;

import java.time.Instant;

public record ActivityResponse(
        Long id,
        String actorName,
        String action,
        String entityType,
        Long entityId,
        String projectName,
        Instant createdAt
) {
}
