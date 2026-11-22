package com.taskflow.controller;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
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
class ProjectControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
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
    private User pmOneAlpha;
    private User pmTwoAlpha;
    private User devAssignedAlpha;
    private User devUnassignedAlpha;
    private User qaAlpha;
    private User devBeta;
    private Project alphaProject;
    private Project betaProject;

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
        pmOneAlpha = userRepository.save(new User(orgAlpha, "EMP-A2", "pm1@alpha.test", hash, "Mark PM", "Manager", pmRole));
        pmTwoAlpha = userRepository.save(new User(orgAlpha, "EMP-A3", "pm2@alpha.test", hash, "Paula PM", "Manager", pmRole));
        devAssignedAlpha = userRepository.save(new User(orgAlpha, "EMP-A4", "dev1@alpha.test", hash, "Dana Dev", "Engineer", devRole));
        devUnassignedAlpha = userRepository.save(new User(orgAlpha, "EMP-A5", "dev2@alpha.test", hash, "Devon Dev", "Engineer", devRole));
        qaAlpha = userRepository.save(new User(orgAlpha, "EMP-A6", "qa@alpha.test", hash, "Quinn QA", "QA Engineer", qaRole));
        devBeta = userRepository.save(new User(orgBeta, "EMP-B1", "dev@beta.test", hash, "Bob Beta", "Engineer", devRole));

        User pmBeta = userRepository.save(new User(orgBeta, "EMP-B2", "pm@beta.test", hash, "Ben Beta PM", "Manager", pmRole));

        alphaProject = projectRepository.save(new Project(
                orgAlpha,
                "ALPHA",
                "Alpha Core Platform",
                "Primary platform project",
                pmOneAlpha,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        ));
        projectMemberRepository.save(new ProjectMember(alphaProject, pmOneAlpha, RoleName.PROJECT_MANAGER, adminAlpha));
        projectMemberRepository.save(new ProjectMember(alphaProject, devAssignedAlpha, RoleName.DEVELOPER, pmOneAlpha));

        betaProject = projectRepository.save(new Project(
                orgBeta,
                "BETA",
                "Beta Ledger",
                "Cross-organization isolated project",
                pmBeta,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        ));
        projectMemberRepository.save(new ProjectMember(betaProject, pmBeta, RoleName.PROJECT_MANAGER, pmBeta));
    }

    @Test
    void adminCreatesProject_ShouldReturn201AndWriteAuditLog() throws Exception {
        String body = """
                {
                  "name": "Enterprise Billing",
                  "projectKey": "bill",
                  "description": "Billing automation platform",
                  "startDate": "2026-02-01",
                  "endDate": "2026-08-31",
                  "initialStatus": "PLANNING",
                  "managerId": %d,
                  "memberIds": [%d, %d]
                }
                """.formatted(pmOneAlpha.getId(), devAssignedAlpha.getId(), qaAlpha.getId());

        mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectKey").value("BILL"))
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.manager.id").value(pmOneAlpha.getId()))
                .andExpect(jsonPath("$.members.length()").value(3));

        assertThat(auditLogRepository.findAll()).isNotEmpty();
    }

    @Test
    void projectManagerCreatesProject_ShouldReturn201WithSelfAsManager() throws Exception {
        String body = """
                {
                  "name": "Mobile Sync",
                  "projectKey": "MOBSYNC",
                  "description": "Offline sync engine",
                  "startDate": "2026-03-01",
                  "endDate": "2026-09-30"
                }
                """;

        mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmTwoAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectKey").value("MOBSYNC"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.manager.id").value(pmTwoAlpha.getId()))
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void developerBlockedFromCreatingProject_ShouldReturn403() throws Exception {
        String body = """
                {
                  "name": "Shadow Project",
                  "projectKey": "SHADOW",
                  "startDate": "2026-03-01"
                }
                """;

        mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devAssignedAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateProjectKeyWithinOrganization_ShouldReturn409() throws Exception {
        String body = """
                {
                  "name": "Duplicate Alpha",
                  "projectKey": "alpha",
                  "startDate": "2026-03-01"
                }
                """;

        mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void crossOrganizationMemberAndDuplicateMembership_ShouldBeRejected() throws Exception {
        // Cross-org member rejected with 403
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + devBeta.getId() + ",\"projectRole\":\"DEVELOPER\"}"))
                .andExpect(status().isForbidden());

        // Duplicate membership rejected with 409
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + devAssignedAlpha.getId() + ",\"projectRole\":\"DEVELOPER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));

        // Valid member addition, role update, and removal succeed
        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + qaAlpha.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectRole").value("QA_TESTER"));

        mockMvc.perform(patch("/api/v1/projects/" + alphaProject.getId() + "/members/" + qaAlpha.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectRole\":\"DEVELOPER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectRole").value("DEVELOPER"));

        mockMvc.perform(delete("/api/v1/projects/" + alphaProject.getId() + "/members/" + qaAlpha.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmOneAlpha)))
                .andExpect(status().isNoContent());
    }

    @Test
    void unauthorizedCrossOrganizationProjectAccess_EvenByAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + betaProject.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/projects/" + betaProject.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha)))
                .andExpect(status().isForbidden());
    }

    @Test
    void projectManagerAttemptingToModifyAnotherManagersProject_ShouldReturn403() throws Exception {
        String updateBody = """
                {
                  "name": "Hijacked Name",
                  "description": "Unauthorized edit",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """;

        mockMvc.perform(put("/api/v1/projects/" + alphaProject.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmTwoAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/projects/" + alphaProject.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmTwoAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_HOLD\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/projects/" + alphaProject.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmTwoAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + qaAlpha.getId() + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/projects/" + alphaProject.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmTwoAlpha)))
                .andExpect(status().isForbidden());
    }

    @Test
    void developerAccessingAssignedProject_ShouldReturn200_AndUnassignedProject_ShouldReturn403() throws Exception {
        // Assigned developer can view project, list projects, and list members
        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(devAssignedAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectKey").value("ALPHA"))
                .andExpect(jsonPath("$.members.length()").value(2));

        mockMvc.perform(get("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devAssignedAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devAssignedAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Unassigned developer receives 403 on project detail & members, and empty list
        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(devUnassignedAlpha)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects/" + alphaProject.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devUnassignedAlpha)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devUnassignedAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void adminAndAssignedManagerCanUpdateStatusAndArchiveProject() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/" + alphaProject.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pmOneAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_HOLD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"));

        mockMvc.perform(delete("/api/v1/projects/" + alphaProject.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ProjectStatus.ARCHIVED.name()));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }
}
