package com.taskflow.dto.report;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsQuery;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsReportRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static AnalyticsQuery validQuery() {
        return new AnalyticsQuery(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of());
    }

    @Test
    void validRequestHasNoViolations() {
        AnalyticsReportRequest request = new AnalyticsReportRequest(
                ReportType.EXECUTIVE_OVERVIEW, ReportFormat.PDF, validQuery(), null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void nullTypeFormatOrQueryAreRejected() {
        assertThat(validator.validate(new AnalyticsReportRequest(
                null, ReportFormat.PDF, validQuery(), null)))
                .extracting(v -> v.getPropertyPath().toString()).contains("type");
        assertThat(validator.validate(new AnalyticsReportRequest(
                ReportType.EXECUTIVE_OVERVIEW, null, validQuery(), null)))
                .extracting(v -> v.getPropertyPath().toString()).contains("format");
        assertThat(validator.validate(new AnalyticsReportRequest(
                ReportType.EXECUTIVE_OVERVIEW, ReportFormat.PDF, null, null)))
                .extracting(v -> v.getPropertyPath().toString()).contains("query");
    }

    @Test
    void nestedAnalyticsQueryValidationIsApplied() {
        AnalyticsQuery inverted = new AnalyticsQuery(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 1),
                null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of());
        AnalyticsReportRequest request = new AnalyticsReportRequest(
                ReportType.EXECUTIVE_OVERVIEW, ReportFormat.XLSX, inverted, null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void sprintBurndownRequiresPositiveSprintId() {
        AnalyticsReportRequest missing = new AnalyticsReportRequest(
                ReportType.SPRINT_BURNOWN, ReportFormat.PDF, validQuery(), null);
        assertThat(validator.validate(missing)).isNotEmpty();

        AnalyticsReportRequest negative = new AnalyticsReportRequest(
                ReportType.SPRINT_BURNOWN, ReportFormat.PDF, validQuery(), -1L);
        assertThat(validator.validate(negative)).isNotEmpty();

        AnalyticsReportRequest valid = new AnalyticsReportRequest(
                ReportType.SPRINT_BURNOWN, ReportFormat.PDF, validQuery(), 42L);
        assertThat(validator.validate(valid)).isEmpty();
    }

    @Test
    void nonPositiveSprintIdIsRejectedForOtherReportsToo() {
        AnalyticsReportRequest request = new AnalyticsReportRequest(
                ReportType.PROJECT_PERFORMANCE, ReportFormat.PDF, validQuery(), 0L);

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString()).contains("sprintId");
    }
}
