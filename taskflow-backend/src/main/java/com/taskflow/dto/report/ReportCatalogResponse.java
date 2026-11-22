package com.taskflow.dto.report;

import java.util.List;

/** Catalog of report types and formats, derived from the existing enums. */
public record ReportCatalogResponse(
        List<ReportTypeEntry> reportTypes,
        List<ReportFormatEntry> formats
) {

    public record ReportTypeEntry(
            String id,
            String label,
            List<String> formats,
            boolean requiresSprintId
    ) {
    }

    public record ReportFormatEntry(
            String id,
            String label,
            String contentType,
            String extension
    ) {
    }
}
