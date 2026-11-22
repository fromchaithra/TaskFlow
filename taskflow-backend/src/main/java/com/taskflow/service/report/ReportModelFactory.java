package com.taskflow.service.report;

import com.taskflow.dto.analytics.AnalyticsMeta;
import com.taskflow.dto.analytics.DataAvailability;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse.AuditActivityTrend;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse.BugSeverityDistribution;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse.EmployeeCompletionMetrics;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse.EmployeeCompletionRow;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse.ProjectStatusSummary;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse.TaskCreationTrend;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse.TaskPriorityDistribution;
import com.taskflow.dto.analytics.MetricValue;
import com.taskflow.dto.analytics.TimeBucket;
import com.taskflow.dto.report.ReportMetadata;
import com.taskflow.dto.report.ReportType;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.dto.report.model.ChartSeries;
import com.taskflow.dto.report.model.ChartType;
import com.taskflow.dto.report.model.ChartValue;
import com.taskflow.dto.report.model.ReportChart;
import com.taskflow.dto.report.model.ReportKpi;
import com.taskflow.dto.report.model.ReportSection;
import com.taskflow.dto.report.model.ReportTable;
import com.taskflow.util.report.MetricFormatter;

import com.taskflow.enums.AuditAction;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts existing analytics responses into renderer-neutral report sections.
 *
 * <p>No analytics are recomputed: every value, comparison and availability
 * state is carried through unchanged.</p>
 */
public final class ReportModelFactory {

    private static final String DATA_AVAILABILITY_EXPLANATION =
            "No data in range means no qualifying records were found for this metric during the selected reporting period.";

    private ReportModelFactory() {
    }

    public static AnalyticsReportData create(ReportMetadata metadata, ReportType type, Object analyticsResponse) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(analyticsResponse, "analyticsResponse");

