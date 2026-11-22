package com.taskflow.config;

import com.taskflow.entity.Bug;
import com.taskflow.entity.BugComment;
import com.taskflow.entity.BugHistory;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Sprint;
import com.taskflow.entity.Team;
import com.taskflow.entity.TeamMember;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskHistory;
import com.taskflow.entity.TestCase;
import com.taskflow.entity.TestExecution;
import com.taskflow.entity.TestRun;
import com.taskflow.entity.TestSuite;
import com.taskflow.entity.TestSuiteCase;
import com.taskflow.entity.TestStep;
import com.taskflow.entity.User;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.NotificationType;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.SprintStatus;
import com.taskflow.enums.TaskStatus;
import com.taskflow.enums.TestExecutionResult;
import com.taskflow.enums.TestRunStatus;
import com.taskflow.repository.BugCommentRepository;
import com.taskflow.repository.BugHistoryRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.TaskHistoryRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.TestCaseRepository;
import com.taskflow.repository.TestExecutionRepository;
import com.taskflow.repository.TestRunRepository;
import com.taskflow.repository.TestSuiteCaseRepository;
import com.taskflow.repository.TestSuiteRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.service.NotificationDispatcher;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds realistic demo data (projects, tasks, bugs, QA runs) for the development
 * environment only.
 *
 * <p>Runs only under the {@code dev} profile, after {@link DevEmployeeSeeder}, and is
 * idempotent: the whole demo dataset is skipped when the primary demo project already
 * exists for the organization, so restarts never create duplicates. The run is
 * transactional, therefore a partial failure rolls back the entire demo dataset.</p>
 */
