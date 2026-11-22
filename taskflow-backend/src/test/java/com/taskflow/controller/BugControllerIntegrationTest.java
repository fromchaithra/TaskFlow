package com.taskflow.controller;

import com.taskflow.entity.Bug;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.BugHistoryRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.RoleRepository;
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
class BugControllerIntegrationTest {

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
    private BugRepository bugRepository;
    @Autowired
    private BugHistoryRepository bugHistoryRepository;
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
    private Bug alphaAssignedBug;
    private Bug alphaResolvedBug;
    private Bug betaBug;

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
        adminBeta = userRepository.save(new User(orgBeta, "EMP-B1", "admin@beta.test", hash, "Bob Beta", "Admin", adminRole));

        alphaProject = projectRepository.save(new Project(
                orgAlpha, "ALPHA", "Alpha Core", "Core platform", pmAlpha,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        ));
        projectMemberRepository.save(new ProjectMember(alphaProject, pmAlpha, RoleName.PROJECT_MANAGER, adminAlpha));
        projectMemberRepository.save(new ProjectMember(alphaProject, devOneAlpha, RoleName.DEVELOPER, pmAlpha));
        projectMemberRepository.save(new ProjectMember(alphaProject, devTwoAlpha, RoleName.DEVELOPER, pmAlpha));
        projectMemberRepository.save(new ProjectMember(alphaProject, qaAlpha, RoleName.QA_TESTER, pmAlpha));

        betaProject = projectRepository.save(new Project(
                orgBeta, "BETA", "Beta Ledger", "Org B project", adminBeta,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        ));
        projectMemberRepository.save(new ProjectMember(betaProject, adminBeta, RoleName.PROJECT_MANAGER, adminBeta));

        alphaAssignedBug = bugRepository.save(new Bug(
                alphaProject, alphaProject.nextBugKey(), null, "Token refresh race condition",
                "Parallel refresh calls trigger 401 sporadically", "1. Open two tabs\n2. Expire JWT",
                BugSeverity.HIGH, PriorityLevel.HIGH, qaAlpha, devOneAlpha
        ));

        Bug resolved = new Bug(
                alphaProject, alphaProject.nextBugKey(), null, "CSV export escapes commas incorrectly",
                "Exported report shifts columns when task titles contain commas", "1. Export CSV",
                BugSeverity.MEDIUM, PriorityLevel.MEDIUM, qaAlpha, devOneAlpha
        );
        resolved.setStatus(BugStatus.RESOLVED);
        alphaResolvedBug = bugRepository.save(resolved);
        projectRepository.save(alphaProject);

