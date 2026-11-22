package com.taskflow.security;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.service.AuditLogWriter;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Audit persistence failures must never break authentication. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationAuditResilienceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private AuditLogWriter auditLogWriter;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        Organization org = organizationRepository.save(
                new Organization("Resilient Org", "auth-resilient-org", "Software", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        userRepository.save(new User(org, "EMP-0001", "resilient@security.test",
                passwordEncoder.encode("StrongPass123!"), "Resilient User", "Tester", adminRole));
        Mockito.doThrow(new RuntimeException("audit store unavailable"))
                .when(auditLogWriter).safeRecord(Mockito.any(), Mockito.any(), Mockito.any(AuditAction.class),
                        Mockito.any(), Mockito.any());
    }

    @Test
    void loginStillSucceedsWhenAuditWriteFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"resilient@security.test","password":"StrongPass123!"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void failedLoginStillReturns401WhenAuditWriteFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"resilient@security.test","password":"WrongPassword123!"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
