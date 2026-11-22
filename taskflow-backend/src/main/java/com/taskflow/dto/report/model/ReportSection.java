package com.taskflow.dto.report.model;

import java.util.List;

/** A titled section containing KPIs, tables and charts. */
public record ReportSection(
        String title,
        String description,
        List<ReportKpi> kpis,
        List<ReportTable> tables,
        List<ReportChart> charts,
        List<String> availabilityNotes
) {
}
