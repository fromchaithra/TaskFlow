package com.taskflow.controller;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.Sprint;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.SprintStatus;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.SprintTaskRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SprintControllerIntegrationTest {

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
    private SprintRepository sprintRepository;
    @Autowired
    private SprintTaskRepository sprintTaskRepository;
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
    private User devAlpha;
    private User qaAlpha;
    private User unassignedDevAlpha;
    private User adminBeta;
    private Project alphaProject;
    private Project otherAlphaProject;
    private Task alphaDoneTask;
    private Task alphaInProgressTask;
    private Task alphaBlockedTask;
    private Task otherProjectTask;

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
        devAlpha = userRepository.save(new User(orgAlpha, "EMP-A3", "dev@alpha.test", hash, "Dana Dev", "Engineer", devRole));
        qaAlpha = userRepository.save(new User(orgAlpha, "EMP-A4", "qa@alpha.test", hash, "Quinn QA", "QA", qaRole));
        unassignedDevAlpha = userRepository.save(new User(orgAlpha, "EMP-A5", "outsider@alpha.test", hash, "Oscar Dev", "Engineer", devRole));
        adminBeta = userRepository.save(new User(orgBeta, "EMP-B1", "admin@beta.test", hash, "Bob Beta", "Admin", adminRole));

        alphaProject = projectRepository.save(new Project(
                orgAlpha, "ALPHA", "Alpha Core", "Primary platform", pmAlpha,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        ));
        projectMemberRepository.save(new ProjectMember(alphaProject, pmAlpha, RoleName.PROJECT_MANAGER, adminAlpha));
        projectMemberRepository.save(new ProjectMember(alphaProject, devAlpha, RoleName.DEVELOPER, pmAlpha));
        projectMemberRepository.save(new ProjectMember(alphaProject, qaAlpha, RoleName.QA_TESTER, pmAlpha));

        otherAlphaProject = projectRepository.save(new Project(
                orgAlpha, "GAMMA", "Gamma Mobile", "Separate project", adminAlpha,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        ));
        projectMemberRepository.save(new ProjectMember(otherAlphaProject, adminAlpha, RoleName.PROJECT_MANAGER, adminAlpha));

        Task t1 = new Task(alphaProject, alphaProject.nextTaskKey(), "Auth Hardening", "Done task",
                PriorityLevel.HIGH, devAlpha, pmAlpha, null, null, new BigDecimal("4.00"), LocalDate.of(2026, 4, 10));
        t1.setStatus(TaskStatus.DONE);
        alphaDoneTask = taskRepository.save(t1);

        Task t2 = new Task(alphaProject, alphaProject.nextTaskKey(), "Webhook Retries", "In progress",
                PriorityLevel.MEDIUM, devAlpha, pmAlpha, null, null, new BigDecimal("6.00"), LocalDate.of(2026, 4, 12));
        t2.setStatus(TaskStatus.IN_PROGRESS);
        alphaInProgressTask = taskRepository.save(t2);

        Task t3 = new Task(alphaProject, alphaProject.nextTaskKey(), "Third-Party SLA", "Blocked task",
                PriorityLevel.CRITICAL, devAlpha, pmAlpha, null, null, new BigDecimal("2.00"), LocalDate.of(2026, 4, 14));
        t3.setStatus(TaskStatus.BLOCKED);
        alphaBlockedTask = taskRepository.save(t3);
        projectRepository.save(alphaProject);

        otherProjectTask = taskRepository.save(new Task(
                otherAlphaProject, otherAlphaProject.nextTaskKey(), "Gamma Push Token", "Other project task",
                PriorityLevel.LOW, adminAlpha, adminAlpha, null, null, new BigDecimal("3.00"), LocalDate.of(2026, 4, 15)
        ));
        projectRepository.save(otherAlphaProject);
    }

    @Test
    void adminAndProjectManagerCanCreateSprints_AndDeveloperIsBlocked() throws Exception {
        String adminSprintBody = """
                {
                  "name": "Sprint 1",
                  "goal": "Ship core auth and permissions",
                  "startDate": "2026-02-01",
                  "endDate": "2026-02-14"
                }
                """;

        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminSprintBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sprint 1"))
                .andExpect(jsonPath("$.status").value("PLANNED"));

        String pmSprintBody = """
                {
                  "name": "Sprint 2",
                  "goal": "Ship Kanban workflow",
                  "startDate": "2026-02-15",
                  "endDate": "2026-02-28"
                }
                """;

        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pmSprintBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sprint 2"));

        // Developer blocked from creating sprint -> 403
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dev Sprint\",\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-14\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidDatesAndOverlappingActiveSprints_ShouldBeRejected() throws Exception {
        // End date before start date -> 422
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Sprint\",\"startDate\":\"2026-03-15\",\"endDate\":\"2026-03-01\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));

        // Create and start Sprint 1
        Sprint sprint1 = sprintRepository.save(new Sprint(
                alphaProject, "Sprint Active", "Active goal",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 14), pmAlpha
        ));
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint1.getId() + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Creating an overlapping sprint while Sprint Active is running -> 422
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Overlapping\",\"startDate\":\"2026-03-10\",\"endDate\":\"2026-03-20\"}"))
                .andExpect(status().is(422));

        // Creating a non-overlapping future sprint succeeds, but starting it while Sprint Active is active -> 422
        Sprint futureSprint = sprintRepository.save(new Sprint(
                alphaProject, "Sprint Future", "Next sprint",
                LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 28), pmAlpha
        ));
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + futureSprint.getId() + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().is(422));
    }

    @Test
    void taskAdditionValidation_ForeignTaskAndDuplicateRejected_AndSprintReportAccurate() throws Exception {
        Sprint sprint = sprintRepository.save(new Sprint(
                alphaProject, "Sprint Delivery", "Deliver reporting",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15), pmAlpha
        ));

        // Foreign project task rejected -> 422
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[" + otherProjectTask.getId() + "]}"))
                .andExpect(status().is(422));

        // Add 3 Alpha tasks (1 DONE, 1 IN_PROGRESS, 1 BLOCKED)
        String addBody = """
                {
                  "taskIds": [%d, %d, %d]
                }
                """.formatted(alphaDoneTask.getId(), alphaInProgressTask.getId(), alphaBlockedTask.getId());

        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCount").value(3));

        // Duplicate addition of an already-added task rejected -> 409
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[" + alphaDoneTask.getId() + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));

        // Start sprint and fetch report as QA member
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/report")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(3))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.inProgressTasks").value(1))
                .andExpect(jsonPath("$.blockedTasks").value(1))
                .andExpect(jsonPath("$.remainingTasks").value(2))
                .andExpect(jsonPath("$.completionPercentage").value(33.33))
                .andExpect(jsonPath("$.completedTaskList.length()").value(1))
                .andExpect(jsonPath("$.incompleteTaskList.length()").value(2));

        // Complete sprint -> incomplete tasks returned to backlog, report snapshot still preserves 1/3 completed
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Task reloadedIncomplete = taskRepository.findById(alphaInProgressTask.getId()).orElseThrow();
        assertThat(reloadedIncomplete.getSprint()).isNull();

        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/report")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(3))
                .andExpect(jsonPath("$.completedTasks").value(1));
    }

    @Test
    void lifecycleGuardsAndUnauthorizedProjectAccess_ShouldBeEnforced() throws Exception {
        Sprint sprint = sprintRepository.save(new Sprint(
                alphaProject, "Sprint Guard", "Guard test",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 14), pmAlpha
        ));

        // Cannot complete a PLANNED sprint -> 422 INVALID_WORKFLOW_TRANSITION
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("INVALID_WORKFLOW_TRANSITION"));

        // Update sprint details -> 200
        mockMvc.perform(put("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint Guard Updated\",\"goal\":\"Updated goal\",\"startDate\":\"2026-05-02\",\"endDate\":\"2026-05-16\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sprint Guard Updated"));

        // Add & remove a task -> 200 then 204
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[" + alphaDoneTask.getId() + "]}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/tasks/" + alphaDoneTask.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().isNoContent());

        // Cancel sprint -> 200, then starting cancelled sprint rejected -> 422
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SprintStatus.CANCELLED.name()));

        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/sprints/" + sprint.getId() + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("INVALID_WORKFLOW_TRANSITION"));

        // Unassigned developer and cross-organization admin blocked -> 403
        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId() + "/sprints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(unassignedDevAlpha)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId() + "/sprints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminBeta)))
                .andExpect(status().isForbidden());

        assertThat(auditLogRepository.findAll()).isNotEmpty();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }
}
