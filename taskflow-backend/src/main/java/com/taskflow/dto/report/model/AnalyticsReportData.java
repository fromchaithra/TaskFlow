package com.taskflow.dto.report.model;

import com.taskflow.dto.report.ReportMetadata;
import com.taskflow.dto.report.ReportType;

import java.util.List;

/** Renderer-neutral model produced from an authorized analytics response. */
public record AnalyticsReportData(
        ReportMetadata metadata,
        ReportType reportType,
        List<ReportKpi> summaryKpis,
        List<ReportSection> sections,
        List<String> availabilityNotes,
        boolean emptyDataset
) {
}
