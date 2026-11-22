package com.taskflow.dto.analytics;

import com.taskflow.enums.ProjectStatus;

import java.util.List;

public record ProjectPerformanceReportResponse(
        AnalyticsMeta meta,
        List<ProjectPerformanceRow> rows,
        PortfolioSummary summary
) {

    public record ProjectPerformanceRow(
            Long projectId,
            String projectKey,
            String name,
            ProjectStatus status,
            String managerName,
            long totalTasks,
            long completedTasks,
            long overdueTasks,
            long openBugs,
            MetricValue progressPercent,
            MetricValue velocity
    ) {
    }

    public record PortfolioSummary(
            MetricValue totalProjects,
            MetricValue completionRate,
            MetricValue onTimeRate,
            MetricValue openBugs
    ) {
    }
}
