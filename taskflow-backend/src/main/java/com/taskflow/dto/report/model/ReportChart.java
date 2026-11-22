package com.taskflow.dto.report.model;

import java.util.List;

/** Chart-ready data with no rendering details. */
public record ReportChart(
        String title,
        ChartType type,
        List<ChartSeries> series,
        List<String> availabilityNotes
) {
}
