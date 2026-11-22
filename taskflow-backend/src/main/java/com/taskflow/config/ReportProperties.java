package com.taskflow.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounded resource limits for in-memory report generation. */
@Validated
@ConfigurationProperties(prefix = "taskflow.reports")
public record ReportProperties(

        @Min(value = 1, message = "max-buckets-per-report must be at least 1")
        @Max(value = 500, message = "max-buckets-per-report must not exceed 500")
        int maxBucketsPerReport,

        @Min(value = 1, message = "max-rows-per-sheet must be at least 1")
        @Max(value = 100000, message = "max-rows-per-sheet must not exceed 100000")
        int maxRowsPerSheet,

        @Min(value = 1, message = "max-kpis-per-report must be at least 1")
        @Max(value = 200, message = "max-kpis-per-report must not exceed 200")
        int maxKpisPerReport,

        @Min(value = 1, message = "max-pdf-pages must be at least 1")
        @Max(value = 200, message = "max-pdf-pages must not exceed 200")
        int maxPdfPages
) {
}
