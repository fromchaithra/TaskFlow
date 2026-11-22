package com.taskflow.dto.report.model;

import java.util.List;

/** A named series; value order is the original time-bucket/row order. */
public record ChartSeries(String name, List<ChartValue> values) {
}
