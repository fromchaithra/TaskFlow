package com.taskflow.dto.report.model;

import java.util.List;

/** Tabular report data. Cells retain original values (Long, String, MetricValue). */
public record ReportTable(String title, List<String> headers, List<List<Object>> rows) {
}
