package com.taskflow.dto.analytics;

import java.util.List;

public record BugTrendResponse(
        AnalyticsMeta meta,
        List<TimeBucket> buckets,
        MetricValue reopenRate,
        MetricValue mttrDays
) {
}
