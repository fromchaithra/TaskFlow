package com.taskflow.controller;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User adminUser;
    private User developerUser;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);

        Organization org = organizationRepository.save(
                new Organization("TaskFlow Enterprise", "taskflow-test", "Software", "UTC", "MON,TUE,WED,THU,FRI")
        );
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));

        adminUser = userRepository.save(new User(
                org, "EMP-2001", "admin@dashboard.test", passwordEncoder.encode("StrongPass123!"),
                "Alice Admin", "Org Admin", adminRole
        ));
        developerUser = userRepository.save(new User(
                org, "EMP-2002", "dev@dashboard.test", passwordEncoder.encode("StrongPass123!"),
                "Dana Developer", "Backend Engineer", devRole
        ));
    }

    @Test
    void adminCanAccessDashboardStats() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.totalEmployees").value(2));
    }

    @Test
    void developerCanAccessDashboardStats() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyOrganizationReturnsZeroCounts() throws Exception {
        // Use a fresh org with no users beyond the admin already created; counts should be >=0 and not crash.
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.openBugs").value(0));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }
}
