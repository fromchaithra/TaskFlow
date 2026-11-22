package com.taskflow.dto.report.model;

import com.taskflow.dto.analytics.MetricValue;

/** One chart value; keeps the original analytics metric and availability. */
public record ChartValue(String label, MetricValue metric) {
}
