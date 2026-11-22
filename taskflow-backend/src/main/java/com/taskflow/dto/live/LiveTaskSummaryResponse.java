package com.taskflow.dto.live;

import com.taskflow.dto.analytics.MetricValue;

import java.util.Map;

/**
 * Live task KPIs produced by the existing analytics calculation path.
 *
 * <p>Metrics carry their original {@link MetricValue} availability so an empty
 * dataset is never presented as a measured zero.</p>
 */
public record LiveTaskSummaryResponse(
        Map<String, MetricValue> kpis,
        Map<String, Long> statusDistribution
) {
}
