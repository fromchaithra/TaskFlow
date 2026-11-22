package com.taskflow.dto.analytics;

import java.util.List;
import java.util.Map;

public record QaAnalyticsResponse(
        AnalyticsMeta meta,
        Map<String, MetricValue> results,
        MetricValue passRate,
        MetricValue defectLinkRate,
        List<QaRunRow> runs
) {

    public record QaRunRow(
            Long runId,
            String runName,
            String status,
            MetricValue total,
            MetricValue passed,
            MetricValue failed,
            MetricValue blocked,
            MetricValue notRun
    ) {
    }
}
