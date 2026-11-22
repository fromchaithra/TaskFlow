package com.taskflow.controller;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.Team;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.OrganizationRepository;
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
class TeamControllerIntegrationTest {

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
    private User managerAlpha;
    private User devAlpha;
    private User inactiveAlpha;
    private User devBeta;
    private Team alphaTeam;
    private Team betaTeam;

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
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA Tester"));

        String hash = passwordEncoder.encode("StrongPass123!");
        adminAlpha = userRepository.save(new User(orgAlpha, "EMP-A1", "admin@alpha.test", hash, "Alice Admin", "Admin", adminRole));
        managerAlpha = userRepository.save(new User(orgAlpha, "EMP-A2", "pm@alpha.test", hash, "Mark PM", "Manager", pmRole));
        devAlpha = userRepository.save(new User(orgAlpha, "EMP-A3", "dev@alpha.test", hash, "Dana Dev", "Engineer", devRole));

        User disabled = new User(orgAlpha, "EMP-A4", "inactive@alpha.test", hash, "Inactive Dev", "Engineer", devRole);
        disabled.setActive(false);
        inactiveAlpha = userRepository.save(disabled);

        devBeta = userRepository.save(new User(orgBeta, "EMP-B1", "dev@beta.test", hash, "Bob Beta", "Engineer", devRole));

        alphaTeam = teamRepository.save(new Team(orgAlpha, "Core Platform", "Backend platform team", managerAlpha));
        betaTeam = teamRepository.save(new Team(orgBeta, "Beta Payments", "Isolated org B team", devBeta));
    }

    @Test
    void adminCreatesTeam_WithLeadAndMembers_ShouldReturn201() throws Exception {
        String body = """
                {
                  "name": "Quality Engineering",
                  "description": "Automated QA and release sign-off",
                  "leadUserId": %d,
                  "memberIds": [%d, %d]
                }
                """.formatted(managerAlpha.getId(), managerAlpha.getId(), devAlpha.getId());

        mockMvc.perform(post("/api/v1/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Quality Engineering"))
                .andExpect(jsonPath("$.lead.id").value(managerAlpha.getId()))
                .andExpect(jsonPath("$.members.length()").value(2));

        assertThat(auditLogRepository.findAll()).isNotEmpty();
    }

    @Test
    void nonAdminCannotCreateOrModifyTeams_ShouldReturn403() throws Exception {
        String createBody = """
                {
                  "name": "Shadow Team",
                  "description": "Not permitted"
                }
                """;

        mockMvc.perform(post("/api/v1/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/teams/" + alphaTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(devAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/teams/" + alphaTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAlpha)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/teams/" + alphaTeam.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + devAlpha.getId() + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/teams/" + alphaTeam.getId() + "/lead")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leadUserId\":" + devAlpha.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateTeamName_ShouldReturn409() throws Exception {
        String body = """
                {
                  "name": "core platform",
                  "description": "Duplicate case-insensitive name"
                }
                """;

        mockMvc.perform(post("/api/v1/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void inactiveLead_ShouldReturn422() throws Exception {
        String body = """
                {
                  "name": "Mobile Squad",
                  "leadUserId": %d
                }
                """.formatted(inactiveAlpha.getId());

        mockMvc.perform(post("/api/v1/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void crossOrganizationMemberAddition_AndDuplicateMemberAddition_ShouldBeRejected() throws Exception {
        // Cross-org member rejected with 403
        mockMvc.perform(post("/api/v1/teams/" + alphaTeam.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + devBeta.getId() + "}"))
                .andExpect(status().isForbidden());

        // Valid member addition succeeds (201)
        mockMvc.perform(post("/api/v1/teams/" + alphaTeam.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + devAlpha.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members.length()").value(1));

        // Duplicate member addition rejected with 409
        mockMvc.perform(post("/api/v1/teams/" + alphaTeam.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + devAlpha.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));

        // Removing member succeeds (204)
        mockMvc.perform(delete("/api/v1/teams/" + alphaTeam.getId() + "/members/" + devAlpha.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha)))
                .andExpect(status().isNoContent());
    }

    @Test
    void teamListingAndDetails_RespectOrganizationBoundaries() throws Exception {
        mockMvc.perform(get("/api/v1/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(devAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Core Platform"));

        // Probing Org B's team ID from Org A returns 403
        mockMvc.perform(get("/api/v1/teams/" + betaTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdatesLeadAndDeactivatesTeam() throws Exception {
        mockMvc.perform(patch("/api/v1/teams/" + alphaTeam.getId() + "/lead")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leadUserId\":" + devAlpha.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead.id").value(devAlpha.getId()));

        mockMvc.perform(delete("/api/v1/teams/" + alphaTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAlpha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }
}
