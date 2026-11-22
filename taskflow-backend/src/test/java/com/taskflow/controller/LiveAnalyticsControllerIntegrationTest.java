package com.taskflow.controller;

import com.taskflow.entity.AuditLog;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.RevokedToken;
import com.taskflow.entity.Role;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.service.LiveSubscriptionRegistry;
import com.taskflow.support.TestDatabaseCleaner;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full-stack verification of the live analytics backend foundation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveAnalyticsControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private RevokedTokenRepository revokedTokenRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LiveSubscriptionRegistry registry;

    private Organization orgA;
    private Organization orgB;
    private User adminA;
    private User devA;
    private User adminB;
    private Project projectA;
    private Project projectB;
    private String tokenAdminA;
    private String tokenDevA;
    private String tokenAdminB;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        registry.closeAll();

        orgA = organizationRepository.save(new Organization("Org A", "live-a", "Software", "UTC", "MON-FRI"));
        orgB = organizationRepository.save(new Organization("Org B", "live-b", "Finance", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));

        adminA = user(orgA, "ADM-A", "adminA@live.test", adminRole);
        devA = user(orgA, "DEV-A", "devA@live.test", devRole);
        adminB = user(orgB, "ADM-B", "adminB@live.test", adminRole);

        projectA = projectRepository.save(new Project(orgA, "LVA", "Project A", "desc", adminA,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(10)));
        projectA.setStatus(ProjectStatus.ACTIVE);
        projectRepository.save(projectA);
        projectB = projectRepository.save(new Project(orgA, "LVB", "Project B", "desc", adminA,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(10)));
        projectMemberRepository.save(new ProjectMember(projectA, devA, RoleName.DEVELOPER, adminA));

        task(projectA, "LVA-1", TaskStatus.DONE, adminA);
        task(projectA, "LVA-2", TaskStatus.IN_PROGRESS, devA);
        task(projectB, "LVB-1", TaskStatus.TODO, adminA);

        auditLogRepository.save(new AuditLog(orgA, adminA, projectA, AuditAction.TASK_CREATED,
                "TASK", 1L, null, null, null));
        auditLogRepository.save(new AuditLog(orgA, adminA, projectB, AuditAction.TASK_CREATED,
                "TASK", 2L, null, null, null));

        tokenAdminA = "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(adminA));
        tokenDevA = "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(devA));
        tokenAdminB = "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(adminB));
    }

    private User user(Organization org, String code, String email, Role role) {
        return userRepository.save(new User(org, code, email,
                passwordEncoder.encode("StrongPass123!"), email, "Test user", role));
    }

    private void task(Project project, String key, TaskStatus status, User assignee) {
        Task task = new Task(project, key, "Task " + key, "desc", PriorityLevel.HIGH,
                assignee, project.getManager(), null, null, BigDecimal.valueOf(2), LocalDate.now().plusDays(1));
        task.setStatus(status);
        taskRepository.save(task);
    }

    private static String query() {
        return "?from=" + LocalDate.now().minusDays(30)
                + "&to=" + LocalDate.now().plusDays(1)
                + "&granularity=WEEK";
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/live/analytics" + query()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/live/analytics/snapshot" + query()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sseStreamStartsWithReconnectHintAndInitialSnapshot() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/live/analytics" + query())
                        .header(HttpHeaders.AUTHORIZATION, tokenAdminA))
                .andExpect(request().asyncStarted())
                .andReturn();

        // The initial events are written before the controller returns, so no
        // asyncDispatch (which would wait for the emitter timeout) is needed.
        assertThat(result.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getContentAsString())
                .contains("retry:3000", "event:snapshot", "\"revision\"");
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void snapshotFallbackReturnsApprovedMetricsOnly() throws Exception {
        mockMvc.perform(get("/api/v1/live/analytics/snapshot" + query())
                        .header(HttpHeaders.AUTHORIZATION, tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").exists())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.taskKpis.totalTasks.value").value(3))
                .andExpect(jsonPath("$.taskKpis.completedTasks.value").value(1))
                .andExpect(jsonPath("$.taskKpis.inProgressTasks.value").value(1))
                .andExpect(jsonPath("$.taskKpis.overdueTasks.value").value(0))
                .andExpect(jsonPath("$.taskStatusDistribution.DONE").value(1))
                .andExpect(jsonPath("$.recentActivity.length()").value(2))
                .andExpect(jsonPath("$.completionTrend").isArray())
                .andExpect(jsonPath("$.tasksCompletedInRange").exists())
                .andExpect(jsonPath("$.employeeKpis").doesNotExist());
    }

    @Test
    void organizationIsolationIsEnforcedForSnapshots() throws Exception {
        mockMvc.perform(get("/api/v1/live/analytics/snapshot" + query())
                        .header(HttpHeaders.AUTHORIZATION, tokenAdminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskKpis.totalTasks.availability").value("NO_DATA_IN_RANGE"))
                .andExpect(jsonPath("$.taskKpis.totalTasks.value").doesNotExist())
                .andExpect(jsonPath("$.recentActivity.length()").value(0));
    }

    @Test
    void projectLevelIsolationIsEnforcedForSnapshotsAndActivity() throws Exception {
        mockMvc.perform(get("/api/v1/live/analytics/snapshot" + query())
                        .header(HttpHeaders.AUTHORIZATION, tokenDevA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskKpis.totalTasks.value").value(2))
                .andExpect(jsonPath("$.recentActivity.length()").value(1));
    }

    @Test
    void explicitForeignProjectFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/live/analytics/snapshot" + query() + "&projectIds=" + projectB.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenDevA))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidDateRangeReturnsSafeValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/live/analytics/snapshot?from=2026-12-31&to=2026-01-01")
                        .header(HttpHeaders.AUTHORIZATION, tokenAdminA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void emptyOrganizationProducesUnderstandableSnapshot() throws Exception {
        Organization empty = organizationRepository.save(
                new Organization("Empty", "live-empty", "IT", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        User admin = user(empty, "ADM-E", "empty@live.test", adminRole);
        String token = "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(admin));

        mockMvc.perform(get("/api/v1/live/analytics/snapshot" + query())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskKpis.totalTasks.availability").value("NO_DATA_IN_RANGE"))
                .andExpect(jsonPath("$.taskStatusDistribution").isEmpty())
                .andExpect(jsonPath("$.recentActivity.length()").value(0))
                .andExpect(jsonPath("$.revision").isNumber());
    }

    @Test
    void revokedOrDisabledViewerIsDisconnectedOnRevalidation() throws Exception {
        mockMvc.perform(get("/api/v1/live/analytics" + query())
                        .header(HttpHeaders.AUTHORIZATION, tokenDevA))
                .andExpect(request().asyncStarted());
        assertThat(registry.size()).isEqualTo(1);

        Claims claims = jwtService.parseAndValidateToken(tokenDevA.substring(7));
        revokedTokenRepository.save(new RevokedToken(claims.getId(), devA, Instant.now().plusSeconds(600)));
        registry.revalidateAll();

        assertThat(registry.size()).isZero();
    }

    @Test
    void logoutClosesAllConnectionsForThatUser() throws Exception {
        mockMvc.perform(get("/api/v1/live/analytics" + query())
                        .header(HttpHeaders.AUTHORIZATION, tokenDevA))
                .andExpect(request().asyncStarted());
        assertThat(registry.countForUser(devA.getId())).isEqualTo(1);

        registry.closeAllForUser(devA.getId());

        assertThat(registry.countForUser(devA.getId())).isZero();
    }
}
