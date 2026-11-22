package com.taskflow.dto.analytics;

import java.util.List;

public record SprintVelocityResponse(
        AnalyticsMeta meta,
        List<SprintVelocityPoint> points,
        MetricValue averageVelocity
) {

    public record SprintVelocityPoint(
            Long sprintId,
            String sprintName,
            MetricValue committed,
            MetricValue completed,
            MetricValue carriedOver,
            MetricValue completionPercentage
    ) {
    }
}
