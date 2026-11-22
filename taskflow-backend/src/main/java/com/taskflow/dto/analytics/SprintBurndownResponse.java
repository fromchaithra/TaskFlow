package com.taskflow.dto.analytics;

import java.time.LocalDate;
import java.util.List;

public record SprintBurndownResponse(
        AnalyticsMeta meta,
        List<BurndownPoint> points,
        DataAvailability actualAvailability
) {

    public record BurndownPoint(
            LocalDate date,
            MetricValue idealRemaining,
            MetricValue actualRemaining
    ) {
    }
}
