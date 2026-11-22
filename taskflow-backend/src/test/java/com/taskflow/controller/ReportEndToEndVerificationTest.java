package com.taskflow.controller;

import com.taskflow.dto.analytics.AnalyticsGranularity;
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
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.BugHistoryRepository;
import com.taskflow.repository.BugRepository;
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
import com.taskflow.security.JwtService;
import com.taskflow.service.AnalyticsReportService;
import com.taskflow.dto.report.AnalyticsReportRequest;
import com.taskflow.dto.report.ReportFormat;
import com.taskflow.dto.report.ReportType;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.support.TestDatabaseCleaner;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full-stack Group 4 verification: real auth, analytics service and both renderers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportEndToEndVerificationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskHistoryRepository taskHistoryRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private SprintTaskRepository sprintTaskRepository;
    @Autowired private BugRepository bugRepository;
    @Autowired private BugHistoryRepository bugHistoryRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private TestRunRepository testRunRepository;
    @Autowired private TestExecutionRepository testExecutionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AnalyticsReportService reportService;

    private final LocalDate today = LocalDate.now();
    private final Instant now = Instant.now();

    private Organization organization;
    private User admin;
    private User developer;
    private Role adminRole;
    private Project project;
    private Sprint sprint;
    private Team team;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);

        organization = organizationRepository.save(new Organization(
                "TaskFlow Enterprise", "e2e-reports", "Software", "UTC", "MON-FRI"));
        Role adminRole = this.adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role managerRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        Role qaRole = roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));

        admin = saveUser("e2e-admin@taskflow.test", "E2E-0001", adminRole);
        User manager = saveUser("e2e-pm@taskflow.test", "E2E-0002", managerRole);
        developer = saveUser("e2e-dev@taskflow.test", "E2E-0003", devRole);
        User qa = saveUser("e2e-qa@taskflow.test", "E2E-0004", qaRole);

        project = projectRepository.save(new Project(organization, "E2E", "E2E Project", "desc",
                manager, today.minusMonths(2), today.plusMonths(2)));
        project.setStatus(ProjectStatus.ACTIVE);
        projectRepository.save(project);
        projectMemberRepository.save(new ProjectMember(project, developer, RoleName.DEVELOPER, admin));
        projectMemberRepository.save(new ProjectMember(project, qa, RoleName.QA_TESTER, admin));

        team = teamRepository.save(new Team(organization, "E2E Team", "desc", developer));
        teamMemberRepository.save(new TeamMember(team, developer));

        Task task = taskRepository.save(new Task(project, "E2E-1", "Completed task", "desc",
                PriorityLevel.HIGH, developer, manager, null, null,
                BigDecimal.valueOf(4), today.minusDays(1)));
        task.setStatus(TaskStatus.DONE);
        taskRepository.save(task);
        TaskHistory completion = new TaskHistory(task, manager, "status",
                TaskStatus.IN_PROGRESS.name(), TaskStatus.DONE.name(), "done");
        ReflectionTestUtils.setField(completion, "createdAt", now.minus(2, java.time.temporal.ChronoUnit.DAYS));
        taskHistoryRepository.save(completion);

        bugRepository.save(new Bug(project, "E2E-B1", task, "Critical bug", "desc", "steps",
                BugSeverity.CRITICAL, PriorityLevel.HIGH, qa, developer));
        BugHistory resolved = new BugHistory(bugRepository.findAll().getFirst(), manager, "status",
                BugStatus.IN_PROGRESS.name(), BugStatus.RESOLVED.name(), "fixed");
        ReflectionTestUtils.setField(resolved, "createdAt", now.minus(3, java.time.temporal.ChronoUnit.DAYS));
        bugHistoryRepository.save(resolved);

        sprint = sprintRepository.save(new Sprint(project, "E2E Sprint", "goal",
                today.minusDays(5), today.plusDays(5), manager));
        sprint.setStatus(SprintStatus.ACTIVE);
        sprintRepository.save(sprint);
        sprintTaskRepository.save(new SprintTask(sprint, task, manager));

        TestCase testCase = testCaseRepository.save(new TestCase(organization, project,
                "E2E-TC-1", "Login test", qa));
        TestRun run = testRunRepository.save(new TestRun(organization, project, "E2E Run", qa));
        run.setStatus(TestRunStatus.COMPLETED);
        testRunRepository.save(run);
        TestExecution execution = new TestExecution(run, testCase);
        execution.setResult(TestExecutionResult.PASS);
        execution.setExecutedBy(qa);
        execution.setExecutedAt(now.minus(1, java.time.temporal.ChronoUnit.DAYS));
        testExecutionRepository.save(execution);

        auditLogRepository.save(new AuditLog(organization, admin, project,
                AuditAction.PROJECT_STATUS_CHANGED, "PROJECT", project.getId(),
                Map.of("status", ProjectStatus.ACTIVE.name()),
                Map.of("status", ProjectStatus.COMPLETED.name()), null));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User saveUser(String email, String code, Role role) {
        return userRepository.save(new User(organization, code, email,
                passwordEncoder.encode("StrongPass123!"), email, "Test user", role));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }

    private static String requestJson(ReportType type, ReportFormat format, Long sprintId) {
        return """
                {"type":"%s","format":"%s",
                 "query":{"from":"%s","to":"%s","granularity":"WEEK",
                          "projectIds":[],"teamIds":[],"employeeIds":[]},
                 "sprintId":%s}
                """.formatted(type.name(), format.name(),
                LocalDate.now().minusDays(30), LocalDate.now().plusDays(1),
                sprintId == null ? "null" : sprintId.toString());
    }

    @Test
    void everyReportTypeProducesValidPdfAndXlsxThroughTheFullStack() throws Exception {
        for (ReportType type : ReportType.values()) {
            Long sprintId = type == ReportType.SPRINT_BURNOWN ? sprint.getId() : null;

            byte[] pdf = mockMvc.perform(post("/api/v1/reports/pdf")
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson(type, ReportFormat.PDF, sprintId)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                    .andReturn().getResponse().getContentAsByteArray();

            assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                    .as("PDF signature for " + type).isEqualTo("%PDF-");
            try (PDDocument document = Loader.loadPDF(pdf)) {
                assertThat(document.getNumberOfPages()).as("PDF pages for " + type).isGreaterThanOrEqualTo(1);
                if (type == ReportType.EXECUTIVE_OVERVIEW) {
                    String text = new PDFTextStripper().getText(document);
                    assertThat(text).contains("Project Status", "Task Analytics", "Team Productivity",
                            "Activity Trends", "Quality Analytics", "Executive Insights");
                }
            }

            byte[] xlsx = mockMvc.perform(post("/api/v1/reports/xlsx")
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson(type, ReportFormat.XLSX, sprintId)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                    .andReturn().getResponse().getContentAsByteArray();

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
                assertThat(workbook.getNumberOfSheets()).as("XLSX sheets for " + type)
                        .isGreaterThanOrEqualTo(1);
                assertThat(workbook.getSheet("Executive Summary")).as("Summary sheet for " + type)
                        .isNotNull();
                if (type == ReportType.EXECUTIVE_OVERVIEW) {
                    assertThat(workbook.getSheet("Project Status")).isNotNull();
                    assertThat(workbook.getSheet("Task Analytics")).isNotNull();
                    assertThat(workbook.getSheet("Team Productivity")).isNotNull();
                    assertThat(workbook.getSheet("Activity Trends")).isNotNull();
                    assertThat(workbook.getSheet("Quality Analytics")).isNotNull();
                    assertThat(workbook.getSheet("Executive Insights")).isNotNull();
                }
            }
        }
    }

    @Test
    void catalogIdentifiersMatchEnumsAndSprintSprintIdRuleIsCorrect() throws Exception {
        String body = mockMvc.perform(get("/api/v1/reports/catalog")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportTypes.length()").value(10))
                .andExpect(jsonPath("$.formats.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        for (ReportType type : ReportType.values()) {
            assertThat(body).contains("\"id\":\"" + type.name() + "\"")
                    .contains("\"label\":\"" + type.label() + "\"");
        }
        for (ReportFormat format : ReportFormat.values()) {
            assertThat(body).contains("\"id\":\"" + format.name() + "\"");
        }
        assertThat(body).contains("\"id\":\"SPRINT_BURNOWN\",\"label\":\"Sprint Burndown\"");
        assertThat(body).doesNotContain("SPRINT_BURNDOWN");
    }

    @Test
    void filtersAndComparisonArePreservedByRealService() {
        CustomUserDetails principal = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        AnalyticsQueryLike query = new AnalyticsQueryLike();
        AnalyticsReportRequest request = new AnalyticsReportRequest(
                ReportType.EXECUTIVE_OVERVIEW, ReportFormat.PDF, query.value(), null);

        AnalyticsReportData data = reportService.buildReport(request);

        assertThat(data.metadata().compared()).isTrue();
        assertThat(data.metadata().appliedFilters()).contains(
                "Projects: 1 selected", "Teams: 1 selected", "Employees: 1 selected",
                "Comparison: " + query.value().compareFrom() + " to " + query.value().compareTo());
        assertThat(data.metadata().organizationName()).isEqualTo("TaskFlow Enterprise");
    }

    @Test
    void emptyDatasetIsExplainedRatherThanShownAsZero() throws Exception {
        Organization emptyOrg = organizationRepository.save(new Organization(
                "Empty Org", "empty-e2e", "IT", "UTC", "MON-FRI"));
        User emptyAdmin = userRepository.save(new User(emptyOrg, "E2E-9001", "empty-e2e@taskflow.test",
                passwordEncoder.encode("StrongPass123!"), "Empty Admin", "Admin", adminRole));

        byte[] pdf = mockMvc.perform(post("/api/v1/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(emptyAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(ReportType.EXECUTIVE_OVERVIEW, ReportFormat.PDF, null)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }

        byte[] xlsx = mockMvc.perform(post("/api/v1/reports/xlsx")
                        .header(HttpHeaders.AUTHORIZATION, bearer(emptyAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(ReportType.EXECUTIVE_OVERVIEW, ReportFormat.XLSX, null)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getSheet("Executive Summary")).isNotNull();
        }
    }

    @Test
    void crossOrganizationProjectFilterIsForbiddenThroughApi() throws Exception {
        Organization other = organizationRepository.save(new Organization(
                "Other Org", "other-e2e", "Finance", "UTC", "MON-FRI"));
        Project foreign = projectRepository.save(new Project(other, "FRN", "Foreign", "desc",
                admin, today.minusMonths(1), today.plusMonths(1)));

        String json = """
                {"type":"PROJECT_PERFORMANCE","format":"PDF",
                 "query":{"from":"%s","to":"%s","granularity":"WEEK",
                          "projectIds":[%d],"teamIds":[],"employeeIds":[]}}
                """.formatted(today.minusDays(30), today.plusDays(1), foreign.getId());

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    private final class AnalyticsQueryLike {
        private com.taskflow.dto.analytics.AnalyticsQuery value() {
            return new com.taskflow.dto.analytics.AnalyticsQuery(
                    today.minusDays(30), today.plusDays(1),
                    today.minusDays(60), today.minusDays(31),
                    AnalyticsGranularity.WEEK,
                    java.util.List.of(project.getId()),
                    java.util.List.of(team.getId()),
                    java.util.List.of(developer.getId()));
        }
    }
}
