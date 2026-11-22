package com.taskflow.util.report;

import com.taskflow.dto.analytics.DataAvailability;
import com.taskflow.dto.analytics.MetricValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MetricFormatterTest {

    @Test
    void availableValuesRenderWithoutFabrication() {
        assertThat(MetricFormatter.format(MetricValue.available(BigDecimal.valueOf(4), "count")))
                .isEqualTo("4");
        assertThat(MetricFormatter.format(MetricValue.available(BigDecimal.ZERO, "count")))
                .isEqualTo("0");
        assertThat(MetricFormatter.format(MetricValue.available(new BigDecimal("33.330"), "%")))
                .isEqualTo("33.33%");
        assertThat(MetricFormatter.format(MetricValue.available(new BigDecimal("6.00"), "days")))
                .isEqualTo("6 days");
    }

    @Test
    void nullMetricOrNullValueIsNotAvailable() {
        assertThat(MetricFormatter.format(null)).isEqualTo(MetricFormatter.UNAVAILABLE);
        assertThat(MetricFormatter.format(new MetricValue(
                null, null, null, null, DataAvailability.AVAILABLE, "count")))
                .isEqualTo(MetricFormatter.UNAVAILABLE);
    }

    @Test
    void allUnavailableStatesUseExplicitLabels() {
        assertThat(MetricFormatter.format(MetricValue.noData("count")))
                .isEqualTo("No data in range");
        assertThat(MetricFormatter.format(MetricValue.insufficientHistory("count")))
                .isEqualTo("Insufficient history");
        assertThat(MetricFormatter.format(MetricValue.notApplicable("count")))
                .isEqualTo("Not applicable");
        assertThat(MetricFormatter.format(MetricValue.notConfigured("hours")))
                .isEqualTo("Not configured");
        assertThat(MetricFormatter.availabilityLabel(null))
                .isEqualTo(MetricFormatter.UNAVAILABLE);
    }

    @Test
    void comparisonFormattingRequiresARealComparison() {
        assertThat(MetricFormatter.formatComparison(MetricValue.available(BigDecimal.ONE, "count")))
                .isEqualTo(MetricFormatter.NO_COMPARISON);
        assertThat(MetricFormatter.formatComparison(MetricValue.compared(
                BigDecimal.valueOf(2), BigDecimal.valueOf(1), "count")))
                .contains("100%", "vs previous");
    }

    @Test
    void availabilityCheckMatchesValueState() {
        assertThat(MetricFormatter.isAvailable(MetricValue.available(BigDecimal.ZERO, "count"))).isTrue();
        assertThat(MetricFormatter.isAvailable(MetricValue.noData("count"))).isFalse();
        assertThat(MetricFormatter.isAvailable(null)).isFalse();
    }
}
