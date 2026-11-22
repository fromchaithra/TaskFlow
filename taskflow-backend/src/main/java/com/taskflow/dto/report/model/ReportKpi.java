package com.taskflow.dto.report.model;

import com.taskflow.dto.analytics.MetricValue;

/** A report KPI that carries the untouched analytics metric. */
public record ReportKpi(String key, String label, MetricValue metric, String description) {
}
