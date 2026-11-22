package com.taskflow.dto.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A single analytics value with explicit comparison and availability metadata.
 *
 * <p>{@code value} is {@code null} when no real data exists for the requested
 * range; a literal zero is only used when data exists and the measured count
 * is actually zero.</p>
 */
public record MetricValue(
        BigDecimal value,
        BigDecimal previousValue,
        BigDecimal deltaPercent,
        String direction,
        DataAvailability availability,
        String unit
) {

    public static MetricValue available(BigDecimal value, String unit) {
        return new MetricValue(value, null, null, null, DataAvailability.AVAILABLE, unit);
    }

    public static MetricValue compared(BigDecimal value, BigDecimal previousValue, String unit) {
        if (value == null || previousValue == null) {
            return insufficientHistory(unit);
        }
        BigDecimal delta = null;
        if (previousValue.compareTo(BigDecimal.ZERO) != 0) {
            delta = value.subtract(previousValue)
                    .divide(previousValue.abs(), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        String direction = value.compareTo(previousValue) > 0 ? "UP"
                : value.compareTo(previousValue) < 0 ? "DOWN" : "FLAT";
        return new MetricValue(value, previousValue, delta, direction, DataAvailability.AVAILABLE, unit);
    }

    public static MetricValue noData(String unit) {
        return new MetricValue(null, null, null, null, DataAvailability.NO_DATA_IN_RANGE, unit);
    }

    public static MetricValue insufficientHistory(String unit) {
        return new MetricValue(null, null, null, null, DataAvailability.INSUFFICIENT_HISTORY, unit);
    }

    public static MetricValue notConfigured(String unit) {
        return new MetricValue(null, null, null, null, DataAvailability.NOT_CONFIGURED, unit);
    }

    public static MetricValue notApplicable(String unit) {
        return new MetricValue(null, null, null, null, DataAvailability.NOT_APPLICABLE, unit);
    }
}
