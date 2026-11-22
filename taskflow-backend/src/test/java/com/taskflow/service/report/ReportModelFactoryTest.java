package com.taskflow.service.report;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsMeta;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.BugTrendResponse;
import com.taskflow.dto.analytics.DataAvailability;
import com.taskflow.dto.analytics.EmployeeWorkloadResponse;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse;
import com.taskflow.dto.analytics.MetricValue;
import com.taskflow.dto.analytics.ProjectGrowthResponse;
import com.taskflow.dto.analytics.ProjectPerformanceReportResponse;
import com.taskflow.dto.analytics.QaAnalyticsResponse;
import com.taskflow.dto.analytics.SprintBurndownResponse;
import com.taskflow.dto.analytics.SprintVelocityResponse;
import com.taskflow.dto.analytics.TaskCompletionTrendResponse;
import com.taskflow.dto.analytics.TeamProductivityResponse;
import com.taskflow.dto.analytics.TimeBucket;
import com.taskflow.dto.report.ReportMetadata;
import com.taskflow.dto.report.ReportType;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.dto.report.model.ChartType;
import com.taskflow.dto.report.model.ReportKpi;
import com.taskflow.dto.report.model.ReportSection;
import com.taskflow.entity.Organization;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.util.report.ReportMetadataFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportModelFactoryTest {

    private static final Organization ORGANIZATION = new Organization(
            "TaskFlow Enterprise", "taskflow-enterprise", "Software", "UTC", "MON-FRI");

    private static AnalyticsQuery query() {
        return new AnalyticsQuery(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of());
    }

    private static AnalyticsMeta meta() {
        return AnalyticsMeta.of(query(), "unit test basis");
    }

    private static ReportMetadata metadata(ReportType type) {
        return ReportMetadataFactory.create(ORGANIZATION, meta(), query(), type);
    }

    private static AnalyticsReportData create(ReportType type, Object response) {
        return ReportModelFactory.create(metadata(type), type, response);
    }

    private static TimeBucket bucket(LocalDate date, Map<String, MetricValue> series) {
        return new TimeBucket(date, date.plusDays(6), series);
    }

    @Test
    void executiveOverviewCarriesKpisAttentionAndTrend() {
        Map<String, MetricValue> kpis = new LinkedHashMap<>();
        kpis.put("totalTasks", MetricValue.available(BigDecimal.valueOf(12), "count"));
        kpis.put("openBugs", MetricValue.noData("count"));
        ExecutiveOverviewResponse response = new ExecutiveOverviewResponse(meta(), kpis,
                List.of(bucket(LocalDate.of(2026, 1, 5), Map.of("tasksCompleted",
                        MetricValue.available(BigDecimal.valueOf(2), "count")))),
                List.of(new ExecutiveOverviewResponse.AttentionItem(
                        "OVERDUE_TASKS", "Overdue", "3 tasks", null, null)));

        AnalyticsReportData model = create(ReportType.EXECUTIVE_OVERVIEW, response);

        assertThat(model.reportType()).isEqualTo(ReportType.EXECUTIVE_OVERVIEW);
        assertThat(model.metadata().reportTitle()).isEqualTo("Executive Overview");
        assertThat(model.summaryKpis()).hasSize(2);
        assertThat(model.summaryKpis().getFirst().metric()).isSameAs(kpis.get("totalTasks"));
        assertThat(model.availabilityNotes()).contains("No data in range");
        assertThat(model.sections().getFirst().tables().getFirst().rows()).hasSize(1);
        assertThat(model.sections().getFirst().charts().getFirst().type()).isEqualTo(ChartType.LINE);
        assertThat(model.emptyDataset()).isFalse();
    }

    @Test
    void projectPerformanceCarriesSummaryTableAndVelocity() {
        ProjectPerformanceReportResponse response = new ProjectPerformanceReportResponse(meta(),
                List.of(new ProjectPerformanceReportResponse.ProjectPerformanceRow(
                        1L, "ACME", "Acme", ProjectStatus.ACTIVE, "Priya", 10L, 5L, 1L, 2L,
                        MetricValue.available(BigDecimal.valueOf(50), "%"),
                        MetricValue.available(BigDecimal.valueOf(3), "count"))),
                new ProjectPerformanceReportResponse.PortfolioSummary(
                        MetricValue.available(BigDecimal.ONE, "count"),
                        MetricValue.available(BigDecimal.valueOf(50), "%"),
                        MetricValue.available(BigDecimal.valueOf(90), "%"),
                        MetricValue.available(BigDecimal.valueOf(2), "count")));

        AnalyticsReportData model = create(ReportType.PROJECT_PERFORMANCE, response);

        assertThat(model.summaryKpis()).extracting(ReportKpi::key)
                .containsExactly("totalProjects", "completionRate", "onTimeRate", "openBugs");
        assertThat(model.sections().getFirst().tables().getFirst().rows().getFirst())
                .containsExactly("ACME", "Acme", "ACTIVE", "Priya", 10L, 5L, 1L, 2L,
                        response.rows().getFirst().progressPercent(), response.rows().getFirst().velocity());
        assertThat(model.sections().getFirst().charts().getFirst().type()).isEqualTo(ChartType.BAR);
        assertThat(model.emptyDataset()).isFalse();
    }

    @Test
    void projectGrowthPreservesTimeBucketOrder() {
        MetricValue first = MetricValue.available(BigDecimal.ONE, "count");
        MetricValue second = MetricValue.available(BigDecimal.valueOf(2), "count");
        ProjectGrowthResponse response = new ProjectGrowthResponse(meta(),
                List.of(bucket(LocalDate.of(2026, 1, 5), Map.of("projectsCreated", first)),
                        bucket(LocalDate.of(2026, 1, 12), Map.of("projectsCreated", second))),
                MetricValue.available(BigDecimal.valueOf(2), "count"));

        AnalyticsReportData model = create(ReportType.PROJECT_GROWTH, response);

        var values = model.sections().getFirst().charts().getFirst().series().getFirst().values();
        assertThat(values).extracting(v -> v.label())
                .containsExactly("2026-01-05", "2026-01-12");
        assertThat(values.getFirst().metric()).isSameAs(first);
        assertThat(values.get(1).metric()).isSameAs(second);
    }

    @Test
    void sprintVelocityCarriesCommittedCompletedAndCarriedOver() {
        SprintVelocityResponse response = new SprintVelocityResponse(meta(),
                List.of(new SprintVelocityResponse.SprintVelocityPoint(7L, "Sprint 7",
                        MetricValue.available(BigDecimal.valueOf(5), "count"),
                        MetricValue.available(BigDecimal.valueOf(3), "count"),
                        MetricValue.available(BigDecimal.valueOf(2), "count"),
                        MetricValue.available(BigDecimal.valueOf(60), "%"))),
                MetricValue.available(BigDecimal.valueOf(3), "count"));

        AnalyticsReportData model = create(ReportType.SPRINT_VELOCITY, response);

        assertThat(model.summaryKpis().getFirst().key()).isEqualTo("averageVelocity");
        assertThat(model.sections().getFirst().charts().getFirst().series()).hasSize(3);
        assertThat(model.sections().getFirst().tables().getFirst().rows().getFirst())
                .contains("Sprint 7", response.points().getFirst().completed());
    }

    @Test
    void sprintBurndownPreservesAvailabilityOfActualLine() {
        SprintBurndownResponse response = new SprintBurndownResponse(meta(),
                List.of(new SprintBurndownResponse.BurndownPoint(LocalDate.of(2026, 1, 5),
                        MetricValue.available(BigDecimal.valueOf(4), "count"),
                        MetricValue.insufficientHistory("count"))),
                DataAvailability.INSUFFICIENT_HISTORY);

        AnalyticsReportData model = create(ReportType.SPRINT_BURNOWN, response);

        assertThat(model.sections().getFirst().charts().getFirst().series()).hasSize(2);
        assertThat(model.availabilityNotes()).anyMatch(note -> note.contains("Insufficient history"));
    }

    @Test
    void taskTrendsPreserveComparisonData() {
        MetricValue compared = MetricValue.compared(BigDecimal.valueOf(4), BigDecimal.valueOf(2), "count");
        TaskCompletionTrendResponse response = new TaskCompletionTrendResponse(meta(),
                List.of(bucket(LocalDate.of(2026, 1, 5), Map.of("tasksCompleted", compared))), compared);

        AnalyticsReportData model = create(ReportType.TASK_COMPLETION_TRENDS, response);

        assertThat(model.summaryKpis().getFirst().metric().previousValue()).isEqualByComparingTo("2");
        assertThat(model.summaryKpis().getFirst().metric().deltaPercent()).isEqualByComparingTo("100");
        assertThat(model.sections().getFirst().charts().getFirst().series().getFirst()
                .values().getFirst().metric()).isSameAs(compared);
    }

    @Test
    void teamProductivityCarriesRowsAndCompletionRateChart() {
        TeamProductivityResponse response = new TeamProductivityResponse(meta(),
                List.of(new TeamProductivityResponse.TeamProductivityRow(3L, "Platform", 4L,
                        MetricValue.available(BigDecimal.valueOf(7), "count"),
                        MetricValue.available(BigDecimal.valueOf(2), "count"),
                        MetricValue.available(BigDecimal.ZERO, "count"),
                        MetricValue.available(BigDecimal.valueOf(78), "%"))));

        AnalyticsReportData model = create(ReportType.TEAM_PRODUCTIVITY, response);

        assertThat(model.sections().getFirst().tables().getFirst().rows().getFirst()).contains("Platform", 4L);
        assertThat(model.sections().getFirst().charts().getFirst().series().getFirst().name())
                .isEqualTo("Completion Rate");
    }

    @Test
    void employeeWorkloadReportsNotConfiguredCapacity() {
        EmployeeWorkloadResponse response = new EmployeeWorkloadResponse(meta(),
                List.of(new EmployeeWorkloadResponse.EmployeeWorkloadRow(5L, "Arjun", "DEVELOPER",
                        MetricValue.available(BigDecimal.valueOf(3), "count"),
                        MetricValue.available(BigDecimal.valueOf(2), "count"),
                        MetricValue.available(BigDecimal.valueOf(12), "hours"),
                        MetricValue.notConfigured("hours"),
                        MetricValue.notConfigured("%"))),
                DataAvailability.NOT_CONFIGURED);

        AnalyticsReportData model = create(ReportType.EMPLOYEE_WORKLOAD, response);

        assertThat(model.availabilityNotes()).contains("Capacity basis: Not configured");
        assertThat(model.sections().getFirst().tables().getFirst().rows().getFirst()).contains("Arjun");
    }

    @Test
    void bugTrendsCarryReopenRateMttrAndLifecycleSeries() {
        BugTrendResponse response = new BugTrendResponse(meta(),
                List.of(bucket(LocalDate.of(2026, 1, 5), Map.of(
                        "bugsCreated", MetricValue.available(BigDecimal.valueOf(2), "count"),
                        "bugsResolved", MetricValue.available(BigDecimal.ONE, "count"),
                        "bugsReopened", MetricValue.noData("count")))),
                MetricValue.available(BigDecimal.valueOf(50), "%"),
                MetricValue.available(new BigDecimal("6.00"), "days"));

        AnalyticsReportData model = create(ReportType.BUG_TRENDS, response);

        assertThat(model.summaryKpis()).extracting(ReportKpi::key).containsExactly("reopenRate", "mttrDays");
        assertThat(model.sections().getFirst().charts().getFirst().type()).isEqualTo(ChartType.STACKED_BAR);
        assertThat(model.availabilityNotes()).contains("No data in range");
    }

    @Test
    void qaAnalyticsCarriesResultsPassRateDefectLinkAndRuns() {
        Map<String, MetricValue> results = new LinkedHashMap<>();
        results.put("PASS", MetricValue.available(BigDecimal.valueOf(3), "count"));
        results.put("FAIL", MetricValue.available(BigDecimal.ONE, "count"));
        QaAnalyticsResponse response = new QaAnalyticsResponse(meta(), results,
                MetricValue.available(BigDecimal.valueOf(75), "%"),
                MetricValue.available(BigDecimal.valueOf(100), "%"),
                List.of(new QaAnalyticsResponse.QaRunRow(9L, "Regression", "COMPLETED",
                        MetricValue.available(BigDecimal.valueOf(4), "count"),
                        results.get("PASS"), results.get("FAIL"),
                        MetricValue.available(BigDecimal.ZERO, "count"),
                        MetricValue.available(BigDecimal.ZERO, "count"))));

        AnalyticsReportData model = create(ReportType.QA_ANALYTICS, response);

        assertThat(model.summaryKpis()).extracting(ReportKpi::key)
                .containsExactly("PASS", "FAIL", "passRate", "defectLinkRate");
        assertThat(model.sections().getFirst().charts().getFirst().type()).isEqualTo(ChartType.DONUT);
        assertThat(model.sections().getFirst().tables().getFirst().rows()).hasSize(1);
    }

    @Test
    void trueZeroIsAvailableWhileNullIsUnavailable() {
        Map<String, MetricValue> kpis = new LinkedHashMap<>();
        kpis.put("zero", MetricValue.available(BigDecimal.ZERO, "count"));
        kpis.put("missing", MetricValue.noData("count"));
        ExecutiveOverviewResponse response = new ExecutiveOverviewResponse(
                meta(), kpis, List.of(), List.of());

        AnalyticsReportData model = create(ReportType.EXECUTIVE_OVERVIEW, response);

        assertThat(model.summaryKpis().get(0).metric().value()).isEqualByComparingTo("0");
        assertThat(model.summaryKpis().get(0).metric().availability()).isEqualTo(DataAvailability.AVAILABLE);
        assertThat(model.summaryKpis().get(1).metric().value()).isNull();
        assertThat(model.summaryKpis().get(1).metric().availability()).isEqualTo(DataAvailability.NO_DATA_IN_RANGE);
    }

    @Test
    void emptyDatasetsAreFlagged() {
        ExecutiveOverviewResponse empty = new ExecutiveOverviewResponse(meta(), Map.of(),
                List.of(), List.of());
        ProjectGrowthResponse growth = new ProjectGrowthResponse(meta(), List.of(), MetricValue.noData("count"));
        QaAnalyticsResponse qa = new QaAnalyticsResponse(meta(), Map.of(),
                MetricValue.noData("%"), MetricValue.noData("%"), List.of());

        assertThat(create(ReportType.EXECUTIVE_OVERVIEW, empty).emptyDataset()).isTrue();
        assertThat(create(ReportType.PROJECT_GROWTH, growth).emptyDataset()).isTrue();
        assertThat(create(ReportType.QA_ANALYTICS, qa).emptyDataset()).isTrue();
    }

    @Test
    void executiveOverviewIntegratesAllSixAnalyticsStructures() {
        LocalDate periodStart = LocalDate.of(2026, 1, 5);
        Map<String, MetricValue> createdSeries = Map.of("tasksCreated",
                MetricValue.available(BigDecimal.valueOf(2), "count"));
        Map<String, MetricValue> auditSeries = Map.of("TASK_CREATED",
                MetricValue.available(BigDecimal.valueOf(5), "count"));
        ExecutiveOverviewResponse response = new ExecutiveOverviewResponse(meta(),
                Map.of("totalProjects", MetricValue.available(BigDecimal.valueOf(2), "count")),
                List.of(bucket(periodStart, Map.of("tasksCompleted",
                        MetricValue.available(BigDecimal.ONE, "count")))),
                List.of(),
                new ExecutiveOverviewResponse.ProjectStatusSummary(2, 1, 1, 0,
                        Map.of(ProjectStatus.ACTIVE, 1L, ProjectStatus.COMPLETED, 1L),
                        DataAvailability.AVAILABLE),
                new ExecutiveOverviewResponse.TaskPriorityDistribution(
                        Map.of(PriorityLevel.HIGH, 3L), DataAvailability.AVAILABLE),
                new ExecutiveOverviewResponse.TaskCreationTrend(
                        List.of(bucket(periodStart, createdSeries)), DataAvailability.AVAILABLE),
                new ExecutiveOverviewResponse.EmployeeCompletionMetrics(List.of(
                        new ExecutiveOverviewResponse.EmployeeCompletionRow(
                                1L, "Dev", "DEVELOPER", 4, 2, 1, 1)), DataAvailability.AVAILABLE),
                new ExecutiveOverviewResponse.AuditActivityTrend(
                        List.of(bucket(periodStart, auditSeries)),
                        Map.of(AuditAction.TASK_CREATED, 5L), DataAvailability.AVAILABLE),
                new ExecutiveOverviewResponse.BugSeverityDistribution(
                        Map.of(BugSeverity.HIGH, 2L), DataAvailability.AVAILABLE));

        AnalyticsReportData model = create(ReportType.EXECUTIVE_OVERVIEW, response);

        assertThat(model.sections()).extracting(ReportSection::title).containsExactly(
                "Executive Overview", "Project Status", "Task Analytics", "Team Productivity",
                "Activity Trends", "Quality Analytics", "Executive Insights");
        ReportSection insights = model.sections().stream()
                .filter(section -> section.title().equals("Executive Insights")).findFirst().orElseThrow();
        assertThat(insights.tables().getFirst().rows()).isNotEmpty();
        assertThat(insights.tables().getFirst().rows().getFirst().get(1).toString()).contains("Total 2");
        ReportSection taskAnalytics = model.sections().stream()
                .filter(section -> section.title().equals("Task Analytics")).findFirst().orElseThrow();
        assertThat(taskAnalytics.charts().getFirst().type()).isEqualTo(ChartType.LINE);
        assertThat(model.emptyDataset()).isFalse();
    }
}
