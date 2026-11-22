package com.taskflow.dto.analytics;

import java.util.List;

public record EmployeeWorkloadResponse(
        AnalyticsMeta meta,
        List<EmployeeWorkloadRow> rows,
        DataAvailability capacityBasis
) {

    public record EmployeeWorkloadRow(
            Long employeeId,
            String fullName,
            String role,
            MetricValue assignedTasks,
            MetricValue activeTasks,
            MetricValue estimatedHours,
            MetricValue capacityHours,
            MetricValue utilizationPercent
    ) {
    }
}
