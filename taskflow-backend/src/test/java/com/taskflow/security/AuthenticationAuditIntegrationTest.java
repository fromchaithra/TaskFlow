package com.taskflow.security;

import com.taskflow.entity.AuditLog;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.support.TestDatabaseCleaner;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationAuditIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        Organization org = organizationRepository.save(
                new Organization("Audit Org", "auth-audit-org", "Software", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        userRepository.save(new User(org, "EMP-0001", "audit@security.test",
                passwordEncoder.encode(PASSWORD), "Audit User", "Tester", adminRole));
    }

    private List<AuditLog> events(AuditAction action) {
        return auditLogRepository.findAll().stream().filter(e -> e.getAction() == action).toList();
    }

    private void assertNoSensitiveValues(AuditLog event) {
        String serialized = String.valueOf(event.getNewValue()) + String.valueOf(event.getPreviousValue());
        assertThat(serialized).doesNotContain(PASSWORD);
        assertThat(serialized).doesNotContain("accessToken", "refreshToken", "passwordHash", "tf_refresh");
    }

    @Test
    void successfulLoginIsAuditedWithoutSecrets() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"audit@security.test","password":"StrongPass123!"}
                                """))
                .andExpect(status().isOk());
        assertThat(events(AuditAction.LOGIN_SUCCESS)).hasSize(1);
        assertNoSensitiveValues(events(AuditAction.LOGIN_SUCCESS).getFirst());
    }

    @Test
    void failedLoginForKnownAccountIsAudited() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"audit@security.test","password":"WrongPassword123!"}
                                """))
                .andExpect(status().isUnauthorized());
        assertThat(events(AuditAction.LOGIN_FAILURE)).hasSize(1);
        assertNoSensitiveValues(events(AuditAction.LOGIN_FAILURE).getFirst());
    }

    @Test
    void refreshTokenReuseIsAuditedWithoutTokenValues() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"audit@security.test","password":"StrongPass123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Cookie first = login.getResponse().getCookie("tf_refresh");
        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "TaskFlow").cookie(first))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(refresh.getResponse().getCookie("tf_refresh")).isNotNull();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "TaskFlow").cookie(first))
                .andExpect(status().isUnauthorized());

        assertThat(events(AuditAction.REFRESH_TOKEN_REUSE)).hasSize(1);
        assertNoSensitiveValues(events(AuditAction.REFRESH_TOKEN_REUSE).getFirst());
    }

    @Test
    void unknownEmailFailureDoesNotCreateAuditEvent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@security.test","password":"WrongPassword123!"}
                                """))
                .andExpect(status().isUnauthorized());
        assertThat(events(AuditAction.LOGIN_FAILURE)).isEmpty();
    }
}
