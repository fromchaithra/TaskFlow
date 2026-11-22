package com.taskflow.security;

import com.taskflow.entity.Organization;
import com.taskflow.entity.RefreshToken;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.support.TestDatabaseCleaner;
import jakarta.servlet.http.Cookie;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private Organization org;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);

        org = organizationRepository.save(
                new Organization("TaskFlow Test Org", "taskflow-test", "Software", "UTC", "MON,TUE,WED,THU,FRI")
        );
        adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin role"));
        roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM role"));
        roleRepository.save(new Role(RoleName.DEVELOPER, "Developer role"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA role"));

        User admin = new User(
                org,
                "EMP-0001",
                "admin@taskflow.test",
                passwordEncoder.encode("StrongPass123!"),
                "Alice Admin",
                "System Admin",
                adminRole
        );
        userRepository.save(admin);
    }

    @Test
    void loginMeRefreshAndLogout_ShouldEnforceJwtAndRevocation() throws Exception {
        String loginBody = """
                {
                  "email": "admin@taskflow.test",
                  "password": "StrongPass123!"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("admin@taskflow.test"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        JsonNode loginJson = jsonMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asString();
        Cookie refreshCookie = loginResult.getResponse().getCookie("tf_refresh");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        // Authenticated /me succeeds
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@taskflow.test"));

        // Refresh without CSRF header fails
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isForbidden());

        // Refresh with CSRF header succeeds and rotates token
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "TaskFlow")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie rotatedCookie = refreshResult.getResponse().getCookie("tf_refresh");
        assertThat(rotatedCookie).isNotNull();

        // Logout revokes access token JTI
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .cookie(rotatedCookie))
                .andExpect(status().isNoContent());

        // Revoked access token is immediately rejected
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void replayedRefreshToken_RevokesAllSiblingTokensDurably() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@taskflow.test","password":"StrongPass123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Cookie firstToken = loginResult.getResponse().getCookie("tf_refresh");
        assertThat(firstToken).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "TaskFlow")
                        .cookie(firstToken))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedToken = refreshResult.getResponse().getCookie("tf_refresh");
        assertThat(rotatedToken).isNotNull();

        assertThat(refreshTokenRepository.findByTokenHashWithUser(jwtService.sha256Hex(firstToken.getValue())))
                .get().extracting(RefreshToken::isRevoked).isEqualTo(true);
        assertThat(refreshTokenRepository.findByTokenHashWithUser(jwtService.sha256Hex(rotatedToken.getValue())))
                .get().extracting(RefreshToken::isRevoked).isEqualTo(false);

        // Replaying the already-revoked token must be rejected and must revoke the family.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "TaskFlow")
                        .cookie(firstToken))
                .andExpect(status().isUnauthorized());

        assertThat(refreshTokenRepository.findByTokenHashWithUser(jwtService.sha256Hex(rotatedToken.getValue())))
                .get().extracting(RefreshToken::isRevoked).isEqualTo(true);

        // The sibling token can no longer be used either.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "TaskFlow")
                        .cookie(rotatedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserRefreshRevocation_Persists() throws Exception {
        User user = new User(
                org,
                "EMP-0003",
                "deactivate@taskflow.test",
                passwordEncoder.encode("StrongPass123!"),
                "Deactivate Me",
                "Developer",
                adminRole
        );
        userRepository.save(user);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"deactivate@taskflow.test","password":"StrongPass123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("tf_refresh");
        assertThat(refreshCookie).isNotNull();

        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "TaskFlow")
                        .cookie(refreshCookie))
                .andExpect(status().isForbidden());

        assertThat(refreshTokenRepository.findByTokenHashWithUser(jwtService.sha256Hex(refreshCookie.getValue())))
                .get().extracting(RefreshToken::isRevoked).isEqualTo(true);
    }

    @Test
    void deactivatedUser_ShouldBeBlockedFromLogin() throws Exception {
        User disabled = new User(
                org,
                "EMP-0002",
                "inactive@taskflow.test",
                passwordEncoder.encode("StrongPass123!"),
                "Inactive Dev",
                "Developer",
                adminRole
        );
        disabled.setActive(false);
        userRepository.save(disabled);

        String loginBody = """
                {
                  "email": "inactive@taskflow.test",
                  "password": "StrongPass123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DEACTIVATED"));
    }
}
