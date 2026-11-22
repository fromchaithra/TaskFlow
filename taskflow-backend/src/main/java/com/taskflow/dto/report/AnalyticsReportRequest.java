package com.taskflow.dto.report;

import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.validation.ValidReportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Report generation request.
 *
 * <p>The analytics filters are the existing {@link AnalyticsQuery} contract;
 * this record does not duplicate it, it simply embeds and validates it.</p>
 */
@ValidReportRequest
public record AnalyticsReportRequest(

        @NotNull(message = "Report type is required")
        ReportType type,

        @NotNull(message = "Report format is required")
        ReportFormat format,

        @Valid
        @NotNull(message = "Analytics query is required")
        AnalyticsQuery query,

        @Positive(message = "Sprint id must be positive")
        Long sprintId
) {
}
