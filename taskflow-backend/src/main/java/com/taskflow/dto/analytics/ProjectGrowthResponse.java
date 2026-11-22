package com.taskflow.dto.analytics;

import java.util.List;

public record ProjectGrowthResponse(
        AnalyticsMeta meta,
        List<TimeBucket> buckets,
        MetricValue netGrowth
) {
}
