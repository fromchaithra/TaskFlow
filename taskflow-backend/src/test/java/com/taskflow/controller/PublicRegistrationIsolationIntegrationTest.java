package com.taskflow.controller;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public registration must create independent tenants with isolated ADMINs,
 * and must never grant access to another organization's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicRegistrationIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }

    private User register(String name, String email) throws Exception {
        mockMvc.perform(post("/api/v1/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"StrongPass123!",
                                 "confirmPassword":"StrongPass123!","organizationName":"%s Workspace"}
                                """.formatted(name, email, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    @Test
    void threeVisitorsRegisterIntoThreeIndependentOrganizations() throws Exception {
        User adminA = register("Admin A", "a@public.test");
        User adminB = register("Admin B", "b@public.test");
        User adminC = register("Admin C", "c@public.test");

        assertThat(adminA.hasRole(RoleName.ADMIN)).isTrue();
        assertThat(adminB.hasRole(RoleName.ADMIN)).isTrue();
        assertThat(adminC.hasRole(RoleName.ADMIN)).isTrue();

        Long orgAId = adminA.getOrganization().getId();
        Long orgBId = adminB.getOrganization().getId();
        Long orgCId = adminC.getOrganization().getId();
        assertThat(orgAId).isNotEqualTo(orgBId).isNotEqualTo(orgCId);
        assertThat(orgBId).isNotEqualTo(orgCId);

        Organization orgA = organizationRepository.findById(orgAId).orElseThrow();
        assertThat(orgA.getName()).isEqualTo("Admin A Workspace");
        assertThat(orgA.getSlug()).isNotBlank();
        assertThat(organizationRepository.findBySlug(orgA.getSlug())).isPresent();
        assertThat(userRepository.countByRole(RoleName.ADMIN)).isEqualTo(3L);
        assertThat(adminA.getEmployeeCode()).isEqualTo("EMP-0001");
        assertThat(adminB.getEmployeeCode()).isEqualTo("EMP-0001");
    }

    @Test
    void adminCannotReadAnotherOrganizationsUsers() throws Exception {
        User adminA = register("Admin A", "a@public.test");
        User adminB = register("Admin B", "b@public.test");

        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='b@public.test')]").doesNotExist());

        mockMvc.perform(get("/api/v1/users/" + adminB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotViewOrReportOnAnotherOrganizationsProject() throws Exception {
        User adminA = register("Admin A", "a@public.test");
        User adminB = register("Admin B", "b@public.test");

        Organization orgB = organizationRepository.findById(adminB.getOrganization().getId()).orElseThrow();
        Project projectB = projectRepository.save(new Project(
                orgB, "BPRJ", "B Project", "Org B only", adminB,
                LocalDate.now(), LocalDate.now().plusDays(30)));

        mockMvc.perform(get("/api/v1/projects/" + projectB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"EXECUTIVE_OVERVIEW","format":"PDF","query":{
                                 "from":"2026-01-01","to":"2026-03-31","granularity":"WEEK",
                                 "projectIds":[%d],"teamIds":[],"employeeIds":[]}}
                                """.formatted(projectB.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotSelfPromoteAndAdminCanAddSecondAdminInOwnOrganization() throws Exception {
        User adminA = register("Admin A", "a@public.test");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Dev A","email":"dev@public.test",
                                 "password":"StrongPass123!","roleName":"DEVELOPER"}
                                """))
                .andExpect(status().isCreated());

        User dev = userRepository.findByEmailIgnoreCase("dev@public.test").orElseThrow();
        mockMvc.perform(patch("/api/v1/users/" + dev.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dev))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Admin Two","email":"admin2@public.test",
                                 "password":"StrongPass123!","roleName":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User adminTwo = userRepository.findByEmailIgnoreCase("admin2@public.test").orElseThrow();
        assertThat(adminTwo.getOrganization().getId()).isEqualTo(adminA.getOrganization().getId());
        assertThat(userRepository.countActiveByRole(adminA.getOrganization().getId(), RoleName.ADMIN))
                .isEqualTo(2L);
    }

    @Test
    void unauthenticatedVisitorCannotReadOrganizationDataOrJoinAnExistingTenant() throws Exception {
        User adminA = register("Admin A", "a@public.test");

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Sneaky","email":"sneaky@public.test",
                                 "password":"StrongPass123!","confirmPassword":"StrongPass123!",
                                 "organizationId":%d,"roleName":"DEVELOPER"}
                                """.formatted(adminA.getOrganization().getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User sneaky = userRepository.findByEmailIgnoreCase("sneaky@public.test").orElseThrow();
        assertThat(sneaky.getOrganization().getId()).isNotEqualTo(adminA.getOrganization().getId());
    }
}
