package com.taskflow.dto.analytics;

import java.time.LocalDate;
import java.util.Map;

/** A weekly or monthly bucket of analytics series. */
public record TimeBucket(
        LocalDate start,
        LocalDate end,
        Map<String, MetricValue> series
) {
}
