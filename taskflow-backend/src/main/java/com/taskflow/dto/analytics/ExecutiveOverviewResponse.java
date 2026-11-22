package com.taskflow.dto.analytics;

import com.taskflow.enums.AuditAction;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;

import java.util.List;
import java.util.Map;

public record ExecutiveOverviewResponse(
        AnalyticsMeta meta,
        Map<String, MetricValue> kpis,
        List<TimeBucket> trend,
        List<AttentionItem> attention,
        ProjectStatusSummary projectStatusSummary,
        TaskPriorityDistribution taskPriorityDistribution,
        TaskCreationTrend taskCreationTrend,
        EmployeeCompletionMetrics employeeCompletionMetrics,
        AuditActivityTrend auditActivityTrend,
        BugSeverityDistribution bugSeverityDistribution
) {

    /** Backwards-compatible constructor for existing callers and tests. */
    public ExecutiveOverviewResponse(AnalyticsMeta meta,
                                     Map<String, MetricValue> kpis,
                                     List<TimeBucket> trend,
                                     List<AttentionItem> attention) {
        this(meta, kpis, trend, attention,
                ProjectStatusSummary.empty(),
                TaskPriorityDistribution.empty(),
                TaskCreationTrend.empty(),
                EmployeeCompletionMetrics.empty(),
                AuditActivityTrend.empty(),
                BugSeverityDistribution.empty());
    }

    public record AttentionItem(
            String type,
            String title,
            String detail,
            String entityType,
            Long entityId
    ) {
    }

    /** Counts of projects in each status within the authorized scope. */
    public record ProjectStatusSummary(
            long total,
            long active,
            long completed,
            long other,
            Map<ProjectStatus, Long> byStatus,
            DataAvailability availability
    ) {
        public static ProjectStatusSummary empty() {
            return new ProjectStatusSummary(0, 0, 0, 0, Map.of(), DataAvailability.NO_DATA_IN_RANGE);
        }
    }

    /** Task counts per priority within the authorized scope. */
    public record TaskPriorityDistribution(
            Map<PriorityLevel, Long> counts,
            DataAvailability availability
    ) {
        public static TaskPriorityDistribution empty() {
            return new TaskPriorityDistribution(Map.of(), DataAvailability.NO_DATA_IN_RANGE);
        }
    }

    /** Task creation counts bucketed by the requested granularity. */
    public record TaskCreationTrend(
            List<TimeBucket> buckets,
            DataAvailability availability
    ) {
        public static TaskCreationTrend empty() {
            return new TaskCreationTrend(List.of(), DataAvailability.NO_DATA_IN_RANGE);
        }
    }

    /**
     * Per-employee delivery counts; no capacity or ranking is inferred.
     *
     * <p>{@code assigned}, {@code active} and {@code overdue} are current
     * snapshot values. {@code overdue} is evaluated as of the reporting
     * period's end date. {@code completed} counts distinct tasks that reached
     * DONE during the reporting period, attributed to the assignee recorded
     * on the DONE history event. Legacy rows without attribution are excluded.</p>
     */
    public record EmployeeCompletionMetrics(
            List<EmployeeCompletionRow> rows,
            DataAvailability availability
    ) {
        public static EmployeeCompletionMetrics empty() {
            return new EmployeeCompletionMetrics(List.of(), DataAvailability.NO_DATA_IN_RANGE);
        }
    }

    public record EmployeeCompletionRow(
            Long employeeId,
            String fullName,
            String role,
            long assigned,
            long active,
            long completed,
            long overdue
    ) {
    }

    /** Audit-log events bucketed by period and action type. */
    public record AuditActivityTrend(
            List<TimeBucket> buckets,
            Map<AuditAction, Long> totalsByAction,
            DataAvailability availability
    ) {
        public static AuditActivityTrend empty() {
            return new AuditActivityTrend(List.of(), Map.of(), DataAvailability.NO_DATA_IN_RANGE);
        }
    }

    /** Bug counts per severity within the authorized scope. */
    public record BugSeverityDistribution(
            Map<BugSeverity, Long> counts,
            DataAvailability availability
    ) {
        public static BugSeverityDistribution empty() {
            return new BugSeverityDistribution(Map.of(), DataAvailability.NO_DATA_IN_RANGE);
        }
    }
}
