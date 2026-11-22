package com.taskflow.service;

import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.BugTrendResponse;
import com.taskflow.dto.analytics.EmployeeWorkloadResponse;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse;
import com.taskflow.dto.analytics.ProjectGrowthResponse;
import com.taskflow.dto.analytics.ProjectPerformanceReportResponse;
import com.taskflow.dto.analytics.QaAnalyticsResponse;
import com.taskflow.dto.analytics.SprintBurndownResponse;
import com.taskflow.dto.analytics.SprintVelocityResponse;
import com.taskflow.dto.analytics.TaskCompletionTrendResponse;
import com.taskflow.dto.analytics.TeamProductivityResponse;
import com.taskflow.dto.live.LiveTaskSummaryResponse;

/**
 * Read-only analytics service contract.
 *
 * <p>Every method receives the common validated {@link AnalyticsQuery} and must
 * resolve its scope through {@link AnalyticsAuthorizationService} before any
 * database aggregation. Implementations are introduced in later groups; no
 * endpoint is exposed in the foundation group.</p>
 */
public interface AnalyticsService {

    ExecutiveOverviewResponse getExecutiveOverview(AnalyticsQuery query);

    ProjectPerformanceReportResponse getProjectPerformance(AnalyticsQuery query);

    ProjectGrowthResponse getProjectGrowth(AnalyticsQuery query);

    SprintVelocityResponse getSprintVelocity(AnalyticsQuery query);

    SprintBurndownResponse getSprintBurndown(AnalyticsQuery query, Long sprintId);

    TaskCompletionTrendResponse getTaskCompletionTrends(AnalyticsQuery query);

    TeamProductivityResponse getTeamProductivity(AnalyticsQuery query);

    EmployeeWorkloadResponse getEmployeeWorkload(AnalyticsQuery query);

    BugTrendResponse getBugTrends(AnalyticsQuery query);

    QaAnalyticsResponse getQaResults(AnalyticsQuery query);

    /**
     * Lean, org/project-scoped task summary for the live dashboard. Reuses the
     * same scope resolution, repository queries and availability rules as the
     * report analytics - it does not recompute metrics independently.
     */
    LiveTaskSummaryResponse getLiveTaskSummary(AnalyticsQuery query);
}
