package com.taskflow.dto.analytics.projections;

/** Count grouped by a string key (enum name, priority, severity, action). */
public record StringKeyCountProjection(String key, Long count) {
}