@Component
@Profile("dev")
@Order(200)
public class DevDemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDemoDataSeeder.class);

    private static final String DEMO_PROJECT_KEY = "ACME";

    private record SeedAccount(String employeeCode, String email, String fullName, String jobTitle, RoleName role) {
    }

    private static final List<SeedAccount> SEED_ACCOUNTS = List.of(
            new SeedAccount("EMP-0002", "manager@taskflow.com", "Priya Menon", "Project Manager", RoleName.PROJECT_MANAGER),
            new SeedAccount("EMP-0003", "developer1@taskflow.com", "Arjun Rao", "Software Engineer", RoleName.DEVELOPER),
            new SeedAccount("EMP-0004", "developer2@taskflow.com", "Sneha Kulkarni", "Software Engineer", RoleName.DEVELOPER),
            new SeedAccount("EMP-0005", "qa@taskflow.com", "Rahul Verma", "QA Engineer", RoleName.QA_TESTER),
            new SeedAccount("EMP-0006", "developer3@taskflow.com", "Divya Nair", "Software Engineer", RoleName.DEVELOPER)
    );

    private final DevDemoDataSeedProperties properties;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final BugRepository bugRepository;
    private final BugHistoryRepository bugHistoryRepository;
    private final BugCommentRepository bugCommentRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final TestSuiteCaseRepository testSuiteCaseRepository;
    private final TestRunRepository testRunRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final EntityManager entityManager;

    public DevDemoDataSeeder(
            DevDemoDataSeedProperties properties,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            SprintRepository sprintRepository,
            TaskRepository taskRepository,
            TaskHistoryRepository taskHistoryRepository,
            BugRepository bugRepository,
            BugHistoryRepository bugHistoryRepository,
            BugCommentRepository bugCommentRepository,
            NotificationDispatcher notificationDispatcher,
            TestCaseRepository testCaseRepository,
            TestSuiteRepository testSuiteRepository,
            TestSuiteCaseRepository testSuiteCaseRepository,
            TestRunRepository testRunRepository,
            TestExecutionRepository testExecutionRepository,
            EntityManager entityManager
    ) {
        this.properties = properties;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.bugRepository = bugRepository;
        this.bugHistoryRepository = bugHistoryRepository;
        this.bugCommentRepository = bugCommentRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.testCaseRepository = testCaseRepository;
        this.testSuiteRepository = testSuiteRepository;
        this.testSuiteCaseRepository = testSuiteCaseRepository;
        this.testRunRepository = testRunRepository;
        this.testExecutionRepository = testExecutionRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Dev demo data seeding is disabled (taskflow.demo-seed.enabled=false).");
            return;
        }

        Organization organization = organizationRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Default organization missing. Verify Flyway migrations."));

        Map<String, User> usersByEmail = new HashMap<>();
        List<String> missingAccounts = new ArrayList<>();
        for (SeedAccount account : SEED_ACCOUNTS) {
            User user = userRepository.findByEmailIgnoreCase(account.email()).orElse(null);
            if (user == null || !user.isActive()) {
                missingAccounts.add(account.email());
                continue;
            }
            usersByEmail.put(account.email(), user);
        }
        if (!missingAccounts.isEmpty()) {
            log.warn("Dev demo data seeding skipped: missing prerequisite accounts {} for organization {}.",
                    missingAccounts, organization.getName());
            return;
        }

        List<User> admins = userRepository.searchUsers(
                organization.getId(), null, RoleName.ADMIN, Boolean.TRUE, PageRequest.of(0, 1)).getContent();
        if (admins.isEmpty()) {
            log.warn("Dev demo data seeding skipped: no active ADMIN account for organization {}.",
                    organization.getName());
            return;
        }
        User admin = admins.getFirst();
        User manager = usersByEmail.get("manager@taskflow.com");
        User dev1 = usersByEmail.get("developer1@taskflow.com");
        User dev2 = usersByEmail.get("developer2@taskflow.com");
        User qa = usersByEmail.get("qa@taskflow.com");
        User dev3 = usersByEmail.get("developer3@taskflow.com");

        if (projectRepository.existsByOrganizationIdAndProjectKeyIgnoreCase(organization.getId(), DEMO_PROJECT_KEY)) {
            log.info("Dev demo data already present for organization {} (project key {}); skipping.",
                    organization.getName(), DEMO_PROJECT_KEY);
            return;
        }

        // ------------------------------------------------------------------
        // Projects
        // ------------------------------------------------------------------
        Project acme = new Project(
                organization,
                DEMO_PROJECT_KEY,
                "Acme Cloud Platform",
                "Multi-tenant SaaS platform for project tracking, bug reporting and QA.",
                manager,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 3, 31)
        );
        acme.setStatus(ProjectStatus.ACTIVE);
        projectRepository.save(acme);
        log.info("Seeded demo project {} ({})", acme.getProjectKey(), acme.getName());

        Project northwind = new Project(
                organization,
                "NORTHWIND",
                "Northwind Logistics",
                "Dispatch and shipment tracking workspace.",
                dev3,
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 6, 30)
        );
        northwind.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(northwind);
        log.info("Seeded demo project {} ({})", northwind.getProjectKey(), northwind.getName());

        projectMemberRepository.save(new ProjectMember(acme, manager, RoleName.PROJECT_MANAGER, admin));
        projectMemberRepository.save(new ProjectMember(acme, dev1, RoleName.DEVELOPER, admin));
        projectMemberRepository.save(new ProjectMember(acme, dev2, RoleName.DEVELOPER, admin));
        projectMemberRepository.save(new ProjectMember(acme, qa, RoleName.QA_TESTER, admin));
        projectMemberRepository.save(new ProjectMember(northwind, dev3, RoleName.DEVELOPER, admin));

        // ------------------------------------------------------------------
        // Teams
        // ------------------------------------------------------------------
        Team platformTeam = new Team(
                organization,
                "Platform Engineering",
                "Core platform, infrastructure and tooling squad.",
                dev1
        );
        teamRepository.save(platformTeam);
        teamMemberRepository.save(new TeamMember(platformTeam, dev1));
        teamMemberRepository.save(new TeamMember(platformTeam, dev2));

        Team qaTeam = new Team(
                organization,
                "QA Guild",
                "Test strategy, execution and quality advocacy squad.",
                qa
        );
        teamRepository.save(qaTeam);
        teamMemberRepository.save(new TeamMember(qaTeam, qa));

        // ------------------------------------------------------------------
        // Sprint + tasks
        // ------------------------------------------------------------------
        Sprint sprint = new Sprint(
                acme,
                "Sprint 1",
                "Foundation delivery and core feature work.",
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 27),
                manager
        );
        sprint.setStatus(SprintStatus.ACTIVE);
        sprint.setStartedAt(Instant.parse("2026-11-02T09:00:00Z"));
        sprintRepository.save(sprint);

        Task task101 = new Task(
                acme,
                acme.nextTaskKey(),
                "Implement login rate limiting",
                "Add per-IP and per-account login rate limiting with exponential backoff.",
                PriorityLevel.HIGH,
                dev1,
                manager,
                sprint,
                null,
                BigDecimal.valueOf(8),
                LocalDate.of(2026, 9, 30)
        );
        task101.setStatus(TaskStatus.DONE);
        Task savedTask101 = taskRepository.save(task101);
        taskHistoryRepository.save(new TaskHistory(savedTask101, dev1, "CREATED", null, TaskStatus.TODO.name(), "Task " + savedTask101.getTaskKey() + " created"));
        taskHistoryRepository.save(new TaskHistory(savedTask101, dev1, dev1, "STATUS", TaskStatus.TODO.name(), TaskStatus.DONE.name(), "Rate limiting policy shipped"));

        Task task102 = new Task(
                acme,
                acme.nextTaskKey(),
                "Design notification preferences API",
                "Design public API for user notification preferences.",
                PriorityLevel.MEDIUM,
                dev2,
                manager,
                sprint,
                null,
                BigDecimal.valueOf(6),
                LocalDate.of(2026, 11, 27)
        );
        task102.setStatus(TaskStatus.IN_PROGRESS);
        Task savedTask102 = taskRepository.save(task102);
        taskHistoryRepository.save(new TaskHistory(savedTask102, manager, "CREATED", null, TaskStatus.TODO.name(), "Task " + savedTask102.getTaskKey() + " created"));
        taskHistoryRepository.save(new TaskHistory(savedTask102, manager, "STATUS", TaskStatus.TODO.name(), TaskStatus.IN_PROGRESS.name(), "API design started"));

        Task task103 = new Task(
                acme,
                acme.nextTaskKey(),
                "Add team activity timeline",
                "Render organization-wide activity feed for projects and teams.",
                PriorityLevel.HIGH,
                manager,
                dev1,
                sprint,
                null,
                BigDecimal.valueOf(12),
                LocalDate.of(2026, 12, 4)
        );
        task103.setStatus(TaskStatus.IN_REVIEW);
        Task savedTask103 = taskRepository.save(task103);
        taskHistoryRepository.save(new TaskHistory(savedTask103, dev1, "CREATED", null, TaskStatus.TODO.name(), "Task " + savedTask103.getTaskKey() + " created"));

        Task task104 = new Task(
                acme,
                acme.nextTaskKey(),
                "Fix CSV export encoding",
                "Ensure CSV export handles unicode characters correctly.",
                PriorityLevel.LOW,
                dev1,
                manager,
                sprint,
                null,
                BigDecimal.valueOf(2),
                LocalDate.of(2026, 12, 18)
        );
        task104.setStatus(TaskStatus.TODO);
        Task savedTask104 = taskRepository.save(task104);
        taskHistoryRepository.save(new TaskHistory(savedTask104, manager, "CREATED", null, TaskStatus.TODO.name(), "Task " + savedTask104.getTaskKey() + " created"));

        Task task105 = new Task(
                acme,
                acme.nextTaskKey(),
                "Migrate attachment storage to S3",
                "Move local attachment storage to an S3-compatible store.",
                PriorityLevel.CRITICAL,
                dev2,
                manager,
                sprint,
                null,
                BigDecimal.valueOf(16),
                LocalDate.of(2026, 10, 28)
        );
        task105.setStatus(TaskStatus.BLOCKED);
        Task savedTask105 = taskRepository.save(task105);
        taskHistoryRepository.save(new TaskHistory(savedTask105, manager, "CREATED", null, TaskStatus.TODO.name(), "Task " + savedTask105.getTaskKey() + " created"));
        taskHistoryRepository.save(new TaskHistory(savedTask105, dev2, "STATUS", TaskStatus.TODO.name(), TaskStatus.BLOCKED.name(), "Blocked awaiting storage credentials"));

        // ------------------------------------------------------------------
        // Bugs
        // ------------------------------------------------------------------
        Bug bug101 = new Bug(
                acme,
                acme.nextBugKey(),
                savedTask101,
                "Login endpoint returns 500 under high concurrency",
                "Login requests fail with HTTP 500 once concurrent sessions exceed the configured limit.",
                "1. Enable per-IP and per-account rate limiting.\n2. Verify retry behaviour with exponential backoff.\n3. Check logs for upstream timeout errors.",
                BugSeverity.CRITICAL,
                PriorityLevel.HIGH,
                manager,
                dev1
        );
        Bug savedBug101 = bugRepository.save(bug101);
        bugHistoryRepository.save(new BugHistory(savedBug101, manager, "REPORTED", null, savedBug101.getStatus().name(), "Bug " + savedBug101.getBugKey() + " reported"));

        Bug bug102 = new Bug(
                acme,
                acme.nextBugKey(),
                savedTask102,
                "Notification preference changes not persisted",
                "Saving notification preferences appears to succeed but resets on reload.",
                "1. Toggle a notification preference.\n2. Save preferences.\n3. Reload the page and re-check the toggle.",
                BugSeverity.HIGH,
                PriorityLevel.MEDIUM,
                qa,
                dev2
        );
        Bug savedBug102 = bugRepository.save(bug102);
        bugHistoryRepository.save(new BugHistory(savedBug102, qa, "REPORTED", null, savedBug102.getStatus().name(), "Bug " + savedBug102.getBugKey() + " reported"));
        savedBug102.setStatus(BugStatus.IN_PROGRESS);
        bugHistoryRepository.save(new BugHistory(savedBug102, dev2, "STATUS", BugStatus.ASSIGNED.name(), BugStatus.IN_PROGRESS.name(), "Investigating persistence layer"));
        bugRepository.save(savedBug102);

        Bug bug103 = new Bug(
                acme,
                acme.nextBugKey(),
                savedTask103,
                "Activity timeline shows duplicate events",
                "The activity feed renders the same event twice for sprint updates.",
                "1. Open project activity feed.\n2. Trigger a sprint status change.\n3. Observe duplicated entries.",
                BugSeverity.MEDIUM,
                PriorityLevel.LOW,
                manager,
                dev1
        );
        Bug savedBug103 = bugRepository.save(bug103);
        savedBug103.setStatus(BugStatus.RESOLVED);
        bugHistoryRepository.save(new BugHistory(savedBug103, manager, "REPORTED", null, savedBug103.getStatus().name(), "Bug " + savedBug103.getBugKey() + " reported"));
        bugHistoryRepository.save(new BugHistory(savedBug103, dev1, "STATUS", BugStatus.ASSIGNED.name(), BugStatus.RESOLVED.name(), "Fixed by deduplicating activity events"));
        bugRepository.save(savedBug103);

        bugCommentRepository.save(new BugComment(savedBug102, dev2, "Reproduced with the preference toggle enabled; state resets on reload."));

        // ------------------------------------------------------------------
        // Notifications
        // ------------------------------------------------------------------
        notificationDispatcher.notifyUser(
                dev2,
                NotificationType.TASK_ASSIGNED,
                "New task assigned: " + savedTask102.getTaskKey(),
                manager.getFullName() + " assigned you to '" + savedTask102.getTitle() + "'.",
                "TASK",
                savedTask102.getId()
        );
        notificationDispatcher.notifyUser(
                dev2,
                NotificationType.TASK_OVERDUE,
                "Task overdue: " + savedTask105.getTaskKey(),
                "Task '" + savedTask105.getTitle() + "' is past its due date and is not completed.",
                "TASK",
                savedTask105.getId()
        );
        notificationDispatcher.notifyUser(
                dev1,
                NotificationType.BUG_ASSIGNED,
                "Bug assigned: " + savedBug101.getBugKey(),
                manager.getFullName() + " assigned you bug '" + savedBug101.getTitle() + "'.",
                "BUG",
                savedBug101.getId()
        );
        notificationDispatcher.notifyUser(
                dev2,
                NotificationType.BUG_ASSIGNED,
                "Bug assigned: " + savedBug102.getBugKey(),
                qa.getFullName() + " assigned you bug '" + savedBug102.getTitle() + "'.",
                "BUG",
                savedBug102.getId()
        );

        // ------------------------------------------------------------------
        // QA: test cases, suite, runs and executions
        // ------------------------------------------------------------------
        TestCase testCase001 = new TestCase(organization, acme, "ACME-TC-001", "Login rate limiting rejects excess requests", qa);
        testCase001.setDescription("Verify login rate limiting responds with 429 when limits are exceeded.");
        testCase001.setPreconditions("Rate limiting policy enabled.");
        testCase001.setPriority(PriorityLevel.HIGH);
        TestCase savedTestCase001 = testCaseRepository.save(testCase001);
        entityManager.persist(new TestStep(savedTestCase001, 1, "Send 30 login requests with valid credentials in quick succession", "API responds with 429 Too Many Requests"));
        entityManager.persist(new TestStep(savedTestCase001, 2, "Retry login after backoff window with valid credentials", "Login succeeds once backoff window elapses"));

        TestCase testCase002 = new TestCase(organization, acme, "ACME-TC-002", "Authenticated session token expires after TTL", qa);
        testCase002.setDescription("Verify access tokens stop working after the configured expiry.");
        testCase002.setPreconditions("JWT TTL configured.");
        testCase002.setPriority(PriorityLevel.MEDIUM);
        TestCase savedTestCase002 = testCaseRepository.save(testCase002);
        entityManager.persist(new TestStep(savedTestCase002, 1, "Login and capture access token", "Token contains exp claim within configured TTL."));
        entityManager.persist(new TestStep(savedTestCase002, 2, "Wait for token expiry and call protected endpoint", "Protected endpoint returns 401 after expiry."));

        TestCase testCase003 = new TestCase(organization, acme, "ACME-TC-003", "Password reset flow completes end-to-end", qa);
        testCase003.setDescription("Verify the full password reset lifecycle from request to login.");
        testCase003.setPreconditions("Mail transport configured.");
        testCase003.setPriority(PriorityLevel.LOW);
        TestCase savedTestCase003 = testCaseRepository.save(testCase003);
        entityManager.persist(new TestStep(savedTestCase003, 1, "Request password reset for a seeded user", "Reset email delivered."));
        entityManager.persist(new TestStep(savedTestCase003, 2, "Reset password and login with new credentials", "Login succeeds with new password."));

        TestSuite suite = new TestSuite(organization, acme, "ACME Regression Suite", qa);
        suite.setDescription("Automated regression coverage for the Acme platform.");
        TestSuite savedSuite = testSuiteRepository.save(suite);

        addSuiteCase(testSuiteCaseRepository, savedSuite, savedTestCase001);
        addSuiteCase(testSuiteCaseRepository, savedSuite, savedTestCase002);
        addSuiteCase(testSuiteCaseRepository, savedSuite, savedTestCase003);

        TestRun smokeRun = new TestRun(organization, acme, "ACME Smoke Run", qa);
        smokeRun.setSuite(savedSuite);
        smokeRun.setAssignedTo(qa);
        smokeRun.setStatus(TestRunStatus.COMPLETED);
        smokeRun.setStartedAt(Instant.parse("2026-11-10T09:00:00Z"));
        smokeRun.setCompletedAt(Instant.parse("2026-11-10T09:45:00Z"));
        TestRun savedSmokeRun = testRunRepository.save(smokeRun);

        TestExecution execution001 = new TestExecution(savedSmokeRun, savedTestCase001);
        execution001.setResult(TestExecutionResult.FAIL);
        execution001.setBug(savedBug101);
        execution001.setActualResult("Expected HTTP 429 with Retry-After header; received HTTP 500.");
        execution001.setNotes("Smoke run detected concurrency failure.");
        execution001.setExecutedBy(qa);
        execution001.setExecutedAt(Instant.parse("2026-11-10T09:40:00Z"));
        testExecutionRepository.save(execution001);

        TestExecution execution002 = new TestExecution(savedSmokeRun, savedTestCase002);
        execution002.setResult(TestExecutionResult.PASS);
        execution002.setActualResult("Token expiry claim verified.");
        execution002.setExecutedBy(qa);
        execution002.setExecutedAt(Instant.parse("2026-11-10T09:44:00Z"));
        testExecutionRepository.save(execution002);

        TestExecution execution003 = new TestExecution(savedSmokeRun, savedTestCase003);
        execution003.setResult(TestExecutionResult.BLOCKED);
        execution003.setNotes("Blocked pending SMTP provider credentials.");
        execution003.setExecutedBy(qa);
        execution003.setExecutedAt(Instant.parse("2026-11-10T09:45:00Z"));
        testExecutionRepository.save(execution003);

        TestRun fullRun = new TestRun(organization, acme, "ACME Full Regression", qa);
        fullRun.setSuite(suite);
        fullRun.setAssignedTo(qa);
        fullRun.setStatus(TestRunStatus.PLANNED);
        TestRun savedFullRun = testRunRepository.save(fullRun);

        TestExecution plannedExecution = new TestExecution(savedFullRun, savedTestCase002);
        plannedExecution.setResult(TestExecutionResult.NOT_RUN);
        testExecutionRepository.save(plannedExecution);

        log.info("Dev demo data seeding complete for organization {} (project {}).", organization.getName(), acme.getProjectKey());
    }

    private void addSuiteCase(TestSuiteCaseRepository testSuiteCaseRepository, TestSuite suite, TestCase testCase) {
        if (testSuiteCaseRepository.existsById(new TestSuiteCase.PK(suite.getId(), testCase.getId()))) {
            return;
        }
        testSuiteCaseRepository.save(new TestSuiteCase(suite, testCase));
    }
}