        betaBug = bugRepository.save(new Bug(
                betaProject, betaProject.nextBugKey(), null, "Beta ledger rounding error",
                "Rounding discrepancy in Beta org", "1. Reconcile",
                BugSeverity.CRITICAL, PriorityLevel.CRITICAL, adminBeta, adminBeta
        ));
        projectRepository.save(betaProject);
    }

    @Test
    void adminDeveloperAndQaCanCreateBugs_AndAuditHistoryAndNotificationsCreated() throws Exception {
        String adminBug = """
                {
                  "projectId": %d,
                  "title": "Admin reported timeout",
                  "description": "Gateway returns 504 on large payload",
                  "stepsToReproduce": "Send 8MB request",
                  "severity": "CRITICAL",
                  "priority": "CRITICAL",
                  "assigneeId": %d
                }
                """.formatted(alphaProject.getId(), devOneAlpha.getId());

        mockMvc.perform(post("/api/v1/bugs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBug))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bugKey").value("ALPHA-BUG-103"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.history.length()").value(1));

        String qaBug = """
                {
                  "projectId": %d,
                  "title": "QA reported filter reset bug",
                  "description": "Kanban search filter clears after drag-and-drop",
                  "severity": "MEDIUM",
                  "priority": "HIGH"
                }
                """.formatted(alphaProject.getId());

        mockMvc.perform(post("/api/v1/bugs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qaBug))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bugKey").value("ALPHA-BUG-104"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        String devBug = """
                {
                  "projectId": %d,
                  "title": "Developer reported memory spike",
                  "description": "Batch job retains references in heap",
                  "severity": "HIGH",
                  "priority": "MEDIUM"
                }
                """.formatted(alphaProject.getId());

        mockMvc.perform(post("/api/v1/bugs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(devBug))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bugKey").value("ALPHA-BUG-105"));

        // Non-project member cannot report bug in Alpha -> 403
        mockMvc.perform(post("/api/v1/bugs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderDevAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(devBug))
                .andExpect(status().isForbidden());

        assertThat(auditLogRepository.findAll()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(bugHistoryRepository.findAll()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(notificationRepository.findAll()).isNotEmpty();
    }

    @Test
    void nonMemberAssignmentAndCrossOrganizationAccess_ShouldBeRejected() throws Exception {
        // Assign bug to non-project member -> 422
        mockMvc.perform(patch("/api/v1/bugs/" + alphaAssignedBug.getId() + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":" + outsiderDevAlpha.getId() + "}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));

        // Cross-org Admin cannot access Org A bug -> 403
        mockMvc.perform(get("/api/v1/bugs/" + alphaAssignedBug.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminBeta)))
                .andExpect(status().isForbidden());

        // Org A Admin cannot access Org B bug -> 403
        mockMvc.perform(get("/api/v1/bugs/" + betaBug.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha)))
                .andExpect(status().isForbidden());
    }

    @Test
    void developerWorkflowAndUnauthorizedModificationChecks_ShouldBeEnforced() throws Exception {
        // Assigned developer updates permitted fields -> 200
        mockMvc.perform(put("/api/v1/bugs/" + alphaAssignedBug.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Root cause identified in token mutex\",\"stepsToReproduce\":\"Updated steps\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Root cause identified in token mutex"));

        // Assigned developer transitions ASSIGNED -> IN_PROGRESS -> RESOLVED
        mockMvc.perform(patch("/api/v1/bugs/" + alphaAssignedBug.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/v1/bugs/" + alphaAssignedBug.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"remarks\":\"Added synchronized lock\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Developer blocked from verifying bug -> 403
        mockMvc.perform(post("/api/v1/bugs/" + alphaAssignedBug.getId() + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());

        // Another developer blocked from editing Dev One's bug -> 403
        mockMvc.perform(put("/api/v1/bugs/" + alphaAssignedBug.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(devTwoAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Unauthorized edit by dev two\"}"))
                .andExpect(status().isForbidden());

        // Developer blocked from changing severity -> 403
        mockMvc.perform(patch("/api/v1/bugs/" + alphaAssignedBug.getId() + "/severity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\":\"LOW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void qaVerificationApprovalRejectionReopenSeverityAndInvalidTransitions_ShouldWork() throws Exception {
        // QA rejects resolved bug -> returns to REOPENED
        mockMvc.perform(post("/api/v1/bugs/" + alphaResolvedBug.getId() + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"remarks\":\"Still fails with quotes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"));

        // Invalid transition: cannot verify a REOPENED bug -> 422
        mockMvc.perform(post("/api/v1/bugs/" + alphaResolvedBug.getId() + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("INVALID_WORKFLOW_TRANSITION"));

        // Developer fixes REOPENED -> IN_PROGRESS -> RESOLVED
        mockMvc.perform(patch("/api/v1/bugs/" + alphaResolvedBug.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/bugs/" + alphaResolvedBug.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        // QA approves -> VERIFIED
        mockMvc.perform(post("/api/v1/bugs/" + alphaResolvedBug.getId() + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"remarks\":\"Verified clean\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        // QA reopens verified bug -> REOPENED
        mockMvc.perform(post("/api/v1/bugs/" + alphaResolvedBug.getId() + "/reopen")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Regression in nightly build\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"));

        // QA updates severity -> 200
        mockMvc.perform(patch("/api/v1/bugs/" + alphaResolvedBug.getId() + "/severity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\":\"CRITICAL\",\"remarks\":\"Impacts billing export\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("CRITICAL"));

        // QA blocked from deleting bug -> 403
        mockMvc.perform(delete("/api/v1/bugs/" + alphaResolvedBug.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(qaAlpha)))
                .andExpect(status().isForbidden());

        // PM deletes bug -> 204 and audit preserved
        mockMvc.perform(delete("/api/v1/bugs/" + alphaResolvedBug.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmAlpha)))
                .andExpect(status().isNoContent());

        assertThat(bugRepository.findById(alphaResolvedBug.getId())).isEmpty();
        assertThat(auditLogRepository.findAll()).isNotEmpty();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }
}
