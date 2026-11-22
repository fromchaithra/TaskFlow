package com.taskflow.dto.analytics;

import java.util.List;

public record TaskCompletionTrendResponse(
        AnalyticsMeta meta,
        List<TimeBucket> buckets,
        MetricValue totalCompleted
) {
}
