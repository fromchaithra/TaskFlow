package com.taskflow.dto.analytics.projections;

/** Bug counts grouped by an owner key and bug status name. */
public record BugStatusCountProjection(Long key, String status, Long count) {
}
