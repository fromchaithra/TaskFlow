package com.taskflow.controller;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.TaskHistoryRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.support.TestDatabaseCleaner;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private TaskHistoryRepository taskHistoryRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RevokedTokenRepository revokedTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Organization orgAlpha;
    private Organization orgBeta;
    private User adminAlpha;
    private User pmAlpha;
    private User devOneAlpha;
    private User devTwoAlpha;
    private User outsiderDevAlpha;
    private User qaAlpha;
    private User adminBeta;
    private Project alphaProject;
    private Project betaProject;
    private Task devOneTodoTask;
    private Task devOneReviewTask;
    private Task devTwoProgressTask;
    private Task betaTask;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);

        orgAlpha = organizationRepository.save(
                new Organization("Alpha Corp", "alpha-corp", "Software", "UTC", "MON,TUE,WED,THU,FRI")
        );
        orgBeta = organizationRepository.save(
                new Organization("Beta Corp", "beta-corp", "FinTech", "UTC", "MON,TUE,WED,THU,FRI")
        );

        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role pmRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "Project Manager"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        Role qaRole = roleRepository.save(new Role(RoleName.QA_TESTER, "QA Tester"));

        String hash = passwordEncoder.encode("StrongPass123!");
        adminAlpha = userRepository.save(new User(orgAlpha, "EMP-A1", "admin@alpha.test", hash, "Alice Admin", "Admin", adminRole));
        pmAlpha = userRepository.save(new User(orgAlpha, "EMP-A2", "pm@alpha.test", hash, "Mark PM", "Manager", pmRole));
        devOneAlpha = userRepository.save(new User(orgAlpha, "EMP-A3", "dev1@alpha.test", hash, "Dana Dev", "Engineer", devRole));
        devTwoAlpha = userRepository.save(new User(orgAlpha, "EMP-A4", "dev2@alpha.test", hash, "Devon Dev", "Engineer", devRole));
        outsiderDevAlpha = userRepository.save(new User(orgAlpha, "EMP-A5", "outsider@alpha.test", hash, "Oscar Outsider", "Engineer", devRole));
        qaAlpha = userRepository.save(new User(orgAlpha, "EMP-A6", "qa@alpha.test", hash, "Quinn QA", "QA Engineer", qaRole));
        adminBeta = userRepository.save(new User(orgBeta, "EMP-B1", "admin@beta.test", hash, "Bob Beta Admin", "Admin", adminRole));

        alphaProject = projectRepository.save(new Project(
                orgAlpha,
                "ALPHA",
                "Alpha Core",
                "Core delivery workspace",
                pmAlpha,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        ));
        projectMemberRepository.save(new ProjectMember(alphaProject, pmAlpha, RoleName.PROJECT_MANAGER, adminAlpha));
        projectMemberRepository.save(new ProjectMember(alphaProject, devOneAlpha, RoleName.DEVELOPER, pmAlpha));
        projectMemberRepository.save(new ProjectMember(alphaProject, devTwoAlpha, RoleName.DEVELOPER, pmAlpha));
        projectMemberRepository.save(new ProjectMember(alphaProject, qaAlpha, RoleName.QA_TESTER, pmAlpha));

        betaProject = projectRepository.save(new Project(
                orgBeta,
                "BETA",
                "Beta Ledger",
                "Isolated organization project",
                adminBeta,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        ));
        projectMemberRepository.save(new ProjectMember(betaProject, adminBeta, RoleName.PROJECT_MANAGER, adminBeta));

        devOneTodoTask = taskRepository.save(new Task(
                alphaProject, alphaProject.nextTaskKey(), "Implement OAuth Guard", "Add token validation",
                PriorityLevel.HIGH, devOneAlpha, pmAlpha, null, null, new BigDecimal("6.00"), LocalDate.of(2026, 5, 20)
        ));

        Task reviewTask = new Task(
                alphaProject, alphaProject.nextTaskKey(), "Build Audit Export", "Export JSON audit log",
                PriorityLevel.MEDIUM, devOneAlpha, pmAlpha, null, null, new BigDecimal("4.00"), LocalDate.of(2026, 5, 25)
        );
        reviewTask.setStatus(TaskStatus.IN_REVIEW);
        devOneReviewTask = taskRepository.save(reviewTask);

        Task progressTask = new Task(
                alphaProject, alphaProject.nextTaskKey(), "Optimize SQL Indexes", "Add composite index",
                PriorityLevel.CRITICAL, devTwoAlpha, pmAlpha, null, null, new BigDecimal("8.00"), LocalDate.of(2026, 6, 1)
        );
        progressTask.setStatus(TaskStatus.IN_PROGRESS);
        devTwoProgressTask = taskRepository.save(progressTask);
        projectRepository.save(alphaProject);

        betaTask = taskRepository.save(new Task(
                betaProject, betaProject.nextTaskKey(), "Beta Settlement Sync", "Org B internal task",
                PriorityLevel.HIGH, adminBeta, adminBeta, null, null, new BigDecimal("5.00"), LocalDate.of(2026, 6, 10)
        ));
        projectRepository.save(betaProject);
    }

    @Test
    void adminAndProjectManagerCanCreateTasks_AndAuditAndHistoryAreRecorded() throws Exception {
        String adminBody = """
                {
                  "projectId": %d,
                  "title": "Configure Aiven MySQL TLS",
                  "description": "Enforce SSL mode REQUIRED for production DB",
                  "priority": "CRITICAL",
                  "assigneeId": %d,
                  "estimatedHours": 3.5,
                  "dueDate": "2026-07-01"
                }
                """.formatted(alphaProject.getId(), devOneAlpha.getId());

        mockMvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskKey").value("ALPHA-104"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.assignee.id").value(devOneAlpha.getId()))
                .andExpect(jsonPath("$.history.length()").value(1));

        String pmBody = """
                {
                  "projectId": %d,
                  "title": "Prepare Release Checklist",
                  "priority": "MEDIUM",
                  "dueDate": "2026-07-15"
                }
                """.formatted(alphaProject.getId());

        mockMvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pmBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskKey").value("ALPHA-105"));

        assertThat(auditLogRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(taskHistoryRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void developerBlockedFromCreatingTask_AndNonMemberAssignmentRejected() throws Exception {
        String devCreateBody = """
                {
                  "projectId": %d,
                  "title": "Unauthorized Dev Task",
                  "priority": "LOW"
                }
                """.formatted(alphaProject.getId());

        mockMvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(devCreateBody))
                .andExpect(status().isForbidden());

        // Non-project member assignment rejected with 422
        mockMvc.perform(patch("/api/v1/tasks/" + devOneTodoTask.getId() + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":" + outsiderDevAlpha.getId() + "}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));

        // Invalid request payload rejected with 400
        mockMvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + alphaProject.getId() + ",\"title\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusTransitionsAndDeveloperRestrictions_ShouldBeEnforced() throws Exception {
        // Developer validly moves assigned TODO -> IN_PROGRESS -> IN_REVIEW
        mockMvc.perform(patch("/api/v1/tasks/" + devOneTodoTask.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"remarks\":\"Started implementation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/v1/tasks/" + devOneTodoTask.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\",\"remarks\":\"PR ready for QA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        // Developer blocked from marking IN_REVIEW -> DONE directly (403)
        mockMvc.perform(patch("/api/v1/tasks/" + devOneTodoTask.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden());

        // Manager cannot skip review (TODO -> DONE returns 422 INVALID_WORKFLOW_TRANSITION)
        Task freshTodo = taskRepository.save(new Task(
                alphaProject, "ALPHA-999", "Fresh Todo", "Needs review",
                PriorityLevel.LOW, devOneAlpha, pmAlpha, null, null, null, null
        ));
        mockMvc.perform(patch("/api/v1/tasks/" + freshTodo.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("INVALID_WORKFLOW_TRANSITION"));
    }

    @Test
    void developerUpdatingAssignedTaskSucceeds_AndUpdatingAnotherDevelopersTaskBlocked() throws Exception {
        // Developer updates permitted fields on own assigned task -> 200
        mockMvc.perform(put("/api/v1/tasks/" + devOneTodoTask.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Detailed technical notes\",\"estimatedHours\":7.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Detailed technical notes"))
                .andExpect(jsonPath("$.estimatedHours").value(7.5));

        // Developer blocked from changing manager-controlled title -> 403
        mockMvc.perform(put("/api/v1/tasks/" + devOneTodoTask.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed By Dev\"}"))
                .andExpect(status().isForbidden());

        // Developer blocked from editing another developer's task -> 403
        mockMvc.perform(put("/api/v1/tasks/" + devTwoProgressTask.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Unauthorized edit\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/tasks/" + devTwoProgressTask.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void qaTesterPermissions_CanApproveAndRejectReviewTasks_BlockedFromManagerActions() throws Exception {
        // QA approves IN_REVIEW -> DONE
        mockMvc.perform(patch("/api/v1/tasks/" + devOneReviewTask.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\",\"remarks\":\"Verified in staging\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        // QA can reopen DONE -> IN_PROGRESS if regression found
        mockMvc.perform(patch("/api/v1/tasks/" + devOneReviewTask.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"remarks\":\"Regression detected\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // QA cannot transition another developer's TODO task
        mockMvc.perform(patch("/api/v1/tasks/" + devOneTodoTask.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());

        // QA cannot delete tasks
        mockMvc.perform(delete("/api/v1/tasks/" + devOneTodoTask.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha)))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossOrganizationAccessRejected_AndKanbanAndMyTasksWorkCorrectly() throws Exception {
        // Cross-organization Admin cannot access Org A task or Kanban board
        mockMvc.perform(get("/api/v1/tasks/" + devOneTodoTask.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminBeta)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId() + "/kanban")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminBeta)))
                .andExpect(status().isForbidden());

        // Cross-org Alpha Admin cannot access Beta task
        mockMvc.perform(get("/api/v1/tasks/" + betaTask.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha)))
                .andExpect(status().isForbidden());

        // Kanban board groups Alpha's 3 tasks accurately across columns
        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId() + "/kanban")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectKey").value("ALPHA"))
                .andExpect(jsonPath("$.totalTasks").value(3))
                .andExpect(jsonPath("$.columns.TODO.totalCount").value(1))
                .andExpect(jsonPath("$.columns.IN_PROGRESS.totalCount").value(1))
                .andExpect(jsonPath("$.columns.IN_REVIEW.totalCount").value(1))
                .andExpect(jsonPath("$.columns.DONE.totalCount").value(0))
                .andExpect(jsonPath("$.columns.BLOCKED.totalCount").value(0));

        // My Tasks returns only Dev One's 2 assigned tasks
        mockMvc.perform(get("/api/v1/tasks/my-tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // PM deletes task and audit log preserves pre-deletion snapshot
        mockMvc.perform(delete("/api/v1/tasks/" + devOneTodoTask.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().isNoContent());

        assertThat(taskRepository.findById(devOneTodoTask.getId())).isEmpty();
        assertThat(auditLogRepository.findAll()).isNotEmpty();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }
}
