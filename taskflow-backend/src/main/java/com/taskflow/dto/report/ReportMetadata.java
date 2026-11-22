package com.taskflow.dto.report;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.DataAvailability;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Report header and filter metadata assembled from existing domain data. */
public record ReportMetadata(
        String organizationName,
        String organizationSlug,
        String industry,
        String timezone,
        String reportTitle,
        Instant generatedAt,
        LocalDate from,
        LocalDate to,
        AnalyticsGranularity granularity,
        boolean compared,
        String basis,
        List<String> appliedFilters,
        Map<String, DataAvailability> notes
) {
}
