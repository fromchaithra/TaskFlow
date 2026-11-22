package com.taskflow.dto.analytics.projections;

import java.time.Instant;

/** Sprint task membership needed for velocity and burndown reconstruction. */
public record SprintTaskAnalyticsProjection(
        Long sprintId,
        Long taskId,
        Instant addedAt,
        Instant removedAt,
        String statusAtSprintEnd,
        String currentStatus,
        Long assigneeId
) {
}
