package com.taskflow.dto.analytics.projections;

/** Task counts grouped by an owner key and task status name. */
public record TaskStatusCountProjection(Long key, String status, Long count) {
}
