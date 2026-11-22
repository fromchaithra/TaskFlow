package com.taskflow.dto.live;

import com.taskflow.dto.analytics.MetricValue;
import com.taskflow.dto.analytics.TimeBucket;
import com.taskflow.dto.activity.ActivityResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The only payload sent to live clients. Contains no internal event metadata.
 *
 * <p>Empty lists and unavailable metric values represent "no data" and are
 * never substituted with fabricated zeros.</p>
 */
public record LiveAnalyticsSnapshot(
        Instant generatedAt,
        long revision,
        Map<String, MetricValue> taskKpis,
        Map<String, Long> taskStatusDistribution,
        List<ActivityResponse> recentActivity,
        List<TimeBucket> completionTrend,
        MetricValue tasksCompletedInRange
) {
}
