package com.taskflow.service;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.BugTrendResponse;
import com.taskflow.dto.analytics.DataAvailability;
import com.taskflow.dto.analytics.EmployeeWorkloadResponse;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse;
import com.taskflow.dto.analytics.ProjectPerformanceReportResponse;
import com.taskflow.dto.analytics.QaAnalyticsResponse;
import com.taskflow.dto.analytics.SprintBurndownResponse;
import com.taskflow.dto.analytics.SprintVelocityResponse;
import com.taskflow.dto.analytics.TaskCompletionTrendResponse;
import com.taskflow.dto.analytics.TeamProductivityResponse;
import com.taskflow.entity.AuditLog;
import com.taskflow.entity.Bug;
import com.taskflow.entity.BugHistory;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.Sprint;
import com.taskflow.entity.SprintTask;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskHistory;
import com.taskflow.entity.Team;
import com.taskflow.entity.TeamMember;
import com.taskflow.entity.TestCase;
import com.taskflow.entity.TestExecution;
import com.taskflow.entity.TestRun;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.SprintStatus;
import com.taskflow.enums.TaskStatus;
import com.taskflow.enums.TestExecutionResult;
import com.taskflow.enums.TestRunStatus;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.BugHistoryRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.SprintTaskRepository;
import com.taskflow.repository.TaskHistoryRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.TestCaseRepository;
import com.taskflow.repository.TestExecutionRepository;
import com.taskflow.repository.TestRunRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceImplIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskHistoryRepository taskHistoryRepository;
    @Autowired private BugRepository bugRepository;
    @Autowired private BugHistoryRepository bugHistoryRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private SprintTaskRepository sprintTaskRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private TestRunRepository testRunRepository;
    @Autowired private TestExecutionRepository testExecutionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private AnalyticsService analyticsService;

    private final Instant now = Instant.now();
    private final LocalDate today = LocalDate.now();

    private Organization org;
    private Organization foreignOrg;
    private User admin;
    private User manager;
    private User developer;
    private User qa;
    private User foreignUser;
    private Role devRole;
    private Project projectOne;
    private Project projectTwo;
    private Project foreignProject;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        org = organizationRepository.save(new Organization("TaskFlow Enterprise", "analytics-org", "Software", "UTC", "MON-FRI"));
        foreignOrg = organizationRepository.save(new Organization("Other Org", "analytics-other", "Finance", "UTC", "MON-FRI"));

        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role pmRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        Role qaRole = roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));

        admin = saveUser("a@analytics.test", "EMP-A1", adminRole, org);
        manager = saveUser("m@analytics.test", "EMP-A2", pmRole, org);
        developer = saveUser("d@analytics.test", "EMP-A3", devRole, org);
        qa = saveUser("q@analytics.test", "EMP-A4", qaRole, org);
        foreignUser = saveUser("f@analytics.test", "EMP-F1", adminRole, foreignOrg);

        projectOne = projectRepository.save(new Project(org, "AN1", "Project One", "First", manager,
                today.minusMonths(3), today.plusMonths(3)));
        projectTwo = projectRepository.save(new Project(org, "AN2", "Project Two", "Second", manager,
                today.minusMonths(2), today.plusMonths(2)));
        foreignProject = projectRepository.save(new Project(foreignOrg, "FGN", "Foreign", "Other org", foreignUser,
                today.minusMonths(2), today.plusMonths(2)));
        for (Project baseProject : List.of(projectOne, projectTwo, foreignProject)) {
            jdbcTemplate.update("UPDATE projects SET created_at = ? WHERE id = ?",
                    Timestamp.from(now.minus(120, ChronoUnit.DAYS)), baseProject.getId());
        }

        projectMemberRepository.save(new ProjectMember(projectOne, developer, RoleName.DEVELOPER, admin));
        projectMemberRepository.save(new ProjectMember(projectOne, qa, RoleName.QA_TESTER, admin));

        authenticate(admin);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Executive overview + comparison
    // ------------------------------------------------------------------
    @Test
    void executiveOverviewAggregatesRealDataAndComparison() {
        Task done = saveTask(projectOne, "AN1-1", developer, TaskStatus.DONE, today.minusDays(5));
        saveTask(projectOne, "AN1-2", developer, TaskStatus.IN_PROGRESS, today.minusDays(1));
        saveTask(projectOne, "AN1-3", developer, TaskStatus.TODO, today.minusDays(10)); // overdue
        recordTaskCompletion(done, daysAgo(2));
        recordTaskCompletion(saveTask(projectOne, "AN1-4", developer, TaskStatus.DONE, today), daysAgo(40));

        Bug critical = saveBug(projectOne, "AN1-B1", BugSeverity.CRITICAL, BugStatus.OPEN, developer);
        assertThat(critical).isNotNull();
        TestRun run = saveRun(projectOne, "Run One");
        saveExecution(run, TestExecutionResult.PASS, daysAgo(3), null);
        saveExecution(run, TestExecutionResult.FAIL, daysAgo(2), null);

        AnalyticsQuery query = query(today.minusDays(30), today,
                today.minusDays(60), today.minusDays(31), List.of(), List.of(), List.of());
        ExecutiveOverviewResponse response = analyticsService.getExecutiveOverview(query);

        assertThat(response.kpis().get("totalTasks").value()).isEqualByComparingTo("4");
        assertThat(response.kpis().get("completedTasks").value()).isEqualByComparingTo("2");
        assertThat(response.kpis().get("overdueTasks").value()).isEqualByComparingTo("2");
        assertThat(response.kpis().get("openBugs").value()).isEqualByComparingTo("1");
        assertThat(response.kpis().get("criticalBugs").value()).isEqualByComparingTo("1");
        assertThat(response.kpis().get("tasksCompletedInRange").value()).isEqualByComparingTo("1");
        assertThat(response.kpis().get("tasksCompletedInRange").previousValue()).isEqualByComparingTo("1");
        assertThat(response.trend()).isNotEmpty();
        assertThat(response.attention()).extracting("type").contains("OVERDUE_TASKS", "CRITICAL_BUGS", "QA_FAILURES");
    }

    @Test
    void executiveOverviewExposesEnrichedScopedAggregations() {
        projectTwo.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(projectTwo);

        Task high = saveTask(projectOne, "AN1-H", developer, TaskStatus.TODO, today.plusDays(2));
        high.setPriority(PriorityLevel.HIGH);
        ReflectionTestUtils.setField(high, "createdAt", daysAgo(20));
        taskRepository.save(high);
        Task critical = saveTask(projectOne, "AN1-C", developer, TaskStatus.IN_PROGRESS, today.plusDays(3));
        critical.setPriority(PriorityLevel.CRITICAL);
        ReflectionTestUtils.setField(critical, "createdAt", daysAgo(5));
        taskRepository.save(critical);
        Task low = saveTask(projectOne, "AN1-L", qa, TaskStatus.TODO, today.plusDays(4));
        low.setPriority(PriorityLevel.LOW);
        ReflectionTestUtils.setField(low, "createdAt", daysAgo(3));
        taskRepository.save(low);
        Task completed = saveTask(projectOne, "AN1-D", developer, TaskStatus.DONE, today);
        recordTaskCompletion(completed, daysAgo(2));
        saveTask(projectOne, "AN1-O", developer, TaskStatus.TODO, today.minusDays(2));
        saveTask(foreignProject, "FGN-X", foreignUser, TaskStatus.TODO, today.plusDays(1));

        saveBug(projectOne, "AN1-BH", BugSeverity.HIGH, BugStatus.OPEN, developer);
        saveBug(projectOne, "AN1-BC", BugSeverity.CRITICAL, BugStatus.OPEN, qa);
        saveBug(foreignProject, "FGN-B", BugSeverity.LOW, BugStatus.OPEN, foreignUser);

        AuditLog audit = auditLog(projectOne);
        ReflectionTestUtils.setField(audit, "createdAt", daysAgo(4));
        auditLogRepository.save(audit);

        AnalyticsQuery query = query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of());
        ExecutiveOverviewResponse response = analyticsService.getExecutiveOverview(query);

        assertThat(response.projectStatusSummary().total()).isEqualTo(2);
        assertThat(response.projectStatusSummary().active()).isEqualTo(1);
        assertThat(response.projectStatusSummary().completed()).isEqualTo(1);
        assertThat(response.taskPriorityDistribution().counts())
                .containsKeys(PriorityLevel.HIGH, PriorityLevel.CRITICAL, PriorityLevel.LOW);
        assertThat(response.taskPriorityDistribution().availability()).isEqualTo(DataAvailability.AVAILABLE);
        assertThat(response.taskCreationTrend().availability()).isEqualTo(DataAvailability.AVAILABLE);
        assertThat(response.taskCreationTrend().buckets()).isNotEmpty();
        assertThat(response.employeeCompletionMetrics().rows()).isNotEmpty();
        assertThat(response.employeeCompletionMetrics().rows())
                .anySatisfy(row -> {
                    assertThat(row.employeeId()).isEqualTo(developer.getId());
                    assertThat(row.assigned()).isGreaterThanOrEqualTo(4);
                    assertThat(row.completed()).isGreaterThanOrEqualTo(1);
                    assertThat(row.overdue()).isGreaterThanOrEqualTo(1);
                });
        assertThat(response.auditActivityTrend().availability()).isEqualTo(DataAvailability.AVAILABLE);
        assertThat(response.auditActivityTrend().totalsByAction()).containsKey(AuditAction.PROJECT_STATUS_CHANGED);
        assertThat(response.bugSeverityDistribution().counts())
                .containsKeys(BugSeverity.HIGH, BugSeverity.CRITICAL)
                .doesNotContainKey(BugSeverity.LOW);
    }

    @Test
    void repeatedDoneTransitionsDoNotInflateEmployeeCompletionCounts() {
        Task task = saveTask(projectOne, "AN1-REP", developer, TaskStatus.DONE, today);
        recordTaskCompletion(task, daysAgo(3));
        recordTaskCompletion(task, daysAgo(2));

        ExecutiveOverviewResponse response = analyticsService.getExecutiveOverview(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));
        long totalCompleted = response.employeeCompletionMetrics().rows().stream()
                .mapToLong(ExecutiveOverviewResponse.EmployeeCompletionRow::completed).sum();
        assertThat(totalCompleted).isEqualTo(1);
    }

    @Test
    void unassignedCompletionIsNotCreditedToAnyEmployee() {
        Task task = saveTask(projectOne, "AN1-UA", developer, TaskStatus.DONE, today);
        task.setAssignee(null);
        taskRepository.save(task);
        recordTaskCompletion(task, daysAgo(2));

        ExecutiveOverviewResponse response = analyticsService.getExecutiveOverview(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));
        assertThat(response.employeeCompletionMetrics().rows().stream()
                .mapToLong(ExecutiveOverviewResponse.EmployeeCompletionRow::completed).sum()).isZero();
    }

    @Test
    void overdueIsEvaluatedAsOfReportEndDate() {
        Task task = saveTask(projectOne, "AN1-OD", developer, TaskStatus.TODO, today.minusDays(1));

        ExecutiveOverviewResponse asOfEarlierEnd = analyticsService.getExecutiveOverview(
                query(today.minusDays(30), today.minusDays(3), null, null, List.of(), List.of(), List.of()));
        assertThat(asOfEarlierEnd.employeeCompletionMetrics().rows().stream()
                .mapToLong(ExecutiveOverviewResponse.EmployeeCompletionRow::overdue).sum()).isZero();

        ExecutiveOverviewResponse asOfToday = analyticsService.getExecutiveOverview(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));
        assertThat(asOfToday.employeeCompletionMetrics().rows().stream()
                .mapToLong(ExecutiveOverviewResponse.EmployeeCompletionRow::overdue).sum()).isEqualTo(1);
    }

    @Test
    void taskCreationTrendAggregatesAcrossWeekAndMonthBoundariesInUtc() {
        Task sunday = saveTask(projectOne, "AN1-SUN", developer, TaskStatus.TODO, today.plusDays(1));
        ReflectionTestUtils.setField(sunday, "createdAt", Instant.parse("2026-11-15T23:59:59Z"));
        taskRepository.save(sunday);
        Task monday = saveTask(projectOne, "AN1-MON", developer, TaskStatus.TODO, today.plusDays(1));
        ReflectionTestUtils.setField(monday, "createdAt", Instant.parse("2026-11-16T00:00:01Z"));
        taskRepository.save(monday);

        ExecutiveOverviewResponse weekly = analyticsService.getExecutiveOverview(
                new AnalyticsQuery(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                        null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of()));
        long weeklyTotal = weekly.taskCreationTrend().buckets().stream()
                .flatMap(bucket -> bucket.series().values().stream())
                .filter(metric -> metric.value() != null)
                .mapToLong(metric -> metric.value().longValue())
                .sum();
        assertThat(weeklyTotal).isEqualTo(2);

        ExecutiveOverviewResponse monthly = analyticsService.getExecutiveOverview(
                new AnalyticsQuery(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                        null, null, AnalyticsGranularity.MONTH, List.of(), List.of(), List.of()));
        assertThat(monthly.taskCreationTrend().buckets()).hasSize(1);
    }

    @Test
    void completionAttributionUsesAssigneeAtDoneTransition() {
        Task task = saveTask(projectOne, "AN1-RA", developer, TaskStatus.IN_PROGRESS, today);
        task.setAssignee(qa);
        taskRepository.save(task);
        recordTaskCompletion(task, daysAgo(2));

        ExecutiveOverviewResponse response = analyticsService.getExecutiveOverview(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));
        assertThat(response.employeeCompletionMetrics().rows())
                .anySatisfy(row -> {
                    assertThat(row.employeeId()).isEqualTo(qa.getId());
                    assertThat(row.completed()).isEqualTo(1);
                })
                .anySatisfy(row -> {
                    assertThat(row.employeeId()).isEqualTo(developer.getId());
                    assertThat(row.completed()).isZero();
                });
    }

    @Test
    void legacyDoneHistoryWithoutAttributionIsExcludedFromEmployeeCounts() {
        Task task = saveTask(projectOne, "AN1-LEG", developer, TaskStatus.DONE, today);
        TaskHistory legacy = new TaskHistory(task, admin, "status", TaskStatus.IN_PROGRESS.name(),
                TaskStatus.DONE.name(), "legacy completion");
        ReflectionTestUtils.setField(legacy, "createdAt", daysAgo(2));
        taskHistoryRepository.save(legacy);

        ExecutiveOverviewResponse response = analyticsService.getExecutiveOverview(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));
        assertThat(response.employeeCompletionMetrics().rows().stream()
                .mapToLong(ExecutiveOverviewResponse.EmployeeCompletionRow::completed).sum()).isZero();
        assertThat(response.kpis().get("tasksCompletedInRange").value()).isEqualByComparingTo("1");
    }

    // ------------------------------------------------------------------
    // Task completion trends, boundaries and granularity
    // ------------------------------------------------------------------
    @Test
    void taskCompletionTrendsRespectBoundariesAndGranularity() {
        Task inside = saveTask(projectOne, "AN1-T1", developer, TaskStatus.DONE, today);
        Task before = saveTask(projectOne, "AN1-T2", developer, TaskStatus.DONE, today);
        recordTaskCompletion(inside, daysAgo(1));
        recordTaskCompletion(before, daysAgo(60));
        taskHistoryRepository.save(new TaskHistory(saveTask(projectOne, "AN1-T3", developer, TaskStatus.DONE, today),
                admin, "priority", "LOW", "HIGH", "not a status event"));

        TaskCompletionTrendResponse response = analyticsService.getTaskCompletionTrends(
                new AnalyticsQuery(today.minusDays(30), today, null, null,
                        AnalyticsGranularity.MONTH, List.of(), List.of(), List.of()));

        assertThat(response.totalCompleted().value()).isEqualByComparingTo("1");
        assertThat(response.totalCompleted().availability()).isEqualTo(DataAvailability.AVAILABLE);
        assertThat(response.buckets()).isNotEmpty();
        assertThat(response.buckets().stream()
                .mapToLong(b -> b.series().get("tasksCompleted").value().longValue()).sum()).isEqualTo(1);
    }

    @Test
    void comparisonPeriodIsReportedSeparatelyFromCurrentPeriod() {
        Task current = saveTask(projectOne, "AN1-C1", developer, TaskStatus.DONE, today);
        Task previous = saveTask(projectOne, "AN1-C2", developer, TaskStatus.DONE, today);
        recordTaskCompletion(current, daysAgo(2));
        recordTaskCompletion(previous, daysAgo(45));

        TaskCompletionTrendResponse response = analyticsService.getTaskCompletionTrends(
                query(today.minusDays(30), today, today.minusDays(60), today.minusDays(31),
                        List.of(), List.of(), List.of()));

        assertThat(response.totalCompleted().value()).isEqualByComparingTo("1");
        assertThat(response.totalCompleted().previousValue()).isEqualByComparingTo("1");
        assertThat(response.totalCompleted().deltaPercent()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------
    // Empty datasets
    // ------------------------------------------------------------------
    @Test
    void emptyDatasetIsReportedAsUnavailableNotZero() {
        Organization emptyOrg = organizationRepository.save(new Organization("Empty", "empty-org", "IT", "UTC", "MON-FRI"));
        User emptyUser = userRepository.save(new User(emptyOrg, "EMP-E1", "e@empty.test", "hash", "Empty User", "Dev", devRole));
        authenticate(emptyUser);

        AnalyticsQuery query = query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of());
        ExecutiveOverviewResponse overview = analyticsService.getExecutiveOverview(query);
        assertThat(overview.kpis().get("totalTasks").value()).isNull();
        assertThat(overview.kpis().get("totalTasks").availability()).isEqualTo(DataAvailability.NO_DATA_IN_RANGE);
        assertThat(overview.trend()).isNotEmpty();
        assertThat(overview.trend().getFirst().series().get("tasksCompleted").availability())
                .isEqualTo(DataAvailability.NO_DATA_IN_RANGE);
        assertThat(overview.projectStatusSummary().availability()).isEqualTo(DataAvailability.NO_DATA_IN_RANGE);
        assertThat(overview.taskPriorityDistribution().counts()).isEmpty();
        assertThat(overview.taskCreationTrend().availability()).isEqualTo(DataAvailability.NO_DATA_IN_RANGE);
        assertThat(overview.employeeCompletionMetrics().rows())
                .allSatisfy(row -> assertThat(row.assigned()).isZero());
        assertThat(overview.auditActivityTrend().availability()).isEqualTo(DataAvailability.NO_DATA_IN_RANGE);
        assertThat(overview.bugSeverityDistribution().counts()).isEmpty();

        assertThat(analyticsService.getProjectPerformance(query).rows()).isEmpty();
        assertThat(analyticsService.getSprintVelocity(query).averageVelocity().availability())
                .isEqualTo(DataAvailability.NO_DATA_IN_RANGE);
        assertThat(analyticsService.getTaskCompletionTrends(query).totalCompleted().availability())
                .isEqualTo(DataAvailability.NO_DATA_IN_RANGE);
    }

    // ------------------------------------------------------------------
    // Organization isolation + project visibility + filters
    // ------------------------------------------------------------------
    @Test
    void organizationIsolationAndProjectVisibilityAreEnforced() {
        authenticate(developer); // member of projectOne only

        AnalyticsQuery query = query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of());
        ExecutiveOverviewResponse ownOverview = analyticsService.getExecutiveOverview(query);
        assertThat(ownOverview.projectStatusSummary().total()).isEqualTo(1);
        ProjectPerformanceReportResponse own = analyticsService.getProjectPerformance(query);
        assertThat(own.rows()).extracting(ProjectPerformanceReportResponse.ProjectPerformanceRow::projectId)
                .containsExactly(projectOne.getId());

        assertThatThrownBy(() -> analyticsService.getProjectPerformance(
                query(today.minusDays(30), today, null, null,
                        List.of(projectOne.getId(), foreignProject.getId()), List.of(), List.of())))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void teamAndEmployeeFiltersRestrictAggregations() {
        Team team = teamRepository.save(new Team(org, "Team A", "First team", developer));
        teamMemberRepository.save(new TeamMember(team, developer));

        Task teamTask = saveTask(projectOne, "AN1-F1", developer, TaskStatus.DONE, today);
        recordTaskCompletion(teamTask, daysAgo(1));
        Task otherTask = saveTask(projectOne, "AN1-F2", qa, TaskStatus.DONE, today);
        recordTaskCompletion(otherTask, daysAgo(1));

        TaskCompletionTrendResponse byTeam = analyticsService.getTaskCompletionTrends(
                query(today.minusDays(30), today, null, null, List.of(), List.of(team.getId()), List.of()));
        assertThat(byTeam.totalCompleted().value()).isEqualByComparingTo("1");

        TaskCompletionTrendResponse byEmployee = analyticsService.getTaskCompletionTrends(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of(qa.getId())));
        assertThat(byEmployee.totalCompleted().value()).isEqualByComparingTo("1");

        ExecutiveOverviewResponse byTeamOverview = analyticsService.getExecutiveOverview(
                query(today.minusDays(30), today, null, null, List.of(), List.of(team.getId()), List.of()));
        assertThat(byTeamOverview.employeeCompletionMetrics().rows())
                .extracting("employeeId")
                .contains(developer.getId())
                .doesNotContain(qa.getId());
    }

    // ------------------------------------------------------------------
    // Project performance and growth
    // ------------------------------------------------------------------
    @Test
    void projectPerformanceUsesSnapshotsAndCompletionHistory() {
        Task done = saveTask(projectOne, "AN1-P1", developer, TaskStatus.DONE, today);
        recordTaskCompletion(done, daysAgo(2));
        saveTask(projectTwo, "AN2-P1", developer, TaskStatus.IN_PROGRESS, today.minusDays(5));
        saveBug(projectOne, "AN1-B2", BugSeverity.HIGH, BugStatus.IN_PROGRESS, developer);

        ProjectPerformanceReportResponse response = analyticsService.getProjectPerformance(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));

        assertThat(response.rows()).hasSize(2);
        ProjectPerformanceReportResponse.ProjectPerformanceRow rowOne = response.rows().stream()
                .filter(r -> r.projectId().equals(projectOne.getId())).findFirst().orElseThrow();
        assertThat(rowOne.totalTasks()).isEqualTo(1);
        assertThat(rowOne.completedTasks()).isEqualTo(1);
        assertThat(rowOne.progressPercent().value()).isEqualByComparingTo("100");
        assertThat(rowOne.velocity().value()).isEqualByComparingTo("1");
        assertThat(rowOne.openBugs()).isEqualTo(1);
        assertThat(response.summary().completionRate().value()).isEqualByComparingTo("50");
    }

    @Test
    void projectGrowthUsesCreatedAtAndStatusAuditEvents() {
        Project createdInRange = projectRepository.save(new Project(org, "AN3", "Project Three", "Third", manager,
                today.minusMonths(1), today.plusMonths(1)));
        jdbcTemplate.update("UPDATE projects SET created_at = ? WHERE id = ?",
                Timestamp.from(daysAgo(3)), createdInRange.getId());
        jdbcTemplate.update("UPDATE audit_logs SET created_at = ? WHERE id = ?",
                Timestamp.from(daysAgo(1)), saveCompletionAudit(createdInRange).getId());

        var response = analyticsService.getProjectGrowth(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));

        assertThat(response.buckets()).isNotEmpty();
        long created = response.buckets().stream()
                .mapToLong(b -> b.series().get("projectsCreated").value().longValue()).sum();
        long completed = response.buckets().stream()
                .mapToLong(b -> b.series().get("projectsCompleted").value().longValue()).sum();
        assertThat(created).isEqualTo(1);
        assertThat(completed).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Sprint velocity and burndown
    // ------------------------------------------------------------------
    @Test
    void sprintVelocityAndBurndownUseMembershipAndStatusHistory() {
        Sprint sprint = sprintRepository.save(new Sprint(projectOne, "Sprint A", "Goal",
                today.minusDays(5), today.minusDays(1), manager));
        sprint.setStatus(SprintStatus.ACTIVE);
        Task one = saveTask(projectOne, "AN1-S1", developer, TaskStatus.DONE, today);
        Task two = saveTask(projectOne, "AN1-S2", developer, TaskStatus.IN_PROGRESS, today);
        sprintTaskRepository.save(new SprintTask(sprint, one, manager));
        sprintTaskRepository.save(new SprintTask(sprint, two, manager));
        recordTaskCompletion(one, daysAgo(3));

        SprintVelocityResponse velocity = analyticsService.getSprintVelocity(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));
        assertThat(velocity.points()).hasSize(1);
        assertThat(velocity.points().getFirst().committed().value()).isEqualByComparingTo("2");
        assertThat(velocity.points().getFirst().completed().value()).isEqualByComparingTo("1");
        assertThat(velocity.averageVelocity().value()).isEqualByComparingTo("1");

        SprintBurndownResponse burndown = analyticsService.getSprintBurndown(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()), sprint.getId());
        assertThat(burndown.points()).hasSize(5);
        assertThat(burndown.points().getFirst().actualRemaining().value()).isNotNull();
        assertThat(burndown.actualAvailability()).isEqualTo(DataAvailability.AVAILABLE);
    }

    @Test
    void burndownWithoutHistoryReportsInsufficientHistory() {
        Sprint sprint = sprintRepository.save(new Sprint(projectOne, "Empty Sprint", "Goal",
                today.minusDays(2), today, manager));
        SprintBurndownResponse response = analyticsService.getSprintBurndown(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()), sprint.getId());
        assertThat(response.points()).isNotEmpty();
        assertThat(response.points().getFirst().actualRemaining().value()).isNull();
        assertThat(response.points().getFirst().actualRemaining().availability())
                .isEqualTo(DataAvailability.INSUFFICIENT_HISTORY);
    }

    // ------------------------------------------------------------------
    // Bug trends (creation, resolution, reopening, MTTR)
    // ------------------------------------------------------------------
    @Test
    void bugTrendsCountCreationResolutionReopeningAndMttr() {
        Bug bug = saveBug(projectOne, "AN1-B3", BugSeverity.HIGH, BugStatus.RESOLVED, developer);
        jdbcTemplate.update("UPDATE bugs SET created_at = ? WHERE id = ?",
                Timestamp.from(daysAgo(10)), bug.getId());
        recordBugStatus(bug, "RESOLVED", daysAgo(4));
        recordBugStatus(bug, "REOPENED", daysAgo(2));

        BugTrendResponse response = analyticsService.getBugTrends(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));

        long created = response.buckets().stream()
                .mapToLong(b -> b.series().get("bugsCreated").value().longValue()).sum();
        long resolved = response.buckets().stream()
                .mapToLong(b -> b.series().get("bugsResolved").value().longValue()).sum();
        long reopened = response.buckets().stream()
                .mapToLong(b -> b.series().get("bugsReopened").value().longValue()).sum();
        assertThat(created).isEqualTo(1);
        assertThat(resolved).isEqualTo(1);
        assertThat(reopened).isEqualTo(1);
        assertThat(response.reopenRate().value()).isEqualByComparingTo("100");
        assertThat(response.mttrDays().value()).isEqualByComparingTo("6.00");
    }

    // ------------------------------------------------------------------
    // QA pass/fail analytics
    // ------------------------------------------------------------------
    @Test
    void qaAnalyticsCountsAllResultsAndDefectLinks() {
        TestRun run = saveRun(projectOne, "QA Run Analytics");
        TestCase testCase = testCaseRepository.save(new TestCase(org, projectOne, "ACME-TC-QA", "Case", qa));
        Bug linkedBug = saveBug(projectOne, "AN1-B4", BugSeverity.MEDIUM, BugStatus.OPEN, developer);

        saveExecution(run, TestExecutionResult.PASS, daysAgo(2), null);
        saveExecution(run, TestExecutionResult.FAIL, daysAgo(2), linkedBug);
        saveExecution(run, TestExecutionResult.BLOCKED, daysAgo(2), null);
        TestExecution notRun = new TestExecution(run, testCase);
        notRun.setResult(TestExecutionResult.NOT_RUN);
        testExecutionRepository.save(notRun);

        QaAnalyticsResponse response = analyticsService.getQaResults(
                query(today.minusDays(30), today, null, null, List.of(), List.of(), List.of()));

        assertThat(response.results().get("PASS").value()).isEqualByComparingTo("1");
        assertThat(response.results().get("FAIL").value()).isEqualByComparingTo("1");
        assertThat(response.results().get("BLOCKED").value()).isEqualByComparingTo("1");
        assertThat(response.results().get("NOT_RUN").value()).isEqualByComparingTo("1");
        assertThat(response.passRate().value()).isEqualByComparingTo("33.33");
        assertThat(response.defectLinkRate().value()).isEqualByComparingTo("100");
        assertThat(response.runs()).hasSize(1);
    }

    @Test
    void employeeWorkloadUsesCurrentAssignmentsWithoutFabricatedCapacity() {
        saveTask(projectOne, "AN1-W1", developer, TaskStatus.IN_PROGRESS, today);
        saveTask(projectOne, "AN1-W2", developer, TaskStatus.TODO, today);

        EmployeeWorkloadResponse response = analyticsService.getEmployeeWorkload(
                query(today.minusDays(30), today, null, null,
                        List.of(), List.of(), List.of(developer.getId())));

        assertThat(response.rows()).hasSize(1);
        EmployeeWorkloadResponse.EmployeeWorkloadRow row = response.rows().getFirst();
        assertThat(row.assignedTasks().value()).isEqualByComparingTo("2");
        assertThat(row.activeTasks().value()).isEqualByComparingTo("1");
        assertThat(row.capacityHours().value()).isNull();
        assertThat(row.capacityHours().availability()).isEqualTo(DataAvailability.NOT_CONFIGURED);
        assertThat(response.capacityBasis()).isEqualTo(DataAvailability.NOT_CONFIGURED);
    }

    @Test
    void teamProductivityUsesMembershipAndTaskSnapshots() {
        Team team = teamRepository.save(new Team(org, "Productivity Team", "Team", manager));
        teamMemberRepository.save(new TeamMember(team, developer));
        saveTask(projectOne, "AN1-TP1", developer, TaskStatus.DONE, today);
        saveTask(projectOne, "AN1-TP2", developer, TaskStatus.IN_PROGRESS, today);

        TeamProductivityResponse response = analyticsService.getTeamProductivity(
                query(today.minusDays(30), today, null, null, List.of(), List.of(team.getId()), List.of()));

        assertThat(response.rows()).hasSize(1);
        TeamProductivityResponse.TeamProductivityRow row = response.rows().getFirst();
        assertThat(row.memberCount()).isEqualTo(1);
        assertThat(row.completedTasks().value()).isEqualByComparingTo("1");
        assertThat(row.activeTasks().value()).isEqualByComparingTo("1");
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------
    private Instant daysAgo(long days) {
        return now.minus(days, ChronoUnit.DAYS);
    }

    private User saveUser(String email, String code, Role role, Organization organization) {
        return userRepository.save(new User(organization, code, email, "hash", email, "Tester", role));
    }

    private Project saveProject(Organization organization, String key, User managerUser) {
        return projectRepository.save(new Project(organization, key, key, "desc", managerUser,
                today.minusMonths(1), today.plusMonths(1)));
    }

    private Task saveTask(Project project, String key, User assignee, TaskStatus status, LocalDate dueDate) {
        Task task = new Task(project, key, key, "desc", PriorityLevel.MEDIUM, assignee, manager,
                null, null, BigDecimal.valueOf(4), dueDate);
        task.setStatus(status);
        return taskRepository.save(task);
    }

    private void recordTaskCompletion(Task task, Instant occurredAt) {
        TaskHistory history = new TaskHistory(task, admin, task.getAssignee(), "status",
                TaskStatus.IN_PROGRESS.name(), TaskStatus.DONE.name(), "completed");
        ReflectionTestUtils.setField(history, "createdAt", occurredAt);
        taskHistoryRepository.save(history);
    }

    private Bug saveBug(Project project, String key, BugSeverity severity, BugStatus status, User assignee) {
        Bug bug = new Bug(project, key, null, key, "desc", "steps", severity, PriorityLevel.MEDIUM, qa, assignee);
        bug.setStatus(status);
        return bugRepository.save(bug);
    }

    private void recordBugStatus(Bug bug, String newStatus, Instant occurredAt) {
        BugHistory history = new BugHistory(bug, admin, "status", bug.getStatus().name(), newStatus, "test");
        ReflectionTestUtils.setField(history, "createdAt", occurredAt);
        bugHistoryRepository.save(history);
    }

    private TestRun saveRun(Project project, String name) {
        TestRun run = new TestRun(org, project, name, qa);
        run.setStatus(TestRunStatus.COMPLETED);
        return testRunRepository.save(run);
    }

    private void saveExecution(TestRun run, TestExecutionResult result, Instant executedAt, Bug bug) {
        TestExecution execution = new TestExecution(run, testCaseRepository.save(new TestCase(
                org, run.getProject(), "TC-" + run.getId() + "-" + result.name() + "-" + executedAt.getNano(),
                "case", qa)));
        execution.setResult(result);
        execution.setExecutedBy(qa);
        execution.setExecutedAt(executedAt);
        execution.setBug(bug);
        testExecutionRepository.save(execution);
    }

    private AuditLog saveCompletionAudit(Project project) {
        return auditLog(project);
    }

    private AuditLog auditLog(Project project) {
        return auditLogRepository.save(new AuditLog(org, admin, project,
                AuditAction.PROJECT_STATUS_CHANGED, "PROJECT", project.getId(),
                Map.of("status", ProjectStatus.ACTIVE.name()),
                Map.of("status", ProjectStatus.COMPLETED.name()), null));
    }

    private AnalyticsQuery query(LocalDate from, LocalDate to, LocalDate compareFrom, LocalDate compareTo,
                                 List<Long> projectIds, List<Long> teamIds, List<Long> employeeIds) {
        return new AnalyticsQuery(from, to, compareFrom, compareTo, AnalyticsGranularity.WEEK,
                projectIds, teamIds, employeeIds);
    }

    private void authenticate(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
