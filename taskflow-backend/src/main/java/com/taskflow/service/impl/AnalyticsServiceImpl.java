package com.taskflow.service.impl;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsMeta;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.AnalyticsScope;
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
import com.taskflow.dto.analytics.projections.AuditActivityProjection;
import com.taskflow.dto.analytics.projections.BugResolutionProjection;
import com.taskflow.dto.analytics.projections.BugStatusCountProjection;
import com.taskflow.dto.analytics.projections.CountByKeyProjection;
import com.taskflow.dto.analytics.projections.DailyCountProjection;
import com.taskflow.dto.analytics.projections.QaResultProjection;
import com.taskflow.dto.analytics.projections.QaRunResultProjection;
import com.taskflow.dto.analytics.projections.SprintTaskAnalyticsProjection;
import com.taskflow.dto.analytics.projections.StatusEventProjection;
import com.taskflow.dto.analytics.projections.StringKeyCountProjection;
import com.taskflow.dto.analytics.projections.TaskCompletionProjection;
import com.taskflow.dto.analytics.projections.TaskStatusCountProjection;
import com.taskflow.dto.analytics.projections.TeamMemberProjection;
import com.taskflow.dto.analytics.projections.WorkloadProjection;
import com.taskflow.dto.live.LiveTaskSummaryResponse;
import com.taskflow.entity.AuditLog;
import com.taskflow.entity.Project;
import com.taskflow.entity.Sprint;
import com.taskflow.entity.Team;
import com.taskflow.entity.TestRun;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.TaskStatus;
import com.taskflow.enums.TestExecutionResult;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.BugHistoryRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.SprintTaskRepository;
import com.taskflow.repository.TaskHistoryRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.TestExecutionRepository;
import com.taskflow.repository.TestRunRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.AnalyticsAuthorizationService;
import com.taskflow.service.AnalyticsService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Database-backed analytics aggregations.
 *
 * <p>All queries are org-scoped and constrained to the projects the viewer may
 * see. Counts are grouped in SQL; time bucketing happens in Java from minimal
 * timestamp projections, keeping the queries portable across MySQL and H2.
 * No dataset is ever filled with fabricated values.</p>
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final String COUNT_UNIT = "count";
    private static final String PERCENT_UNIT = "%";
    private static final String DAYS_UNIT = "days";
    private static final Set<Long> NO_IDS = Set.of(-1L);

    /** Delivery-related audit actions surfaced as audit-log activity. */
    private static final Set<AuditAction> EXECUTIVE_AUDIT_ACTIONS = Set.of(
            AuditAction.PROJECT_CREATED, AuditAction.PROJECT_STATUS_CHANGED, AuditAction.PROJECT_ARCHIVED,
            AuditAction.TASK_CREATED, AuditAction.TASK_UPDATED, AuditAction.TASK_ASSIGNED,
            AuditAction.TASK_STATUS_CHANGED, AuditAction.TASK_DEADLINE_MODIFIED, AuditAction.TASK_DELETED,
            AuditAction.SPRINT_CREATED, AuditAction.SPRINT_STARTED, AuditAction.SPRINT_COMPLETED,
            AuditAction.SPRINT_CANCELLED, AuditAction.BUG_REPORTED, AuditAction.BUG_VERIFIED,
            AuditAction.BUG_REOPENED, AuditAction.BUG_DELETED
    );

    private final AnalyticsAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskRepository taskRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final BugRepository bugRepository;
    private final BugHistoryRepository bugHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final SprintRepository sprintRepository;
    private final SprintTaskRepository sprintTaskRepository;
    private final TestRunRepository testRunRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;

    public AnalyticsServiceImpl(
            AnalyticsAuthorizationService authorizationService,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TaskRepository taskRepository,
            TaskHistoryRepository taskHistoryRepository,
            BugRepository bugRepository,
            BugHistoryRepository bugHistoryRepository,
            AuditLogRepository auditLogRepository,
            SprintRepository sprintRepository,
            SprintTaskRepository sprintTaskRepository,
            TestRunRepository testRunRepository,
            TestExecutionRepository testExecutionRepository,
            ProjectSecurityEvaluator projectSecurityEvaluator
    ) {
        this.authorizationService = authorizationService;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.taskRepository = taskRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.bugRepository = bugRepository;
        this.bugHistoryRepository = bugHistoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.sprintRepository = sprintRepository;
        this.sprintTaskRepository = sprintTaskRepository;
        this.testRunRepository = testRunRepository;
        this.testExecutionRepository = testExecutionRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
    }

    // ------------------------------------------------------------------
    // 1. Executive overview
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public ExecutiveOverviewResponse getExecutiveOverview(AnalyticsQuery query) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "current snapshot + event history");
        Map<String, MetricValue> kpis = new LinkedHashMap<>();

        List<TaskStatusCountProjection> taskCounts = taskRepository.countAnalyticsTasksByProjectAndStatus(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.applyUserFilter(), ctx.userIds());
        List<CountByKeyProjection> overdue = taskRepository.countAnalyticsOverdueTasksByProject(
                ctx.scope.organizationId(), ctx.projectIds(), LocalDate.now(),
                ctx.applyUserFilter(), ctx.userIds());
        List<BugStatusCountProjection> bugCounts = bugRepository.countAnalyticsBugsByProjectAndStatus(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.applyUserFilter(), ctx.userIds());
        List<CountByKeyProjection> criticalBugs = bugRepository.countAnalyticsCriticalBugsByProject(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.applyUserFilter(), ctx.userIds());

        long totalTasks = taskCounts.stream().mapToLong(TaskStatusCountProjection::count).sum();
        long completedTasks = countByStatus(taskCounts, TaskStatus.DONE);
        long overdueTasks = overdue.stream().mapToLong(CountByKeyProjection::count).sum();
        long openBugs = bugCounts.stream().filter(this::isOpenBugStatus).mapToLong(BugStatusCountProjection::count).sum();
        long criticalBugCount = criticalBugs.stream().mapToLong(CountByKeyProjection::count).sum();

        boolean hasTaskData = !taskCounts.isEmpty() || !overdue.isEmpty();
        boolean hasBugData = !bugCounts.isEmpty();
        kpis.put("totalProjects", dataOrNoData(ctx.projectIds().size(), ctx.hasProjects()));
        kpis.put("totalTasks", dataOrNoData(totalTasks, hasTaskData));
        kpis.put("completedTasks", dataOrNoData(completedTasks, hasTaskData));
        kpis.put("overdueTasks", dataOrNoData(overdueTasks, hasTaskData));
        kpis.put("openBugs", dataOrNoData(openBugs, hasBugData));
        kpis.put("criticalBugs", dataOrNoData(criticalBugCount, hasBugData));

        CompletionResult completions = taskCompletions(ctx);
        kpis.put("tasksCompletedInRange", comparedMetric(completions.total(), completions.previous(), ctx));

        QaSummary qaSummary = qaSummary(ctx);
        kpis.put("qaExecutions", dataOrNoData(qaSummary.totalExecutions(), qaSummary.totalExecutions() > 0));
        kpis.put("qaFailures", dataOrNoData(qaSummary.failed(), qaSummary.totalExecutions() > 0));

        Map<LocalDate, Map<String, Long>> trendCounts = new LinkedHashMap<>();
        mergeCounts(trendCounts, completions.counts(), "tasksCompleted");
        BugTrendData bugs = bugTrendData(ctx);
        mergeCounts(trendCounts, bugs.counts(), "bugsCreated");
        boolean anyTrendData = !completions.events().isEmpty() || !bugs.created().isEmpty();
        List<TimeBucket> trend = buildBuckets(ctx, trendCounts,
                List.of("tasksCompleted", "bugsCreated"), anyTrendData);

        List<ExecutiveOverviewResponse.AttentionItem> attention = new ArrayList<>();
        if (overdueTasks > 0) {
            attention.add(new ExecutiveOverviewResponse.AttentionItem(
                    "OVERDUE_TASKS", "Overdue tasks", overdueTasks + " tasks are past their due date.", null, null));
        }
        if (criticalBugCount > 0) {
            attention.add(new ExecutiveOverviewResponse.AttentionItem(
                    "CRITICAL_BUGS", "Critical bugs", criticalBugCount + " critical bugs are still open.", null, null));
        }
        if (qaSummary.failed() > 0) {
            attention.add(new ExecutiveOverviewResponse.AttentionItem(
                    "QA_FAILURES", "Failed test executions", qaSummary.failed() + " executions failed in this range.", null, null));
        }

        return new ExecutiveOverviewResponse(meta, kpis, trend, attention,
                projectStatusSummary(ctx),
                taskPriorityDistribution(ctx),
                taskCreationTrend(ctx),
                employeeCompletionMetrics(ctx),
                auditActivityTrend(ctx),
                bugSeverityDistribution(ctx));
    }

    @Override
    @Transactional(readOnly = true)
    public LiveTaskSummaryResponse getLiveTaskSummary(AnalyticsQuery query) {
        Context ctx = resolve(query);
        List<TaskStatusCountProjection> taskCounts = taskRepository.countAnalyticsTasksByProjectAndStatus(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.applyUserFilter(), ctx.userIds());
        List<CountByKeyProjection> overdue = taskRepository.countAnalyticsOverdueTasksByProject(
                ctx.scope.organizationId(), ctx.projectIds(), LocalDate.now(),
                ctx.applyUserFilter(), ctx.userIds());

        boolean hasTaskData = !taskCounts.isEmpty() || !overdue.isEmpty();
        long totalTasks = taskCounts.stream().mapToLong(TaskStatusCountProjection::count).sum();
        long overdueTasks = overdue.stream().mapToLong(CountByKeyProjection::count).sum();

        Map<String, MetricValue> kpis = new LinkedHashMap<>();
        kpis.put("totalTasks", dataOrNoData(totalTasks, hasTaskData));
        kpis.put("completedTasks", dataOrNoData(countByStatus(taskCounts, TaskStatus.DONE), hasTaskData));
        kpis.put("inProgressTasks", dataOrNoData(countByStatus(taskCounts, TaskStatus.IN_PROGRESS), hasTaskData));
        kpis.put("overdueTasks", dataOrNoData(overdueTasks, hasTaskData));

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            long count = countByStatus(taskCounts, status);
            if (hasTaskData) {
                distribution.put(status.name(), count);
            }
        }
        return new LiveTaskSummaryResponse(Map.copyOf(kpis), Map.copyOf(distribution));
    }

    private ExecutiveOverviewResponse.ProjectStatusSummary projectStatusSummary(Context ctx) {
        List<Project> projects = projectRepository.findAnalyticsProjectsByIds(ctx.scope.organizationId(), ctx.projectIds());
        if (projects.isEmpty()) {
            return ExecutiveOverviewResponse.ProjectStatusSummary.empty();
        }
        Map<ProjectStatus, Long> byStatus = new LinkedHashMap<>();
        for (Project project : projects) {
            byStatus.merge(project.getStatus(), 1L, Long::sum);
        }
        long total = projects.size();
        long active = byStatus.getOrDefault(ProjectStatus.ACTIVE, 0L);
        long completed = byStatus.getOrDefault(ProjectStatus.COMPLETED, 0L);
        return new ExecutiveOverviewResponse.ProjectStatusSummary(
                total, active, completed, total - active - completed,
                Collections.unmodifiableMap(byStatus), DataAvailability.AVAILABLE);
    }

    private ExecutiveOverviewResponse.TaskPriorityDistribution taskPriorityDistribution(Context ctx) {
        List<StringKeyCountProjection> rows = taskRepository.countAnalyticsTasksByPriority(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.applyUserFilter(), ctx.userIds());
        Map<PriorityLevel, Long> counts = new LinkedHashMap<>();
        for (StringKeyCountProjection row : rows) {
            try {
                counts.merge(PriorityLevel.valueOf(row.key()), row.count(), Long::sum);
            } catch (IllegalArgumentException ignored) {
                // Unknown enum values are not represented in the response.
            }
        }
        return new ExecutiveOverviewResponse.TaskPriorityDistribution(
                Collections.unmodifiableMap(counts),
                rows.isEmpty() ? DataAvailability.NO_DATA_IN_RANGE : DataAvailability.AVAILABLE);
    }

    private ExecutiveOverviewResponse.TaskCreationTrend taskCreationTrend(Context ctx) {
        List<DailyCountProjection> dailyCounts = ctx.applyUserFilter()
                ? taskRepository.countAnalyticsTaskCreationsByDayForUsers(
                        ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive(), ctx.userIds())
                : taskRepository.countAnalyticsTaskCreationsByDay(
                        ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive());
        Map<LocalDate, Map<String, Long>> counts = new LinkedHashMap<>();
        for (DailyCountProjection row : dailyCounts) {
            if (row.getBucketDay() == null || row.getTaskCount() == null) {
                continue;
            }
            LocalDate bucket = bucketStart(row.getBucketDay().atStartOfDay(ZoneOffset.UTC).toInstant(),
                    ctx.granularity());
            counts.computeIfAbsent(bucket, key -> new HashMap<>())
                    .merge("tasksCreated", row.getTaskCount(), Long::sum);
        }
        return new ExecutiveOverviewResponse.TaskCreationTrend(
                buildBuckets(ctx, counts, List.of("tasksCreated"), !dailyCounts.isEmpty()),
                dailyCounts.isEmpty() ? DataAvailability.NO_DATA_IN_RANGE : DataAvailability.AVAILABLE);
    }

    private ExecutiveOverviewResponse.EmployeeCompletionMetrics employeeCompletionMetrics(Context ctx) {
        List<User> users;
        if (ctx.userIds().isEmpty()) {
            users = userRepository.searchUsers(ctx.scope.organizationId(), null, null,
                    Boolean.TRUE, Pageable.unpaged()).getContent();
        } else {
            users = userRepository.findAllById(ctx.userIds()).stream()
                    .filter(user -> user.getOrganization().getId().equals(ctx.scope.organizationId()))
                    .toList();
        }
        if (users.isEmpty()) {
            return ExecutiveOverviewResponse.EmployeeCompletionMetrics.empty();
        }

        List<Long> userIds = users.stream().map(User::getId).toList();
        Map<Long, Map<TaskStatus, Long>> statusByUser = new HashMap<>();
        for (TaskStatusCountProjection row : taskRepository.countAnalyticsTasksByAssigneeAndStatusInProjects(
                ctx.scope.organizationId(), ctx.projectIds(), userIds)) {
            statusByUser.computeIfAbsent(row.key(), key -> new HashMap<>())
                    .put(TaskStatus.valueOf(row.status()), row.count());
        }
        Map<Long, Long> overdueByUser = taskRepository.countAnalyticsOverdueTasksByAssigneeInProjects(
                ctx.scope.organizationId(), ctx.projectIds(), userIds, ctx.to()).stream()
                .collect(Collectors.toMap(CountByKeyProjection::key, CountByKeyProjection::count));
        Map<Long, Long> completedByUser = taskHistoryRepository.countAnalyticsTaskCompletionsByAssignee(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive(), userIds).stream()
                .collect(Collectors.toMap(CountByKeyProjection::key, CountByKeyProjection::count));

        List<ExecutiveOverviewResponse.EmployeeCompletionRow> rows = new ArrayList<>();
        for (User user : users) {
            Map<TaskStatus, Long> byStatus = statusByUser.getOrDefault(user.getId(), Map.of());
            long assigned = byStatus.values().stream().mapToLong(Long::longValue).sum();
            long active = byStatus.getOrDefault(TaskStatus.IN_PROGRESS, 0L)
                    + byStatus.getOrDefault(TaskStatus.IN_REVIEW, 0L);
            // Completed uses DONE status transitions within the reporting period;
            // assigned/active/overdue use the current task snapshot.
            long completed = completedByUser.getOrDefault(user.getId(), 0L);
            long overdue = overdueByUser.getOrDefault(user.getId(), 0L);
            rows.add(new ExecutiveOverviewResponse.EmployeeCompletionRow(
                    user.getId(), user.getFullName(), user.getPrimaryRoleName().name(),
                    assigned, active, completed, overdue));
        }
        return new ExecutiveOverviewResponse.EmployeeCompletionMetrics(
                List.copyOf(rows), DataAvailability.AVAILABLE);
    }

    private ExecutiveOverviewResponse.AuditActivityTrend auditActivityTrend(Context ctx) {
        List<AuditActivityProjection> rows = ctx.applyUserFilter()
                ? auditLogRepository.aggregateAuditActivityForUsers(
                        ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive(),
                        ctx.userIds(), EXECUTIVE_AUDIT_ACTIONS.stream().map(Enum::name).toList())
                : auditLogRepository.aggregateAuditActivity(
                        ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive(),
                        EXECUTIVE_AUDIT_ACTIONS.stream().map(Enum::name).toList());
        Map<LocalDate, Map<String, Long>> counts = new LinkedHashMap<>();
        Map<AuditAction, Long> totals = new LinkedHashMap<>();
        for (AuditActivityProjection row : rows) {
            if (row.getBucketDay() == null || row.getAction() == null || row.getEventCount() == null) {
                continue;
            }
            long count = row.getEventCount();
            addCount(counts, row.getBucketDay().atStartOfDay(ZoneOffset.UTC).toInstant(),
                    row.getAction(), ctx.granularity());
            try {
                totals.merge(AuditAction.valueOf(row.getAction()), count, Long::sum);
            } catch (IllegalArgumentException ignored) {
                // Unknown actions are not represented in the response.
            }
        }
        List<String> keys = EXECUTIVE_AUDIT_ACTIONS.stream().map(Enum::name).sorted().toList();
        return new ExecutiveOverviewResponse.AuditActivityTrend(
                buildBuckets(ctx, counts, keys, !rows.isEmpty()),
                Collections.unmodifiableMap(totals),
                rows.isEmpty() ? DataAvailability.NO_DATA_IN_RANGE : DataAvailability.AVAILABLE);
    }

    private ExecutiveOverviewResponse.BugSeverityDistribution bugSeverityDistribution(Context ctx) {
        List<StringKeyCountProjection> rows = bugRepository.countAnalyticsBugsBySeverity(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.applyUserFilter(), ctx.userIds());
        Map<BugSeverity, Long> counts = new LinkedHashMap<>();
        for (StringKeyCountProjection row : rows) {
            try {
                counts.merge(BugSeverity.valueOf(row.key()), row.count(), Long::sum);
            } catch (IllegalArgumentException ignored) {
                // Unknown enum values are not represented in the response.
            }
        }
        return new ExecutiveOverviewResponse.BugSeverityDistribution(
                Collections.unmodifiableMap(counts),
                rows.isEmpty() ? DataAvailability.NO_DATA_IN_RANGE : DataAvailability.AVAILABLE);
    }

    // ------------------------------------------------------------------
    // 2. Project performance
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public ProjectPerformanceReportResponse getProjectPerformance(AnalyticsQuery query) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "task/bug snapshots + completion history");
        List<Project> projects = projectRepository.findAnalyticsProjectsByIds(ctx.scope.organizationId(), ctx.projectIds());
        Map<Long, Project> projectsById = projects.stream().collect(Collectors.toMap(Project::getId, p -> p));

        List<TaskStatusCountProjection> taskCounts = taskRepository.countAnalyticsTasksByProjectAndStatus(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.applyUserFilter(), ctx.userIds());
        Map<Long, Map<TaskStatus, Long>> tasksByProject = new HashMap<>();
        for (TaskStatusCountProjection row : taskCounts) {
            tasksByProject.computeIfAbsent(row.key(), k -> new HashMap<>()).put(TaskStatus.valueOf(row.status()), row.count());
        }
        Map<Long, Long> overdueByProject = taskRepository.countAnalyticsOverdueTasksByProject(
                ctx.scope.organizationId(), ctx.projectIds(), LocalDate.now(),
                ctx.applyUserFilter(), ctx.userIds())
                .stream().collect(Collectors.toMap(CountByKeyProjection::key, CountByKeyProjection::count));
        Map<Long, Long> openBugsByProject = bugRepository.countAnalyticsBugsByProjectAndStatus(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.applyUserFilter(), ctx.userIds())
                .stream().filter(this::isOpenBugStatus)
                .collect(Collectors.groupingBy(BugStatusCountProjection::key, Collectors.summingLong(BugStatusCountProjection::count)));

        CompletionResult completions = taskCompletions(ctx);
        Map<Long, Long> completedInRange = completions.events().stream()
                .collect(Collectors.groupingBy(TaskCompletionProjection::projectId, Collectors.counting()));
        Map<Long, Long> previousCompleted = completions.previousEvents().stream()
                .collect(Collectors.groupingBy(TaskCompletionProjection::projectId, Collectors.counting()));

        List<ProjectPerformanceReportResponse.ProjectPerformanceRow> rows = new ArrayList<>();
        long totalTasks = 0, totalCompleted = 0, totalOverdue = 0;
        for (Project project : projects) {
            Map<TaskStatus, Long> byStatus = tasksByProject.getOrDefault(project.getId(), Map.of());
            long projectTasks = byStatus.values().stream().mapToLong(Long::longValue).sum();
            long projectCompleted = byStatus.getOrDefault(TaskStatus.DONE, 0L);
            long projectOverdue = overdueByProject.getOrDefault(project.getId(), 0L);
            long openBugs = openBugsByProject.getOrDefault(project.getId(), 0L);
            totalTasks += projectTasks;
            totalCompleted += projectCompleted;
            totalOverdue += projectOverdue;

            MetricValue progress = projectTasks > 0
                    ? MetricValue.available(percentage(projectCompleted, projectTasks), PERCENT_UNIT)
                    : MetricValue.noData(PERCENT_UNIT);
            long completedCount = completedInRange.getOrDefault(project.getId(), 0L);
            MetricValue velocity = completedCount == 0 && ctx.compare() == null
                    ? MetricValue.noData(COUNT_UNIT)
                    : comparedMetric(completedCount, previousCompleted.get(project.getId()), ctx);

            rows.add(new ProjectPerformanceReportResponse.ProjectPerformanceRow(
                    project.getId(), project.getProjectKey(), project.getName(), project.getStatus(),
                    project.getManager() != null ? project.getManager().getFullName() : null,
                    projectTasks, projectCompleted, projectOverdue, openBugs, progress, velocity));
        }
        rows.sort((a, b) -> a.projectId().compareTo(b.projectId()));

        MetricValue completionRate = totalTasks > 0
                ? MetricValue.available(percentage(totalCompleted, totalTasks), PERCENT_UNIT)
                : MetricValue.noData(PERCENT_UNIT);
        MetricValue onTimeRate = totalTasks > 0
                ? MetricValue.available(percentage(totalTasks - totalOverdue, totalTasks), PERCENT_UNIT)
                : MetricValue.noData(PERCENT_UNIT);
        MetricValue totalOpenBugs = openBugsByProject.isEmpty()
                ? MetricValue.noData(COUNT_UNIT)
                : countMetric(openBugsByProject.values().stream().mapToLong(Long::longValue).sum());

        return new ProjectPerformanceReportResponse(meta, rows,
                new ProjectPerformanceReportResponse.PortfolioSummary(
                        countMetric(rows.size()), completionRate, onTimeRate, totalOpenBugs));
    }

    // ------------------------------------------------------------------
    // 3. Project growth
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public ProjectGrowthResponse getProjectGrowth(AnalyticsQuery query) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "project created_at + status-change audit events");

        List<Project> created = projectRepository.findByOrganizationIdAndCreatedAtBetween(
                ctx.scope.organizationId(), ctx.fromInstant(), ctx.toExclusive()).stream()
                .filter(p -> ctx.projectIds().contains(p.getId()))
                .toList();
        List<StatusEventProjection> completedEvents = projectCompletionEvents(ctx, ctx.fromInstant(), ctx.toExclusive());
        List<StatusEventProjection> previousCreated = ctx.compare() == null ? List.of() : projectCreatedEvents(ctx, ctx.compare());
        List<StatusEventProjection> previousCompleted = ctx.compare() == null ? List.of()
                : projectCompletionEvents(ctx, ctx.compare().fromInstant(), ctx.compare().toExclusive());

        Map<LocalDate, Map<String, Long>> counts = new LinkedHashMap<>();
        for (Project project : created) {
            addCount(counts, project.getCreatedAt(), "projectsCreated", ctx.granularity());
        }
        for (StatusEventProjection event : completedEvents) {
            addCount(counts, event.occurredAt(), "projectsCompleted", ctx.granularity());
        }
        boolean anyData = !created.isEmpty() || !completedEvents.isEmpty();
        List<TimeBucket> buckets = buildBuckets(ctx, counts, List.of("projectsCreated", "projectsCompleted"), anyData);

        long current = created.size();
        long previous = previousCreated.size();
        MetricValue netGrowth = current == 0 && (ctx.compare() == null || previous == 0)
                ? MetricValue.noData(COUNT_UNIT)
                : comparedMetric(current, ctx.compare() == null ? null : previous, ctx);

        return new ProjectGrowthResponse(meta, buckets, netGrowth);
    }

    // ------------------------------------------------------------------
    // 4. Sprint velocity
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public SprintVelocityResponse getSprintVelocity(AnalyticsQuery query) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "sprint task membership + task status history");
        List<Sprint> sprints = sprintRepository.findAnalyticsSprints(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.from(), ctx.to());
        if (sprints.isEmpty()) {
            return new SprintVelocityResponse(meta, List.of(), MetricValue.noData(COUNT_UNIT));
        }
        List<Long> sprintIds = sprints.stream().map(Sprint::getId).toList();
        List<SprintTaskAnalyticsProjection> rows = sprintTaskRepository.findAnalyticsSprintTasks(
                sprintIds, ctx.applyUserFilter(), ctx.userIds());
        Map<Long, List<SprintTaskAnalyticsProjection>> bySprint = rows.stream()
                .collect(Collectors.groupingBy(SprintTaskAnalyticsProjection::sprintId));

        List<SprintVelocityResponse.SprintVelocityPoint> points = new ArrayList<>();
        long totalCompleted = 0;
        int sprintsWithScope = 0;
        for (Sprint sprint : sprints) {
            List<SprintTaskAnalyticsProjection> sprintRows = bySprint.getOrDefault(sprint.getId(), List.of());
            boolean snapshotEnd = sprint.getStatus() == com.taskflow.enums.SprintStatus.COMPLETED
                    || sprint.getStatus() == com.taskflow.enums.SprintStatus.CANCELLED;
            long committed = sprintRows.stream().filter(r -> r.removedAt() == null).count();
            long completed = sprintRows.stream()
                    .filter(r -> r.removedAt() == null)
                    .filter(r -> "DONE".equals(effectiveStatus(r, snapshotEnd)))
                    .count();
            long carriedOver = committed - completed;
            totalCompleted += completed;
            if (committed > 0) {
                sprintsWithScope++;
            }
            points.add(new SprintVelocityResponse.SprintVelocityPoint(
                    sprint.getId(), sprint.getName(),
                    dataOrNoData(committed, committed > 0 || !sprintRows.isEmpty()),
                    dataOrNoData(completed, committed > 0 || !sprintRows.isEmpty()),
                    dataOrNoData(carriedOver, committed > 0 || !sprintRows.isEmpty()),
                    committed > 0
                            ? MetricValue.available(percentage(completed, committed), PERCENT_UNIT)
                            : MetricValue.noData(PERCENT_UNIT)));
        }
        points.sort((a, b) -> a.sprintId().compareTo(b.sprintId()));
        MetricValue average = sprintsWithScope > 0
                ? MetricValue.available(BigDecimal.valueOf(totalCompleted)
                        .divide(BigDecimal.valueOf(sprintsWithScope), 2, RoundingMode.HALF_UP), COUNT_UNIT)
                : MetricValue.noData(COUNT_UNIT);
        return new SprintVelocityResponse(meta, points, average);
    }

    // ------------------------------------------------------------------
    // 5. Sprint burndown
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public SprintBurndownResponse getSprintBurndown(AnalyticsQuery query, Long sprintId) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "sprint membership events + task status history (ideal line projected)");
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
        CustomUserDetails viewer = SecurityUtils.currentUser();
        if (!sprint.getProject().getOrganization().getId().equals(ctx.scope.organizationId())) {
            throw new UnauthorizedResourceAccessException("You do not have access to this sprint.");
        }
        projectSecurityEvaluator.assertCanViewProject(sprint.getProject().getId(), viewer);
        if (!ctx.projectIds().contains(sprint.getProject().getId())) {
            throw new UnauthorizedResourceAccessException("This sprint is outside the selected project filters.");
        }

        List<SprintTaskAnalyticsProjection> rows = sprintTaskRepository.findAnalyticsSprintTasks(
                Set.of(sprintId), ctx.applyUserFilter(), ctx.userIds());
        List<Long> taskIds = rows.stream().map(SprintTaskAnalyticsProjection::taskId).distinct().toList();
        List<StatusEventProjection> statusEvents = taskIds.isEmpty() ? List.of()
                : taskHistoryRepository.findAnalyticsTaskStatusEvents(
                        taskIds, startOfDayUtc(sprint.getStartDate()), endExclusive(sprint.getEndDate()));
        Map<LocalDate, Long> completionsByDay = statusEvents.stream()
                .filter(e -> "DONE".equals(e.newValue()))
                .collect(Collectors.groupingBy(
                        e -> e.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));

        long committedTotal = rows.stream().filter(r -> r.removedAt() == null).count();
        boolean hasHistory = !statusEvents.isEmpty() || rows.stream().anyMatch(r -> r.addedAt() != null);
        List<SprintBurndownResponse.BurndownPoint> points = new ArrayList<>();
        long days = java.time.temporal.ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) + 1;
        LocalDate cursor = sprint.getStartDate();
        while (!cursor.isAfter(sprint.getEndDate()) && points.size() < 120) {
            final LocalDate day = cursor;
            Instant dayEnd = endExclusive(day);
            long addedByDay = rows.stream().filter(r -> r.addedAt() != null && !r.addedAt().isAfter(dayEnd)).count();
            long removedByDay = rows.stream().filter(r -> r.removedAt() != null && !r.removedAt().isAfter(dayEnd)).count();
            long completedByDay = completionsByDay.entrySet().stream()
                    .filter(e -> !e.getKey().isAfter(day))
                    .mapToLong(Map.Entry::getValue).sum();
            long actual = addedByDay - removedByDay - completedByDay;

            BigDecimal ideal = committedTotal == 0 || days <= 1 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(committedTotal)
                            .multiply(BigDecimal.valueOf(days - 1 - java.time.temporal.ChronoUnit.DAYS
                                    .between(sprint.getStartDate(), day)))
                            .divide(BigDecimal.valueOf(days - 1), 2, RoundingMode.HALF_UP);
            MetricValue idealValue = committedTotal > 0
                    ? MetricValue.available(ideal, COUNT_UNIT)
                    : MetricValue.noData(COUNT_UNIT);
            MetricValue actualValue = hasHistory
                    ? MetricValue.available(BigDecimal.valueOf(Math.max(0, actual)), COUNT_UNIT)
                    : MetricValue.insufficientHistory(COUNT_UNIT);
            points.add(new SprintBurndownResponse.BurndownPoint(cursor, idealValue, actualValue));
            cursor = cursor.plusDays(1);
        }
        return new SprintBurndownResponse(meta, points,
                hasHistory ? DataAvailability.AVAILABLE : DataAvailability.INSUFFICIENT_HISTORY);
    }

    // ------------------------------------------------------------------
    // 6. Task completion trends
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public TaskCompletionTrendResponse getTaskCompletionTrends(AnalyticsQuery query) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "task_history status -> DONE events");
        CompletionResult completions = taskCompletions(ctx);
        List<TimeBucket> buckets = buildBuckets(ctx, completions.counts(), List.of("tasksCompleted"),
                !completions.events().isEmpty());
        MetricValue total = completions.total() == 0
                ? MetricValue.noData(COUNT_UNIT)
                : comparedMetric(completions.total(), completions.previous(), ctx);
        return new TaskCompletionTrendResponse(meta, buckets, total);
    }

    // ------------------------------------------------------------------
    // 7. Team productivity
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public TeamProductivityResponse getTeamProductivity(AnalyticsQuery query) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "team membership + task snapshots/completion events");
        List<Team> teams = query.teamIds().isEmpty()
                ? teamRepository.searchTeams(ctx.scope.organizationId(), null, null, Pageable.unpaged()).getContent()
                : teamRepository.findAllById(query.teamIds()).stream()
                        .filter(t -> t.getOrganization().getId().equals(ctx.scope.organizationId()))
                        .toList();
        if (teams.isEmpty()) {
            return new TeamProductivityResponse(meta, List.of());
        }
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, List<Long>> membersByTeam = new HashMap<>();
        for (TeamMemberProjection member : teamMemberRepository.findAnalyticsTeamMembers(teamIds)) {
            membersByTeam.computeIfAbsent(member.teamId(), k -> new ArrayList<>()).add(member.userId());
        }
        Set<Long> allUserIds = membersByTeam.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, Map<TaskStatus, Long>> tasksByUser = new HashMap<>();
        Map<Long, Long> overdueByUser = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            for (TaskStatusCountProjection row : taskRepository.countAnalyticsTasksByAssigneeAndStatus(
                    ctx.scope.organizationId(), allUserIds)) {
                tasksByUser.computeIfAbsent(row.key(), k -> new HashMap<>()).put(TaskStatus.valueOf(row.status()), row.count());
            }
            for (CountByKeyProjection row : taskRepository.countAnalyticsOverdueTasksByAssignee(
                    ctx.scope.organizationId(), allUserIds, LocalDate.now())) {
                overdueByUser.put(row.key(), row.count());
            }
        }

        List<TeamProductivityResponse.TeamProductivityRow> rows = new ArrayList<>();
        for (Team team : teams) {
            List<Long> memberIds = membersByTeam.getOrDefault(team.getId(), List.of());
            long memberCount = memberIds.stream().distinct().count();
            long total = 0, done = 0, active = 0, overdue = 0;
            for (Long userId : memberIds) {
                Map<TaskStatus, Long> byStatus = tasksByUser.getOrDefault(userId, Map.of());
                long userTotal = byStatus.values().stream().mapToLong(Long::longValue).sum();
                total += userTotal;
                done += byStatus.getOrDefault(TaskStatus.DONE, 0L);
                active += byStatus.getOrDefault(TaskStatus.IN_PROGRESS, 0L)
                        + byStatus.getOrDefault(TaskStatus.IN_REVIEW, 0L);
                overdue += overdueByUser.getOrDefault(userId, 0L);
            }
            rows.add(new TeamProductivityResponse.TeamProductivityRow(
                    team.getId(), team.getName(), memberCount,
                    dataOrNoData(done, total > 0),
                    dataOrNoData(active, total > 0),
                    dataOrNoData(overdue, total > 0),
                    total > 0 ? MetricValue.available(percentage(done, total), PERCENT_UNIT)
                            : MetricValue.noData(PERCENT_UNIT)));
        }
        rows.sort((a, b) -> a.teamId().compareTo(b.teamId()));
        return new TeamProductivityResponse(meta, rows);
    }

    // ------------------------------------------------------------------
    // 8. Employee workload
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public EmployeeWorkloadResponse getEmployeeWorkload(AnalyticsQuery query) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "current task assignments (no capacity configuration available)");
        List<User> users;
        if (ctx.userIds().isEmpty()) {
            users = userRepository.searchUsers(ctx.scope.organizationId(), null, null, Boolean.TRUE, Pageable.unpaged())
                    .getContent();
        } else {
            users = userRepository.findAllById(ctx.userIds()).stream()
                    .filter(u -> u.getOrganization().getId().equals(ctx.scope.organizationId()))
                    .toList();
        }
        if (users.isEmpty()) {
            return new EmployeeWorkloadResponse(meta, List.of(), DataAvailability.NO_DATA_IN_RANGE);
        }
        Map<Long, List<WorkloadProjection>> workloadRows = taskRepository.findAnalyticsWorkload(
                ctx.scope.organizationId(), users.stream().map(User::getId).toList()).stream()
                .collect(Collectors.groupingBy(WorkloadProjection::userId));

        List<EmployeeWorkloadResponse.EmployeeWorkloadRow> rows = new ArrayList<>();
        for (User user : users) {
            List<WorkloadProjection> userRows = workloadRows.getOrDefault(user.getId(), List.of());
            boolean hasWorkload = !userRows.isEmpty();
            long assigned = userRows.stream().mapToLong(WorkloadProjection::taskCount).sum();
            long active = userRows.stream()
                    .filter(w -> TaskStatus.IN_PROGRESS.name().equals(w.status())
                            || TaskStatus.IN_REVIEW.name().equals(w.status()))
                    .mapToLong(WorkloadProjection::taskCount).sum();
            BigDecimal hours = userRows.stream()
                    .map(w -> w.estimatedHours() == null ? BigDecimal.ZERO : w.estimatedHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            rows.add(new EmployeeWorkloadResponse.EmployeeWorkloadRow(
                    user.getId(), user.getFullName(), user.getPrimaryRoleName().name(),
                    dataOrNoData(assigned, hasWorkload),
                    dataOrNoData(active, hasWorkload),
                    MetricValue.available(hours, "hours"),
                    MetricValue.notConfigured("hours"),
                    MetricValue.notConfigured(PERCENT_UNIT)));
        }
        rows.sort((a, b) -> a.employeeId().compareTo(b.employeeId()));
        return new EmployeeWorkloadResponse(meta, rows, DataAvailability.NOT_CONFIGURED);
    }

    // ------------------------------------------------------------------
    // 9. Bug trends
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public BugTrendResponse getBugTrends(AnalyticsQuery query) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "bug created_at + bug_history status events");
        BugTrendData data = bugTrendData(ctx);
        List<TimeBucket> buckets = buildBuckets(ctx, data.counts(),
                List.of("bugsCreated", "bugsResolved", "bugsReopened"), data.anyData());
        long created = data.created().size();
        long reopened = data.reopened().size();
        MetricValue reopenRate = created > 0
                ? MetricValue.available(percentage(reopened, created), PERCENT_UNIT)
                : MetricValue.noData(PERCENT_UNIT);
        MetricValue mttr = data.mttrDays() == null ? MetricValue.noData(DAYS_UNIT)
                : MetricValue.available(data.mttrDays(), DAYS_UNIT);
        return new BugTrendResponse(meta, buckets, reopenRate, mttr);
    }

    // ------------------------------------------------------------------
    // 10. QA results
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public QaAnalyticsResponse getQaResults(AnalyticsQuery query) {
        Context ctx = resolve(query);
        AnalyticsMeta meta = meta(query, "test_executions results (NOT_RUN bucketed by run creation)");
        QaSummary summary = qaSummary(ctx);

        Map<String, MetricValue> results = new LinkedHashMap<>();
        for (TestExecutionResult result : TestExecutionResult.values()) {
            long current = summary.counts().getOrDefault(result, 0L);
            Long previous = summary.previousCounts().getOrDefault(result, null);
            results.put(result.name(), current == 0 && (ctx.compare() == null || previous == null || previous == 0)
                    ? MetricValue.noData(COUNT_UNIT)
                    : comparedMetric(current, ctx.compare() == null ? null : previous, ctx));
        }
        long executed = summary.passed() + summary.failed() + summary.blocked();
        MetricValue passRate = executed > 0 ? MetricValue.available(percentage(summary.passed(), executed), PERCENT_UNIT)
                : MetricValue.noData(PERCENT_UNIT);
        MetricValue defectLinkRate = summary.failed() > 0
                ? MetricValue.available(percentage(summary.defectLinked(), summary.failed()), PERCENT_UNIT)
                : MetricValue.noData(PERCENT_UNIT);

        List<TestRun> runs = testRunRepository.findAnalyticsQaRuns(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive());
        Map<Long, Map<TestExecutionResult, Long>> byRun = new HashMap<>();
        if (!runs.isEmpty()) {
            for (QaRunResultProjection row : testExecutionRepository.countAnalyticsQaResultsByRun(
                    runs.stream().map(TestRun::getId).toList(), ctx.applyUserFilter(), ctx.userIds())) {
                byRun.computeIfAbsent(row.runId(), k -> new HashMap<>())
                        .put(TestExecutionResult.valueOf(row.result()), row.count());
            }
        }
        List<QaAnalyticsResponse.QaRunRow> runRows = new ArrayList<>();
        for (TestRun run : runs) {
            Map<TestExecutionResult, Long> counts = byRun.getOrDefault(run.getId(), Map.of());
            runRows.add(new QaAnalyticsResponse.QaRunRow(
                    run.getId(), run.getName(), run.getStatus().name(),
                    dataOrNoData(counts.values().stream().mapToLong(Long::longValue).sum(), !counts.isEmpty()),
                    dataOrNoData(counts.getOrDefault(TestExecutionResult.PASS, 0L), !counts.isEmpty()),
                    dataOrNoData(counts.getOrDefault(TestExecutionResult.FAIL, 0L), !counts.isEmpty()),
                    dataOrNoData(counts.getOrDefault(TestExecutionResult.BLOCKED, 0L), !counts.isEmpty()),
                    dataOrNoData(counts.getOrDefault(TestExecutionResult.NOT_RUN, 0L), !counts.isEmpty())));
        }
        runRows.sort((a, b) -> a.runId().compareTo(b.runId()));
        return new QaAnalyticsResponse(meta, results, passRate, defectLinkRate, runRows);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private record Context(
            AnalyticsScope scope,
            Set<Long> projectIds,
            Set<Long> userIds,
            boolean applyUserFilter,
            LocalDate from,
            LocalDate to,
            Instant fromInstant,
            Instant toExclusive,
            AnalyticsGranularity granularity,
            CompareRange compare
    ) {
        boolean hasProjects() {
            return !projectIds().isEmpty() && !projectIds().contains(-1L);
        }
    }

    private record CompareRange(Instant fromInstant, Instant toExclusive, LocalDate from, LocalDate to) {
    }

    private record CompletionResult(
            List<TaskCompletionProjection> events,
            List<TaskCompletionProjection> previousEvents,
            Map<LocalDate, Map<String, Long>> counts,
            long total,
            Long previous
    ) {
    }

    private record BugTrendData(
            List<StatusEventProjection> created,
            List<BugResolutionProjection> resolved,
            List<StatusEventProjection> reopened,
            Map<LocalDate, Map<String, Long>> counts,
            boolean anyData,
            BigDecimal mttrDays
    ) {
    }

    private record QaSummary(
            Map<TestExecutionResult, Long> counts,
            Map<TestExecutionResult, Long> previousCounts,
            long totalExecutions,
            long passed,
            long failed,
            long blocked,
            long defectLinked
    ) {
    }

    private Context resolve(AnalyticsQuery query) {
        AnalyticsScope scope = authorizationService.resolveScope(query);
        Set<Long> projectIds = scope.allowedProjectIds().isEmpty() ? NO_IDS : Set.copyOf(scope.allowedProjectIds());

        Set<Long> userIds = new HashSet<>(query.employeeIds());
        if (!query.teamIds().isEmpty()) {
            userIds.addAll(teamMemberRepository.findAnalyticsUserIdsByTeamIds(query.teamIds()));
        }
        boolean hasUserFilters = !query.employeeIds().isEmpty() || !query.teamIds().isEmpty();
        boolean applyUserFilter = hasUserFilters;
        Set<Long> effectiveUserIds = userIds.isEmpty() && hasUserFilters ? NO_IDS : Set.copyOf(userIds);

        CompareRange compare = query.hasComparison()
                ? new CompareRange(startOfDayUtc(query.compareFrom()), endExclusive(query.compareTo()),
                        query.compareFrom(), query.compareTo())
                : null;
        return new Context(scope, projectIds, effectiveUserIds, applyUserFilter,
                query.from(), query.to(), startOfDayUtc(query.from()), endExclusive(query.to()),
                query.granularity(), compare);
    }

    private AnalyticsMeta meta(AnalyticsQuery query, String basis) {
        return AnalyticsMeta.of(query, basis);
    }

    private static Instant startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant endExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static MetricValue countMetric(long value) {
        return MetricValue.available(BigDecimal.valueOf(value), COUNT_UNIT);
    }

    private static MetricValue dataOrNoData(long value, boolean hasData) {
        return hasData ? countMetric(value) : MetricValue.noData(COUNT_UNIT);
    }

    private static MetricValue comparedMetric(long current, Long previous, Context ctx) {
        if (ctx.compare() == null) {
            return current == 0 ? MetricValue.noData(COUNT_UNIT) : countMetric(current);
        }
        if (previous == null) {
            return current == 0 ? MetricValue.noData(COUNT_UNIT) : MetricValue.insufficientHistory(COUNT_UNIT);
        }
        return MetricValue.compared(BigDecimal.valueOf(current), BigDecimal.valueOf(previous), COUNT_UNIT);
    }

    private static long countByStatus(List<TaskStatusCountProjection> rows, TaskStatus status) {
        return rows.stream().filter(r -> status.name().equals(r.status())).mapToLong(TaskStatusCountProjection::count).sum();
    }

    private boolean isOpenBugStatus(BugStatusCountProjection row) {
        BugStatus status = BugStatus.valueOf(row.status());
        return status == BugStatus.OPEN || status == BugStatus.ASSIGNED
                || status == BugStatus.IN_PROGRESS || status == BugStatus.REOPENED;
    }

    private static void mergeCounts(Map<LocalDate, Map<String, Long>> target,
                                    Map<LocalDate, Map<String, Long>> source, String key) {
        source.forEach((date, values) -> target.computeIfAbsent(date, d -> new HashMap<>())
                .put(key, values.getOrDefault(key, 0L)));
    }

    private void addCount(Map<LocalDate, Map<String, Long>> target, Instant instant, String key,
                          AnalyticsGranularity granularity) {
        if (instant == null) {
            return;
        }
        LocalDate bucket = bucketStart(instant, granularity);
        target.computeIfAbsent(bucket, d -> new HashMap<>()).merge(key, 1L, Long::sum);
    }

    private static LocalDate bucketStart(Instant instant, AnalyticsGranularity granularity) {
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        return granularity == AnalyticsGranularity.MONTH
                ? date.withDayOfMonth(1)
                : date.with(DayOfWeek.MONDAY);
    }

    private List<TimeBucket> buildBuckets(Context ctx, Map<LocalDate, Map<String, Long>> counts,
                                          List<String> keys, boolean anyData) {
        List<TimeBucket> buckets = new ArrayList<>();
        LocalDate cursor = bucketStart(ctx.fromInstant(), ctx.granularity());
        LocalDate last = bucketStart(ctx.to().atStartOfDay(ZoneOffset.UTC).toInstant(), ctx.granularity());
        int guard = 0;
        while (!cursor.isAfter(last) && guard++ < 120) {
            Map<String, MetricValue> series = new LinkedHashMap<>();
            for (String key : keys) {
                long value = counts.getOrDefault(cursor, Map.of()).getOrDefault(key, 0L);
                series.put(key, anyData ? countMetric(value) : MetricValue.noData(COUNT_UNIT));
            }
            LocalDate end = ctx.granularity() == AnalyticsGranularity.MONTH
                    ? cursor.withDayOfMonth(cursor.lengthOfMonth())
                    : cursor.plusDays(6);
            buckets.add(new TimeBucket(cursor, end, Collections.unmodifiableMap(series)));
            cursor = ctx.granularity() == AnalyticsGranularity.MONTH ? cursor.plusMonths(1) : cursor.plusWeeks(1);
        }
        return buckets;
    }

    private CompletionResult taskCompletions(Context ctx) {
        List<TaskCompletionProjection> events = taskHistoryRepository.findAnalyticsTaskCompletions(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive(),
                ctx.applyUserFilter(), ctx.userIds());
        List<TaskCompletionProjection> previous = ctx.compare() == null ? List.of()
                : taskHistoryRepository.findAnalyticsTaskCompletions(
                        ctx.scope.organizationId(), ctx.projectIds(), ctx.compare().fromInstant(),
                        ctx.compare().toExclusive(), ctx.applyUserFilter(), ctx.userIds());
        Map<LocalDate, Map<String, Long>> counts = new LinkedHashMap<>();
        for (TaskCompletionProjection event : events) {
            addCount(counts, event.occurredAt(), "tasksCompleted", ctx.granularity());
        }
        return new CompletionResult(events, previous, counts, events.size(),
                ctx.compare() == null ? null : (long) previous.size());
    }

    private BugTrendData bugTrendData(Context ctx) {
        List<StatusEventProjection> created = bugRepository.findAnalyticsBugCreations(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive(),
                ctx.applyUserFilter(), ctx.userIds());
        List<BugResolutionProjection> resolved = bugHistoryRepository.findAnalyticsBugResolutions(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive(),
                ctx.applyUserFilter(), ctx.userIds());
        List<StatusEventProjection> statusEvents = bugHistoryRepository.findAnalyticsBugStatusEvents(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive(),
                ctx.applyUserFilter(), ctx.userIds());
        List<StatusEventProjection> reopened = statusEvents.stream()
                .filter(e -> "REOPENED".equals(e.newValue())).toList();
        Map<LocalDate, Map<String, Long>> counts = new LinkedHashMap<>();
        for (StatusEventProjection event : created) {
            addCount(counts, event.occurredAt(), "bugsCreated", ctx.granularity());
        }
        for (BugResolutionProjection event : resolved) {
            addCount(counts, event.resolvedAt(), "bugsResolved", ctx.granularity());
        }
        for (StatusEventProjection event : reopened) {
            addCount(counts, event.occurredAt(), "bugsReopened", ctx.granularity());
        }
        BigDecimal mttr = resolved.isEmpty() ? null
                : resolved.stream()
                        .map(r -> BigDecimal.valueOf(java.time.Duration.between(r.createdAt(), r.resolvedAt()).toHours())
                                .divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(resolved.size()), 2, RoundingMode.HALF_UP);
        return new BugTrendData(created, resolved, reopened, counts,
                !created.isEmpty() || !resolved.isEmpty() || !reopened.isEmpty(), mttr);
    }

    private QaSummary qaSummary(Context ctx) {
        List<QaResultProjection> current = testExecutionRepository.findAnalyticsQaResults(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.fromInstant(), ctx.toExclusive(),
                ctx.applyUserFilter(), ctx.userIds());
        List<QaResultProjection> previous = ctx.compare() == null ? List.of()
                : testExecutionRepository.findAnalyticsQaResults(
                        ctx.scope.organizationId(), ctx.projectIds(), ctx.compare().fromInstant(),
                        ctx.compare().toExclusive(), ctx.applyUserFilter(), ctx.userIds());
        Map<TestExecutionResult, Long> counts = new LinkedHashMap<>();
        for (QaResultProjection row : current) {
            counts.merge(TestExecutionResult.valueOf(row.result()), 1L, Long::sum);
        }
        Map<TestExecutionResult, Long> previousCounts = new LinkedHashMap<>();
        for (QaResultProjection row : previous) {
            previousCounts.merge(TestExecutionResult.valueOf(row.result()), 1L, Long::sum);
        }
        long defectLinked = testExecutionRepository.countAnalyticsDefectLinkedFailures(
                ctx.scope.organizationId(), ctx.projectIds(), ctx.applyUserFilter(), ctx.userIds());
        return new QaSummary(counts, previousCounts, current.size(),
                counts.getOrDefault(TestExecutionResult.PASS, 0L),
                counts.getOrDefault(TestExecutionResult.FAIL, 0L),
                counts.getOrDefault(TestExecutionResult.BLOCKED, 0L),
                defectLinked);
    }

    private List<StatusEventProjection> projectCreatedEvents(Context ctx, CompareRange compare) {
        return projectRepository.findByOrganizationIdAndCreatedAtBetween(
                        ctx.scope.organizationId(), compare.fromInstant(), compare.toExclusive()).stream()
                .filter(p -> ctx.projectIds().contains(p.getId()))
                .map(p -> new StatusEventProjection(p.getId(), p.getCreatedAt(), null))
                .toList();
    }

    private List<StatusEventProjection> projectCompletionEvents(Context ctx, Instant from, Instant toExclusive) {
        List<AuditLog> logs = auditLogRepository.findAnalyticsProjectStatusChanges(
                ctx.scope.organizationId(), ctx.projectIds(), from, toExclusive);
        List<StatusEventProjection> events = new ArrayList<>();
        for (AuditLog log : logs) {
            Map<String, Object> newValue = log.getNewValue();
            if (newValue != null && "COMPLETED".equals(newValue.get("status")) && log.getProject() != null) {
                events.add(new StatusEventProjection(log.getProject().getId(), log.getCreatedAt(), "COMPLETED"));
            }
        }
        return events;
    }

    private static String effectiveStatus(SprintTaskAnalyticsProjection row, boolean snapshotEnd) {
        if (snapshotEnd && row.statusAtSprintEnd() != null) {
            return row.statusAtSprintEnd();
        }
        return row.currentStatus();
    }
}
