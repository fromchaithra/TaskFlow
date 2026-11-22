package com.taskflow.security;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "taskflow.rate-limit.login-per-ip-limit=3",
        "taskflow.rate-limit.login-failures-per-account-limit=2",
        "taskflow.rate-limit.refresh-per-ip-limit=2",
        "taskflow.rate-limit.activation-per-ip-limit=2",
        "taskflow.rate-limit.registration-per-ip-limit=2",
        "taskflow.rate-limit.window-seconds=2"
})
class RateLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RateLimitFilter rateLimitFilter;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        rateLimitFilter.clear();
        TestDatabaseCleaner.clean(jdbcTemplate);
        Organization org = organizationRepository.save(
                new Organization("Rate Org", "rate-limit-org", "Software", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        userRepository.save(new User(org, "EMP-0001", "user@rate.test",
                passwordEncoder.encode("StrongPass123!"), "Rate User", "Tester", adminRole));
        userRepository.save(new User(org, "EMP-0002", "other@rate.test",
                passwordEncoder.encode("StrongPass123!"), "Other User", "Tester", adminRole));
    }

    private int login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .remoteAddress(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void requestsWithinLimitSucceedAndExcessReturns429() throws Exception {
        assertThat(login("user@rate.test", "StrongPass123!", "10.0.0.1")).isEqualTo(200);
        assertThat(login("user@rate.test", "StrongPass123!", "10.0.0.1")).isEqualTo(200);
        assertThat(login("user@rate.test", "StrongPass123!", "10.0.0.1")).isEqualTo(200);

        mockMvc.perform(post("/api/v1/auth/login")
                        .remoteAddress("10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@rate.test","password":"StrongPass123!"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void limitResetsAfterWindowExpiry() throws Exception {
        assertThat(login("user@rate.test", "StrongPass123!", "10.0.0.2")).isEqualTo(200);
        assertThat(login("user@rate.test", "StrongPass123!", "10.0.0.2")).isEqualTo(200);
        assertThat(login("user@rate.test", "StrongPass123!", "10.0.0.2")).isEqualTo(200);
        assertThat(login("user@rate.test", "StrongPass123!", "10.0.0.2")).isEqualTo(429);

        Thread.sleep(2200);

        assertThat(login("user@rate.test", "StrongPass123!", "10.0.0.2")).isEqualTo(200);
    }

    @Test
    void independentClientIpsAndAccountsHaveIndependentLimits() throws Exception {
        assertThat(login("user@rate.test", "bad-pass-1", "10.1.0.1")).isEqualTo(401);
        assertThat(login("user@rate.test", "bad-pass-2", "10.1.0.2")).isEqualTo(401);
        assertThat(login("user@rate.test", "bad-pass-3", "10.1.0.3")).isEqualTo(429);
        // A different account is measured independently.
        assertThat(login("other@rate.test", "StrongPass123!", "10.1.0.4")).isEqualTo(200);

        // IP buckets are independent: exhausting one IP does not block another.
        assertThat(login("other@rate.test", "StrongPass123!", "10.9.0.1")).isEqualTo(200);
        assertThat(login("other@rate.test", "StrongPass123!", "10.9.0.1")).isEqualTo(200);
        assertThat(login("other@rate.test", "StrongPass123!", "10.9.0.1")).isEqualTo(200);
        assertThat(login("other@rate.test", "StrongPass123!", "10.9.0.1")).isEqualTo(429);
        assertThat(login("other@rate.test", "StrongPass123!", "10.9.0.2")).isEqualTo(200);
    }

    @Test
    void successfulLoginClearsAccountFailureCounter() throws Exception {
        assertThat(login("user@rate.test", "bad-pass-1", "10.2.0.1")).isEqualTo(401);
        assertThat(login("user@rate.test", "StrongPass123!", "10.2.0.2")).isEqualTo(200);
        assertThat(login("user@rate.test", "bad-pass-2", "10.2.0.3")).isEqualTo(401);
        assertThat(login("user@rate.test", "bad-pass-3", "10.2.0.4")).isEqualTo(401);
        assertThat(login("user@rate.test", "bad-pass-4", "10.2.0.5")).isEqualTo(429);
    }

    @Test
    void refreshEndpointIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .remoteAddress("10.3.0.1")
                            .header("X-Requested-With", "TaskFlow"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .remoteAddress("10.3.0.1")
                        .header("X-Requested-With", "TaskFlow"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void publicRegistrationAbuseIsLimitedWithoutCreatingPartialTenants() throws Exception {
        long orgsBefore = organizationRepository.count();
        long usersBefore = userRepository.count();

        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(post("/api/v1/setup/admin")
                            .remoteAddress("10.4.0.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fullName":"Reg %d","email":"reg%d@rate.test",
                                     "password":"StrongPass123!","confirmPassword":"StrongPass123!"}
                                    """.formatted(i, i)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/setup/admin")
                        .remoteAddress("10.4.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Reg 3","email":"reg3@rate.test",
                                 "password":"StrongPass123!","confirmPassword":"StrongPass123!"}
                                """))
                .andExpect(status().isTooManyRequests());

        assertThat(organizationRepository.count()).isEqualTo(orgsBefore + 2);
        assertThat(userRepository.count()).isEqualTo(usersBefore + 2);
        assertThat(userRepository.existsByEmailIgnoreCase("reg3@rate.test")).isFalse();
    }
}
