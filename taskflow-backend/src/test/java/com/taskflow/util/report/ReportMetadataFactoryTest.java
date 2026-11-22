package com.taskflow.util.report;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsMeta;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.report.ReportMetadata;
import com.taskflow.dto.report.ReportType;
import com.taskflow.entity.Organization;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportMetadataFactoryTest {

    private static final Organization ORGANIZATION = new Organization(
            "TaskFlow Enterprise", "taskflow-enterprise", "Software", "UTC", "MON-FRI");

    @Test
    void assemblesHeaderFromOrganizationAndAnalyticsMetadata() {
        AnalyticsQuery query = new AnalyticsQuery(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.MONTH,
                List.of(1L, 2L), List.of(3L), List.of(4L));
        AnalyticsMeta meta = AnalyticsMeta.of(query, "task history");

        ReportMetadata result = ReportMetadataFactory.create(
                ORGANIZATION, meta, query, ReportType.PROJECT_PERFORMANCE);

        assertThat(result.organizationName()).isEqualTo("TaskFlow Enterprise");
        assertThat(result.organizationSlug()).isEqualTo("taskflow-enterprise");
        assertThat(result.industry()).isEqualTo("Software");
        assertThat(result.timezone()).isEqualTo("UTC");
        assertThat(result.reportTitle()).isEqualTo("Project Performance");
        assertThat(result.generatedAt()).isEqualTo(meta.generatedAt());
        assertThat(result.from()).isEqualTo(query.from());
        assertThat(result.to()).isEqualTo(query.to());
        assertThat(result.granularity()).isEqualTo(AnalyticsGranularity.MONTH);
        assertThat(result.compared()).isFalse();
        assertThat(result.basis()).isEqualTo("task history");
        assertThat(result.appliedFilters()).contains(
                "Granularity: MONTH",
                "Projects: 2 selected",
                "Teams: 1 selected",
                "Employees: 1 selected");
        assertThat(result.notes()).isEmpty();
    }

    @Test
    void comparisonPeriodIsIncludedInAppliedFilters() {
        AnalyticsQuery query = new AnalyticsQuery(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31),
                AnalyticsGranularity.WEEK, List.of(), List.of(), List.of());
        AnalyticsMeta meta = AnalyticsMeta.of(query, "audit events");

        ReportMetadata result = ReportMetadataFactory.create(
                ORGANIZATION, meta, query, ReportType.BUG_TRENDS);

        assertThat(result.compared()).isTrue();
        assertThat(result.appliedFilters()).contains("Comparison: 2025-10-01 to 2025-12-31");
    }

    @Test
    void missingOrganizationIsRejectedInsteadOfInvented() {
        AnalyticsQuery query = new AnalyticsQuery(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of());
        AnalyticsMeta meta = AnalyticsMeta.of(query, "basis");

        assertThatThrownBy(() -> ReportMetadataFactory.create(null, meta, query, ReportType.QA_ANALYTICS))
                .isInstanceOf(NullPointerException.class);
    }
}
