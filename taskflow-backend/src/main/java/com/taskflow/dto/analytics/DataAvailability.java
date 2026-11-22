package com.taskflow.dto.analytics;

/**
 * Describes the data state of an analytics metric.
 *
 * <p>Analytics endpoints must never substitute a fabricated value for missing
 * history. Metrics with no underlying records are reported as
 * {@link #NO_DATA_IN_RANGE}; metrics without a comparable previous period are
 * reported as {@link #INSUFFICIENT_HISTORY}.</p>
 */
public enum DataAvailability {
    AVAILABLE,
    NO_DATA_IN_RANGE,
    INSUFFICIENT_HISTORY,
    NOT_APPLICABLE,
    NOT_CONFIGURED
}
