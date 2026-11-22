package com.taskflow.controller;

import com.taskflow.entity.Organization;
import com.taskflow.entity.RefreshToken;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.OrganizationRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
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

    private User adminUser;
    private User managerUser;
    private User developerUser;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);

        Organization org = organizationRepository.save(
                new Organization("TaskFlow Enterprise", "taskflow-test", "Software", "UTC", "MON,TUE,WED,THU,FRI")
        );
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role pmRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "Project Manager"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA Tester"));

        adminUser = userRepository.save(new User(
                org, "EMP-1001", "admin@taskflow.test", passwordEncoder.encode("StrongPass123!"),
                "Alice Admin", "Org Admin", adminRole
        ));
        managerUser = userRepository.save(new User(
                org, "EMP-1002", "pm@taskflow.test", passwordEncoder.encode("StrongPass123!"),
                "Mark Manager", "Engineering Manager", pmRole
        ));
        developerUser = userRepository.save(new User(
                org, "EMP-1003", "dev@taskflow.test", passwordEncoder.encode("StrongPass123!"),
                "Dana Developer", "Backend Engineer", devRole
        ));

        User disabled = new User(
                org, "EMP-1004", "inactive@taskflow.test", passwordEncoder.encode("StrongPass123!"),
                "Former Employee", "Engineer", devRole
        );
        disabled.setActive(false);
        userRepository.save(disabled);
    }

    @Test
    void adminCanCreateEmployee_AndAuditLogIsRecorded() throws Exception {
        String body = """
                {
                  "fullName": "Nora QA",
                  "email": "nora.qa@taskflow.test",
                  "password": "StrongPassword123!",
                  "jobTitle": "Senior QA Engineer",
                  "roleName": "QA_TESTER"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nora.qa@taskflow.test"))
                .andExpect(jsonPath("$.role").value("QA_TESTER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(auditLogRepository.findAll()).isNotEmpty();
    }

    @Test
    void projectManagerCannotCreateEmployee_ShouldReturn403() throws Exception {
        String body = """
                {
                  "fullName": "Unauthorized Create",
                  "email": "unauth@taskflow.test",
                  "password": "StrongPassword123!",
                  "roleName": "DEVELOPER"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void projectManagerListsOnlyActiveOrganizationEmployees() throws Exception {
        mockMvc.perform(get("/api/v1/users?active=false")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.email == 'inactive@taskflow.test')]").isEmpty());
    }

    @Test
    void developerCannotListUsers_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developerUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void developerCanReadAndUpdateOwnProfile_ButCannotAccessAnotherUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + developerUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(developerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dev@taskflow.test"));

        String updateBody = """
                {
                  "fullName": "Dana Senior Dev",
                  "jobTitle": "Senior Backend Engineer",
                  "avatarColor": "#197DE5"
                }
                """;

        mockMvc.perform(put("/api/v1/users/" + developerUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(developerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Dana Senior Dev"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));

        mockMvc.perform(get("/api/v1/users/" + managerUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(developerUser)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/users/" + managerUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(developerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminChangingUserRole_ShouldRevokeActiveRefreshTokens() throws Exception {
        String tokenHash = jwtService.sha256Hex("sample-active-refresh-token");
        RefreshToken activeToken = refreshTokenRepository.save(
                new RefreshToken(developerUser, tokenHash, Instant.now().plus(7, ChronoUnit.DAYS))
        );

        mockMvc.perform(patch("/api/v1/users/" + developerUser.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"QA_TESTER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("QA_TESTER"));

        RefreshToken reloaded = refreshTokenRepository.findById(activeToken.getId()).orElseThrow();
        assertThat(reloaded.isRevoked()).isTrue();
    }

    @Test
    void projectManagerCannotChangeStatusOrRole_AndUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + developerUser.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/users/" + developerUser.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }
}
