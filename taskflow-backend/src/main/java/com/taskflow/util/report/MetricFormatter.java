package com.taskflow.util.report;

import com.taskflow.dto.analytics.DataAvailability;
import com.taskflow.dto.analytics.MetricValue;

import java.math.BigDecimal;

/**
 * Renders analytics metrics for reports without ever turning missing data into
 * a fabricated zero.
 */
public final class MetricFormatter {

    public static final String UNAVAILABLE = "Not available";
    public static final String NO_COMPARISON = "No comparison available";

    private MetricFormatter() {
    }

    public static String format(MetricValue metric) {
        if (metric == null) {
            return UNAVAILABLE;
        }
        if (metric.availability() != DataAvailability.AVAILABLE) {
            return availabilityLabel(metric.availability());
        }
        if (metric.value() == null) {
            return UNAVAILABLE;
        }
        return formatValue(metric.value(), metric.unit());
    }

    public static String formatValue(BigDecimal value, String unit) {
        if (value == null) {
            return UNAVAILABLE;
        }
        String number = value.stripTrailingZeros().toPlainString();
        if (unit == null || unit.isBlank() || "count".equals(unit)) {
            return number;
        }
        if ("%".equals(unit)) {
            return number + "%";
        }
        return number + " " + unit;
    }

    public static String formatComparison(MetricValue metric) {
        if (metric == null || metric.availability() != DataAvailability.AVAILABLE
                || metric.previousValue() == null || metric.deltaPercent() == null) {
            return NO_COMPARISON;
        }
        String arrow = switch (metric.direction() == null ? "" : metric.direction()) {
            case "UP" -> "↑";
            case "DOWN" -> "↓";
            default -> "→";
        };
        return arrow + " " + formatValue(metric.deltaPercent(), "%") + " vs previous";
    }

    public static String availabilityLabel(DataAvailability availability) {
        if (availability == null) {
            return UNAVAILABLE;
        }
        return switch (availability) {
            case AVAILABLE -> "Available";
            case NO_DATA_IN_RANGE -> "No data in range";
            case INSUFFICIENT_HISTORY -> "Insufficient history";
            case NOT_APPLICABLE -> "Not applicable";
            case NOT_CONFIGURED -> "Not configured";
        };
    }

    public static boolean isAvailable(MetricValue metric) {
        return metric != null && metric.availability() == DataAvailability.AVAILABLE && metric.value() != null;
    }
}
