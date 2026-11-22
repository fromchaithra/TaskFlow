package com.taskflow.dto.analytics;

import java.util.List;

public record TeamProductivityResponse(
        AnalyticsMeta meta,
        List<TeamProductivityRow> rows
) {

    public record TeamProductivityRow(
            Long teamId,
            String teamName,
            long memberCount,
            MetricValue completedTasks,
            MetricValue activeTasks,
            MetricValue overdueTasks,
            MetricValue completionRate
    ) {
    }
}
