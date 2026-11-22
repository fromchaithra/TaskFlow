package com.taskflow.util.report;

import com.taskflow.dto.analytics.AnalyticsMeta;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.report.ReportMetadata;
import com.taskflow.dto.report.ReportType;
import com.taskflow.entity.Organization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Assembles report header metadata from existing organization/analytics data. */
public final class ReportMetadataFactory {

    private ReportMetadataFactory() {
    }

    public static ReportMetadata create(Organization organization,
                                        AnalyticsMeta meta,
                                        AnalyticsQuery query,
                                        ReportType type) {
        Objects.requireNonNull(organization, "organization");
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(type, "type");

        List<String> filters = new ArrayList<>();
        filters.add("Granularity: " + query.granularity());
        if (!query.projectIds().isEmpty()) {
            filters.add("Projects: " + query.projectIds().size() + " selected");
        }
        if (!query.teamIds().isEmpty()) {
            filters.add("Teams: " + query.teamIds().size() + " selected");
        }
        if (!query.employeeIds().isEmpty()) {
            filters.add("Employees: " + query.employeeIds().size() + " selected");
        }
        if (query.hasComparison()) {
            filters.add("Comparison: " + query.compareFrom() + " to " + query.compareTo());
        }

        return new ReportMetadata(
                organization.getName(),
                organization.getSlug(),
                organization.getIndustry(),
                organization.getTimezone(),
                type.label(),
                meta.generatedAt(),
                meta.from(),
                meta.to(),
                query.granularity(),
                meta.compared(),
                meta.basis(),
                List.copyOf(filters),
                Map.copyOf(meta.notes())
        );
    }
}
