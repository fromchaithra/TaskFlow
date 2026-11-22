package com.taskflow.controller;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.service.AnalyticsService;
import com.taskflow.service.TestRunService;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class P3ErrorHandlingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private JwtService jwtService;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private TestRunService testRunService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Project project;
    private Organization orgB;
    private User adminA;
    private User foreignUser;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        Organization orgA = organizationRepository.save(new Organization("Org A", "p3-org-a", "Software", "UTC", "MON-FRI"));
        orgB = organizationRepository.save(new Organization("Org B", "p3-org-b", "Finance", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));
        adminA = user(orgA, "EMP-A1", "admin-a@p3.test", adminRole);
        foreignUser = user(orgB, "EMP-B1", "dev-b@p3.test", devRole);
        project = projectRepository.save(new Project(orgA, "P3SECRET", "Project", "", adminA,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user(Organization org, String code, String email, Role role) {
        return userRepository.save(new User(org, code, email,
                passwordEncoder.encode("StrongPass123!"), email, "Test", role));
    }

    private void authenticate(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void crossOrganizationProjectErrorDoesNotRevealProjectKey() throws Exception {
        User adminB = user(orgB, "EMP-B2", "admin-b@p3.test",
                roleRepository.findAll().stream().filter(r -> r.getName() == RoleName.ADMIN)
                        .findFirst().orElseThrow());
        String token = jwtService.generateAccessToken(new CustomUserDetails(adminB));
        String body = mockMvc.perform(get("/api/v1/projects/{id}", project.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("P3SECRET");
    }

    @Test
    void crossOrganizationEmployeeErrorDoesNotRevealDatabaseId() {
        authenticate(adminA);
        String foreignId = String.valueOf(foreignUser.getId());
        assertThatThrownBy(() -> analyticsService.getEmployeeWorkload(new AnalyticsQuery(
                LocalDate.now().minusDays(7), LocalDate.now(), null, null,
                AnalyticsGranularity.WEEK, List.of(), List.of(), List.of(foreignUser.getId()))))
                .isInstanceOf(UnauthorizedResourceAccessException.class)
                .hasMessageNotContaining(foreignId);
    }

    @Test
    void invalidTestRunStatusReturnsClientErrorNotServerError() {
        authenticate(adminA);
        assertThatThrownBy(() -> testRunService.list(project.getId(), "BOGUS", null, Pageable.unpaged()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid test run status");
    }
}