        return switch (type) {
            case EXECUTIVE_OVERVIEW -> executive(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.ExecutiveOverviewResponse.class));
            case PROJECT_PERFORMANCE -> projectPerformance(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.ProjectPerformanceReportResponse.class));
            case PROJECT_GROWTH -> projectGrowth(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.ProjectGrowthResponse.class));
            case SPRINT_VELOCITY -> sprintVelocity(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.SprintVelocityResponse.class));
            case SPRINT_BURNOWN -> sprintBurndown(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.SprintBurndownResponse.class));
            case TASK_COMPLETION_TRENDS -> taskTrends(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.TaskCompletionTrendResponse.class));
            case TEAM_PRODUCTIVITY -> teamProductivity(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.TeamProductivityResponse.class));
            case EMPLOYEE_WORKLOAD -> employeeWorkload(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.EmployeeWorkloadResponse.class));
            case BUG_TRENDS -> bugTrends(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.BugTrendResponse.class));
            case QA_ANALYTICS -> qaAnalytics(metadata, cast(analyticsResponse,
                    com.taskflow.dto.analytics.QaAnalyticsResponse.class));
        };
    }

    // ------------------------------------------------------------------
    // Report builders
    // ------------------------------------------------------------------
    private static AnalyticsReportData executive(ReportMetadata metadata,
                                                  com.taskflow.dto.analytics.ExecutiveOverviewResponse response) {
        List<ReportKpi> summary = kpis(response.kpis());
        ReportTable attention = new ReportTable("Attention Items",
                List.of("Type", "Title", "Detail"),
                response.attention().stream().map(item -> row(
                        item.type(), item.title(), item.detail())).toList());
        ReportChart trend = chartFromBuckets("Task Completion & Bug Creation", ChartType.LINE, response.trend());

        List<ReportSection> sections = new ArrayList<>();
        sections.add(new ReportSection("Executive Overview",
                "Portfolio KPIs, attention items and activity trends",
                List.of(), List.of(attention), List.of(trend), notes(response.kpis().values())));
        sections.add(projectStatusSection(response.projectStatusSummary()));
        sections.add(taskAnalyticsSection(response.taskPriorityDistribution(), response.taskCreationTrend()));
        sections.add(teamProductivitySection(response.employeeCompletionMetrics()));
        sections.add(activityTrendsSection(response.auditActivityTrend()));
        sections.add(qualitySection(response.bugSeverityDistribution()));
        sections.add(executiveInsightsSection(response));

        boolean empty = allUnavailable(response.kpis().values())
                && response.trend().isEmpty() && response.attention().isEmpty()
                && unavailable(response.projectStatusSummary().availability())
                && unavailable(response.taskPriorityDistribution().availability())
                && unavailable(response.taskCreationTrend().availability())
                && unavailable(response.employeeCompletionMetrics().availability())
                && unavailable(response.auditActivityTrend().availability())
                && unavailable(response.bugSeverityDistribution().availability());

        AnalyticsReportData assembled = assemble(metadata, ReportType.EXECUTIVE_OVERVIEW,
                summary, sections, empty);
        List<String> availabilityNotes = new ArrayList<>();
        availabilityNotes.add(DATA_AVAILABILITY_EXPLANATION);
        availabilityNotes.addAll(assembled.availabilityNotes());
        availabilityNotes.addAll(trend.availabilityNotes());
        return new AnalyticsReportData(metadata, ReportType.EXECUTIVE_OVERVIEW,
                List.copyOf(summary), List.copyOf(sections),
                availabilityNotes.stream().distinct().toList(), empty);
    }

    private static ReportSection projectStatusSection(ProjectStatusSummary summary) {
        List<List<Object>> rows = new ArrayList<>();
        if (!unavailable(summary.availability())) {
            for (ProjectStatus status : ProjectStatus.values()) {
                Long count = summary.byStatus().get(status);
                if (count != null) {
                    rows.add(row(status.name(), count));
                }
            }
            if (rows.isEmpty()) {
                rows.add(row("TOTAL", summary.total()));
            }
        }
        return new ReportSection("Project Status",
                "Project counts by lifecycle status in the authorized scope",
                List.of(), List.of(new ReportTable("Project Status", List.of("Status", "Projects"), rows)),
                List.of(), availabilityNotes(summary.availability(), "Project status"));
    }

    private static ReportSection taskAnalyticsSection(TaskPriorityDistribution priorities,
                                                      TaskCreationTrend creation) {
        List<List<Object>> priorityRows = new ArrayList<>();
        if (!unavailable(priorities.availability())) {
            for (PriorityLevel priority : PriorityLevel.values()) {
                Long count = priorities.counts().get(priority);
                if (count != null) {
                    priorityRows.add(row(priority.name(), count));
                }
            }
        }
        List<List<Object>> creationRows = new ArrayList<>();
        if (!unavailable(creation.availability())) {
            for (TimeBucket bucket : creation.buckets()) {
                creationRows.add(row(bucket.start().toString(), bucket.end().toString(),
                        bucket.series().get("tasksCreated")));
            }
        }
        ReportChart creationChart = chartFromBuckets("Task Creation Trend", ChartType.LINE, creation.buckets());
        List<String> notes = new ArrayList<>();
        notes.addAll(availabilityNotes(priorities.availability(), "Task priority distribution"));
        notes.addAll(availabilityNotes(creation.availability(), "Task creation trend"));
        notes.addAll(creationChart.availabilityNotes());
        return new ReportSection("Task Analytics",
                "Priority mix and task creation activity for the selected period",
                List.of(),
                List.of(new ReportTable("Task Priority Distribution", List.of("Priority", "Tasks"), priorityRows),
                        new ReportTable("Task Creation Trend",
                                List.of("Period Start", "Period End", "Tasks Created"), creationRows)),
                List.of(creationChart), notes.stream().distinct().toList());
    }

    private static ReportSection teamProductivitySection(EmployeeCompletionMetrics metrics) {
        List<List<Object>> rows = new ArrayList<>();
        if (!unavailable(metrics.availability())) {
            for (EmployeeCompletionRow employee : metrics.rows()) {
                rows.add(row(employee.fullName(), employee.role(), employee.assigned(),
                        employee.active(), employee.completed(), employee.overdue()));
            }
        }
        List<String> notes = new ArrayList<>(availabilityNotes(metrics.availability(), "Employee completion metrics"));
        long totalAssigned = metrics.rows().stream().mapToLong(EmployeeCompletionRow::assigned).sum();
        if (!unavailable(metrics.availability()) && totalAssigned < 5) {
            notes.add("Small sample: employee counts are informational and no ranking is implied.");
        }
        return new ReportSection("Team Productivity",
                "Current assignments and DONE transitions attributed at completion time",
                List.of(), List.of(new ReportTable("Employee Completion",
                List.of("Employee", "Role", "Assigned", "Active", "Completed", "Overdue"), rows)),
                List.of(), notes.stream().distinct().toList());
    }

    private static ReportSection activityTrendsSection(AuditActivityTrend activity) {
        List<String> actionKeys = activity.totalsByAction().keySet().stream()
                .map(Enum::name).sorted().toList();
        List<List<Object>> rows = new ArrayList<>();
        if (!unavailable(activity.availability())) {
            for (TimeBucket bucket : activity.buckets()) {
                List<Object> cells = new ArrayList<>();
                cells.add(bucket.start().toString());
                cells.add(bucket.end().toString());
                for (String key : actionKeys) {
                    cells.add(bucket.series().get(key));
                }
                rows.add(cells);
            }
        }
        List<String> headers = new ArrayList<>(List.of("Period Start", "Period End"));
        headers.addAll(actionKeys);
        ReportChart chart = chartFromBuckets("Audit Activity Trend", ChartType.LINE, activity.buckets());
        List<String> notes = new ArrayList<>(availabilityNotes(activity.availability(), "Audit-log activity"));
        notes.addAll(chart.availabilityNotes());
        return new ReportSection("Activity Trends",
                "Project-linked delivery audit events grouped by period and action",
                List.of(), List.of(new ReportTable("Audit Activity", headers, rows)),
                List.of(chart), notes.stream().distinct().toList());
    }

    private static ReportSection qualitySection(BugSeverityDistribution severity) {
        List<List<Object>> rows = new ArrayList<>();
        if (!unavailable(severity.availability())) {
            for (BugSeverity level : BugSeverity.values()) {
                Long count = severity.counts().get(level);
                if (count != null) {
                    rows.add(row(level.name(), count));
                }
            }
            if (rows.isEmpty()) {
                rows.add(row("TOTAL", 0L));
            }
        }
        return new ReportSection("Quality Analytics",
                "Bug severity distribution for authorized projects",
                List.of(), List.of(new ReportTable("Bug Severity", List.of("Severity", "Bugs"), rows)),
                List.of(), availabilityNotes(severity.availability(), "Bug severity"));
    }

    private static ReportSection executiveInsightsSection(
            com.taskflow.dto.analytics.ExecutiveOverviewResponse response) {
        List<List<Object>> rows = new ArrayList<>();
        ProjectStatusSummary projects = response.projectStatusSummary();
        if (!unavailable(projects.availability())) {
            rows.add(row("Projects", "Total " + projects.total() + ", active " + projects.active()
                            + ", completed " + projects.completed() + ".",
                    "Review project lifecycle status for the reporting period."));
        }
        TaskPriorityDistribution priorities = response.taskPriorityDistribution();
        if (!unavailable(priorities.availability()) && !priorities.counts().isEmpty()) {
            long highPriority = priorities.counts().getOrDefault(PriorityLevel.HIGH, 0L)
                    + priorities.counts().getOrDefault(PriorityLevel.CRITICAL, 0L);
            rows.add(row("Task priority", highPriority + " task(s) at HIGH or CRITICAL priority.",
                    "Review high-priority task load before committing new scope."));
        }
        TaskCreationTrend creation = response.taskCreationTrend();
        long created = bucketTotal(creation.buckets(), "tasksCreated");
        if (!unavailable(creation.availability())) {
            rows.add(row("Task creation", created + " task(s) created in the selected period.",
                    "Use the creation trend when planning capacity."));
        }
        EmployeeCompletionMetrics employees = response.employeeCompletionMetrics();
        long completed = employees.rows().stream().mapToLong(EmployeeCompletionRow::completed).sum();
        long overdue = employees.rows().stream().mapToLong(EmployeeCompletionRow::overdue).sum();
        if (!unavailable(employees.availability())) {
            rows.add(row("Delivery", completed + " task(s) completed with attributed assignee; "
                            + overdue + " overdue as of the period end.",
                    "Review overdue items; avoid ranking employees on small samples."));
        }
        AuditActivityTrend activity = response.auditActivityTrend();
        if (!unavailable(activity.availability()) && !activity.totalsByAction().isEmpty()) {
            long events = activity.totalsByAction().values().stream().mapToLong(Long::longValue).sum();
            rows.add(row("Audit activity", events + " project-linked delivery audit event(s) recorded.",
                    "Use the activity trend to corroborate delivery changes."));
        }
        BugSeverityDistribution severity = response.bugSeverityDistribution();
        if (!unavailable(severity.availability()) && !severity.counts().isEmpty()) {
            long bugs = severity.counts().values().stream().mapToLong(Long::longValue).sum();
            rows.add(row("Quality", bugs + " bug(s) found in the authorized scope.",
                    "Review severity mix before release sign-off."));
        }
        if (rows.isEmpty()) {
            rows.add(row("Data availability",
                    "No qualifying data was found for the selected filters and period.",
                    "Widen the period or adjust filters, then regenerate the report."));
        }
        return new ReportSection("Executive Insights",
                "Measured facts from this report are listed separately from concise, conditional recommendations. "
                        + "Recommendations are suggestions, not conclusions about performance.",
                List.of(), List.of(new ReportTable("Executive Insights",
                List.of("Area", "Measured fact", "Recommendation"), rows)),
                List.of(), List.of());
    }

    private static boolean unavailable(DataAvailability availability) {
        return availability == null || availability != DataAvailability.AVAILABLE;
    }

    private static List<String> availabilityNotes(DataAvailability availability, String subject) {
        return unavailable(availability)
                ? List.of(subject + ": " + MetricFormatter.availabilityLabel(availability))
                : List.of();
    }

    private static long bucketTotal(List<TimeBucket> buckets, String key) {
        return buckets.stream()
                .map(bucket -> bucket.series().get(key))
                .filter(metric -> metric != null && MetricFormatter.isAvailable(metric))
                .mapToLong(metric -> metric.value().longValue())
                .sum();
    }

    private static AnalyticsReportData projectPerformance(ReportMetadata metadata,
                                                          com.taskflow.dto.analytics.ProjectPerformanceReportResponse response) {
        Map<String, MetricValue> summaryValues = new LinkedHashMap<>();
        summaryValues.put("totalProjects", response.summary().totalProjects());
        summaryValues.put("completionRate", response.summary().completionRate());
        summaryValues.put("onTimeRate", response.summary().onTimeRate());
        summaryValues.put("openBugs", response.summary().openBugs());

        ReportTable table = new ReportTable("Project Performance",
                List.of("Key", "Name", "Status", "Manager", "Total Tasks", "Completed", "Overdue",
                        "Open Bugs", "Progress %", "Velocity"),
                response.rows().stream().map(row -> row(
                        row.projectKey(), row.name(), row.status().name(), row.managerName(),
                        row.totalTasks(), row.completedTasks(), row.overdueTasks(), row.openBugs(),
                        row.progressPercent(), row.velocity())).toList());
        List<ChartSeries> velocitySeries = List.of(new ChartSeries("Velocity by Project",
                response.rows().stream().map(row -> new ChartValue(row.name(), row.velocity())).toList()));
        ReportChart chart = new ReportChart("Velocity by Project", ChartType.BAR, velocitySeries,
                notes(response.rows().stream().map(com.taskflow.dto.analytics.ProjectPerformanceReportResponse.ProjectPerformanceRow::velocity).toList()));
        ReportSection section = new ReportSection("Project Performance",
                "Per-project delivery, progress and open bug load",
                List.of(), List.of(table), List.of(chart), notes(summaryValues.values()));
        return assemble(metadata, ReportType.PROJECT_PERFORMANCE, kpis(summaryValues),
                List.of(section), response.rows().isEmpty());
    }

    private static AnalyticsReportData projectGrowth(ReportMetadata metadata,
                                                     com.taskflow.dto.analytics.ProjectGrowthResponse response) {
        ReportTable table = new ReportTable("Project Growth by Period",
                List.of("Period Start", "Period End", "Projects Created", "Projects Completed"),
                response.buckets().stream().map(bucket -> row(
                        bucket.start(), bucket.end(),
                        bucket.series().get("projectsCreated"), bucket.series().get("projectsCompleted"))).toList());
        ReportChart chart = chartFromBuckets("Project Growth", ChartType.LINE, response.buckets());
        ReportSection section = new ReportSection("Project Growth",
                "Projects created and completed over the selected period",
                List.of(), List.of(table), List.of(chart), notes(List.of(response.netGrowth())));
        return assemble(metadata, ReportType.PROJECT_GROWTH,
                kpis(Map.of("netGrowth", response.netGrowth())), List.of(section), response.buckets().isEmpty());
    }

    private static AnalyticsReportData sprintVelocity(ReportMetadata metadata,
                                                      com.taskflow.dto.analytics.SprintVelocityResponse response) {
        ReportTable table = new ReportTable("Sprint Velocity",
                List.of("Sprint", "Committed", "Completed", "Carried Over", "Completion %"),
                response.points().stream().map(point -> row(
                        point.sprintName(), point.committed(), point.completed(),
                        point.carriedOver(), point.completionPercentage())).toList());
        List<ChartSeries> series = List.of(
                chartSeries("Committed", response.points().stream()
                        .map(p -> new ChartValue(p.sprintName(), p.committed())).toList()),
                chartSeries("Completed", response.points().stream()
                        .map(p -> new ChartValue(p.sprintName(), p.completed())).toList()),
                chartSeries("Carried Over", response.points().stream()
                        .map(p -> new ChartValue(p.sprintName(), p.carriedOver())).toList()));
        ReportChart chart = new ReportChart("Velocity by Sprint", ChartType.BAR, series, List.of());
        ReportSection section = new ReportSection("Sprint Velocity",
                "Committed, completed and carried-over work per sprint",
                List.of(), List.of(table), List.of(chart), notes(List.of(response.averageVelocity())));
        return assemble(metadata, ReportType.SPRINT_VELOCITY,
                kpis(Map.of("averageVelocity", response.averageVelocity())),
                List.of(section), response.points().isEmpty());
    }

    private static AnalyticsReportData sprintBurndown(ReportMetadata metadata,
                                                      com.taskflow.dto.analytics.SprintBurndownResponse response) {
        ReportTable table = new ReportTable("Sprint Burndown",
                List.of("Date", "Ideal Remaining", "Actual Remaining"),
                response.points().stream().map(point -> row(
                        point.date(), point.idealRemaining(), point.actualRemaining())).toList());
        List<ChartSeries> series = List.of(
                chartSeries("Ideal", response.points().stream()
                        .map(p -> new ChartValue(p.date().toString(), p.idealRemaining())).toList()),
                chartSeries("Actual", response.points().stream()
                        .map(p -> new ChartValue(p.date().toString(), p.actualRemaining())).toList()));
        List<String> availability = new ArrayList<>();
        availability.add("Actual remaining: " + MetricFormatter.availabilityLabel(response.actualAvailability()));
        ReportChart chart = new ReportChart("Burndown", ChartType.LINE, series, availability);
        ReportSection section = new ReportSection("Sprint Burndown",
                "Ideal projection versus reconstructed remaining work",
                List.of(), List.of(table), List.of(chart), availability);
        return assemble(metadata, ReportType.SPRINT_BURNOWN, List.of(),
                List.of(section), response.points().isEmpty());
    }

    private static AnalyticsReportData taskTrends(ReportMetadata metadata,
                                                  com.taskflow.dto.analytics.TaskCompletionTrendResponse response) {
        ReportTable table = new ReportTable("Task Completion by Period",
                List.of("Period Start", "Period End", "Completed"),
                response.buckets().stream().map(bucket -> row(
                        bucket.start(), bucket.end(), bucket.series().get("tasksCompleted"))).toList());
        ReportChart chart = chartFromBuckets("Task Completion Trend", ChartType.LINE, response.buckets());
        ReportSection section = new ReportSection("Task Completion Trends",
                "DONE status transitions grouped by week or month",
                List.of(), List.of(table), List.of(chart), notes(List.of(response.totalCompleted())));
        return assemble(metadata, ReportType.TASK_COMPLETION_TRENDS,
                kpis(Map.of("totalCompleted", response.totalCompleted())),
                List.of(section), response.buckets().isEmpty());
    }

    private static AnalyticsReportData teamProductivity(ReportMetadata metadata,
                                                        com.taskflow.dto.analytics.TeamProductivityResponse response) {
        ReportTable table = new ReportTable("Team Productivity",
                List.of("Team", "Members", "Completed", "Active", "Overdue", "Completion %"),
                response.rows().stream().map(row -> row(
                        row.teamName(), row.memberCount(), row.completedTasks(),
                        row.activeTasks(), row.overdueTasks(), row.completionRate())).toList());
        List<ChartSeries> series = List.of(chartSeries("Completion Rate", response.rows().stream()
                .map(row -> new ChartValue(row.teamName(), row.completionRate())).toList()));
        ReportChart chart = new ReportChart("Completion Rate by Team", ChartType.BAR, series, List.of());
        ReportSection section = new ReportSection("Team Productivity",
                "Delivery metrics grouped by team membership",
                List.of(), List.of(table), List.of(chart), notes(response.rows().stream()
                        .flatMap(row -> List.of(row.completedTasks(), row.activeTasks(), row.overdueTasks(), row.completionRate()).stream()).toList()));
        return assemble(metadata, ReportType.TEAM_PRODUCTIVITY, List.of(),
                List.of(section), response.rows().isEmpty());
    }

    private static AnalyticsReportData employeeWorkload(ReportMetadata metadata,
                                                        com.taskflow.dto.analytics.EmployeeWorkloadResponse response) {
        ReportTable table = new ReportTable("Employee Workload",
                List.of("Employee", "Role", "Assigned", "Active", "Estimated Hours", "Capacity Hours", "Utilization %"),
                response.rows().stream().map(row -> row(
                        row.fullName(), row.role(), row.assignedTasks(), row.activeTasks(),
                        row.estimatedHours(), row.capacityHours(), row.utilizationPercent())).toList());
        List<String> availability = List.of(
                "Capacity basis: " + MetricFormatter.availabilityLabel(response.capacityBasis()));
        ReportSection section = new ReportSection("Employee Workload",
                "Current assignments and estimated effort per employee",
                List.of(), List.of(table), List.of(), availability);
        return assemble(metadata, ReportType.EMPLOYEE_WORKLOAD, List.of(),
                List.of(section), response.rows().isEmpty());
    }

    private static AnalyticsReportData bugTrends(ReportMetadata metadata,
                                                 com.taskflow.dto.analytics.BugTrendResponse response) {
        ReportTable table = new ReportTable("Bug Trends by Period",
                List.of("Period Start", "Period End", "Created", "Resolved", "Reopened"),
                response.buckets().stream().map(bucket -> row(
                        bucket.start(), bucket.end(),
                        bucket.series().get("bugsCreated"), bucket.series().get("bugsResolved"),
                        bucket.series().get("bugsReopened"))).toList());
        ReportChart chart = chartFromBuckets("Bug Lifecycle Trends", ChartType.STACKED_BAR, response.buckets());
        Map<String, MetricValue> kpiValues = new LinkedHashMap<>();
        kpiValues.put("reopenRate", response.reopenRate());
        kpiValues.put("mttrDays", response.mttrDays());
        ReportSection section = new ReportSection("Bug Trends",
                "Creation, resolution and reopening events",
                List.of(), List.of(table), List.of(chart), notes(kpiValues.values()));
        return assemble(metadata, ReportType.BUG_TRENDS, kpis(kpiValues),
                List.of(section), response.buckets().isEmpty());
    }

    private static AnalyticsReportData qaAnalytics(ReportMetadata metadata,
                                                   com.taskflow.dto.analytics.QaAnalyticsResponse response) {
        Map<String, MetricValue> kpiValues = new LinkedHashMap<>(response.results());
        kpiValues.put("passRate", response.passRate());
        kpiValues.put("defectLinkRate", response.defectLinkRate());
        ReportTable table = new ReportTable("QA Runs",
                List.of("Run", "Status", "Total", "Passed", "Failed", "Blocked", "Not Run"),
                response.runs().stream().map(run -> row(
                        run.runName(), run.status(), run.total(), run.passed(),
                        run.failed(), run.blocked(), run.notRun())).toList());
        List<ChartValue> resultValues = response.results().entrySet().stream()
                .map(entry -> new ChartValue(entry.getKey(), entry.getValue())).toList();
        ReportChart chart = new ReportChart("Execution Results", ChartType.DONUT,
                List.of(chartSeries("Results", resultValues)), notes(response.results().values()));
        ReportSection section = new ReportSection("QA Analytics",
                "Test execution outcomes and defect linkage",
                List.of(), List.of(table), List.of(chart), notes(kpiValues.values()));
        boolean empty = response.runs().isEmpty() && allUnavailable(response.results().values());
        return assemble(metadata, ReportType.QA_ANALYTICS, kpis(kpiValues), List.of(section), empty);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private static AnalyticsReportData assemble(ReportMetadata metadata, ReportType type,
                                                List<ReportKpi> summaryKpis,
                                                List<ReportSection> sections,
                                                boolean emptyDataset) {
        List<String> notes = new ArrayList<>();
        summaryKpis.stream().map(ReportKpi::metric).forEach(metric -> notes.addAll(notes(List.of(metric))));
        sections.forEach(section -> {
            notes.addAll(section.availabilityNotes());
            section.charts().forEach(chart -> notes.addAll(chart.availabilityNotes()));
        });
        return new AnalyticsReportData(metadata, type, List.copyOf(summaryKpis), List.copyOf(sections),
                List.copyOf(notes), emptyDataset);
    }

    private static List<ReportKpi> kpis(Map<String, MetricValue> values) {
        return values.entrySet().stream()
                .map(entry -> new ReportKpi(entry.getKey(), label(entry.getKey()), entry.getValue(), null))
                .toList();
    }

    private static List<String> notes(Collection<MetricValue> metrics) {
        return metrics.stream()
                .filter(metric -> metric == null || metric.value() == null
                        || metric.availability() != com.taskflow.dto.analytics.DataAvailability.AVAILABLE)
                .map(metric -> metric == null ? MetricFormatter.UNAVAILABLE
                        : MetricFormatter.availabilityLabel(metric.availability()))
                .distinct()
                .toList();
    }

    private static boolean allUnavailable(Collection<MetricValue> metrics) {
        return metrics.stream().allMatch(metric -> metric == null || metric.value() == null
                || metric.availability() != com.taskflow.dto.analytics.DataAvailability.AVAILABLE);
    }

    private static ReportChart chartFromBuckets(String title, ChartType type, List<TimeBucket> buckets) {
        List<String> keys = buckets.isEmpty() ? List.of() : new ArrayList<>(buckets.getFirst().series().keySet());
        List<ChartSeries> series = keys.stream()
                .map(key -> chartSeries(label(key), buckets.stream()
                        .map(bucket -> new ChartValue(bucket.start().toString(), bucket.series().get(key)))
                        .toList()))
                .toList();
        return new ReportChart(title, type, series,
                notes(buckets.stream().flatMap(bucket -> bucket.series().values().stream()).toList()));
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private static ChartSeries chartSeries(String name, List<ChartValue> values) {
        return new ChartSeries(name, List.copyOf(values));
    }

    private static String label(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String spaced = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static <T> T cast(Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Unexpected analytics response for report: " + type.getSimpleName());
        }
        return type.cast(value);
    }
}
