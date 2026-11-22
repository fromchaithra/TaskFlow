package com.taskflow.dto.dashboard;

import java.util.Map;

public record DashboardStatsResponse(
        String role,
        long totalEmployees,
        long activeEmployees,
        long inactiveEmployees,
        long totalProjects,
        long activeProjects,
        long completedProjects,
        long managedProjects,
        long totalTeams,
        long totalTasks,
        long overdueTasks,
        long tasksDueToday,
        long tasksReadyForTesting,
        Map<String, Long> tasksByStatus,
        long openBugs,
        long criticalBugs,
        Map<String, Long> bugsBySeverity,
        long unreadNotifications,
        long totalTestCases,
        long totalTestSuites,
        long activeTestRuns,
        long passedExecutions,
        long failedExecutions,
        long blockedExecutions
) {
}
