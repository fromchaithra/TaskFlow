package com.taskflow.dto.analytics.projections;

import java.time.Instant;

/** Task completion events with both task and project identifiers. */
public record TaskCompletionProjection(Long taskId, Long projectId, Instant occurredAt) {
}
