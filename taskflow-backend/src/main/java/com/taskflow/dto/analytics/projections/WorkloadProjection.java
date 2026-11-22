package com.taskflow.dto.analytics.projections;

import java.math.BigDecimal;

/** Current workload grouped by assignee and task status name. */
public record WorkloadProjection(Long userId, String status, Long taskCount, BigDecimal estimatedHours) {
}
