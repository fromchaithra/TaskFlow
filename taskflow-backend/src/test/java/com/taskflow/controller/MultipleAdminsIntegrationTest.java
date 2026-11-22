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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Multiple ADMIN users in one organization must work without weakening security. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultipleAdminsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Organization orgA;
    private Organization orgB;
    private User adminA;
    private User adminB;
    private User developerA;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        orgA = organizationRepository.save(
                new Organization("Org A", "multi-admin-a", "Software", "UTC", "MON-FRI"));
        orgB = organizationRepository.save(
                new Organization("Org B", "multi-admin-b", "Finance", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role pmRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));

        adminA = user(orgA, "EMP-A1", "admin-a@multi.test", adminRole);
        adminB = user(orgB, "EMP-B1", "admin-b@multi.test", adminRole);
        developerA = user(orgA, "EMP-A2", "dev-a@multi.test", devRole);
        assertThat(pmRole).isNotNull();
    }

    private User user(Organization org, String code, String email, Role role) {
        return userRepository.save(new User(org, code, email,
                passwordEncoder.encode("StrongPass123!"), email, "Test user", role));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }

    private String createAdminBody(String name, String email, Long ignoredOrgId) {
        return """
                {"fullName":"%s","email":"%s","password":"StrongPass123!",
                 "roleName":"ADMIN","jobTitle":"Administrator","organizationId":%s}
                """.formatted(name, email, ignoredOrgId == null ? "null" : ignoredOrgId);
    }

    @Test
    void publicRegistrationStaysOpenAndCreatesANewTenantWhenAdminsAlreadyExist() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminExists").value(true))
                .andExpect(jsonPath("$.firstAdminRegistrationAllowed").value(true));

        mockMvc.perform(post("/api/v1/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Public Admin","email":"public@multi.test",
                                 "password":"StrongPass123!","confirmPassword":"StrongPass123!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User publicAdmin = userRepository.findByEmailIgnoreCase("public@multi.test").orElseThrow();
        Long publicOrgId = publicAdmin.getOrganization().getId();
        assertThat(publicOrgId).isNotEqualTo(orgA.getId());
        assertThat(publicOrgId).isNotEqualTo(orgB.getId());
        Organization publicOrg = organizationRepository.findById(publicOrgId).orElseThrow();
        assertThat(publicOrg.getSlug()).isNotEqualTo(orgA.getSlug());
        assertThat(publicOrg.getSlug()).isNotEqualTo(orgB.getSlug());
    }

    @Test
    void authenticatedAdminCanCreateSecondAndThirdAdminInSameOrganization() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAdminBody("Admin Two", "admin-two@multi.test", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAdminBody("Admin Three", "admin-three@multi.test", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(userRepository.countActiveByRole(orgA.getId(), RoleName.ADMIN)).isEqualTo(3L);
        assertThat(userRepository.findByEmailIgnoreCase("admin-two@multi.test").orElseThrow()
                .getOrganization().getId()).isEqualTo(orgA.getId());
        assertThat(userRepository.findByEmailIgnoreCase("admin-three@multi.test").orElseThrow()
                .getOrganization().getId()).isEqualTo(orgA.getId());
    }

    @Test
    void invitedAdminCanActivateAndLogIn() throws Exception {
        MvcResult invitation = mockMvc.perform(post("/api/v1/users/invite")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Invited Admin","email":"invited-admin@multi.test",
                                 "password":"ignored","roleName":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.activationLink").isNotEmpty())
                .andReturn();

        String link = invitation.getResponse().getContentAsString()
                .replaceAll(".*activationLink\":\"([^\"]+)\".*", "$1").replace("\\u0026", "&");
        String token = link.substring(link.indexOf("token=") + 6);
        User invited = userRepository.findByEmailIgnoreCase("invited-admin@multi.test").orElseThrow();
        assertThat(invited.isActive()).isFalse();

        mockMvc.perform(post("/api/v1/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invited-admin@multi.test","password":"StrongPass123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void bothAdminsCanLogInAndAccessOrganizationResources() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAdminBody("Admin Two", "admin-two@multi.test", null)))
                .andExpect(status().isCreated());
        User adminTwo = userRepository.findByEmailIgnoreCase("admin-two@multi.test").orElseThrow();

        for (User admin : new User[]{adminA, adminTwo}) {
            String token = "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(admin));
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + admin.getEmail()
                                    + "\",\"password\":\"StrongPass123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.role").value("ADMIN"));

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/dashboard/stats")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/live/analytics/snapshot")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.taskKpis").exists());
            mockMvc.perform(get("/api/v1/reports/catalog")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void employeeCannotCreateAdminOrPromoteSelf() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAdminBody("Illegal Admin", "illegal@multi.test", null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/users/" + developerA.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(developerA.getId()).orElseThrow().getPrimaryRoleName())
                .isEqualTo(RoleName.DEVELOPER);
        assertThat(userRepository.existsByEmailIgnoreCase("illegal@multi.test")).isFalse();
    }

    @Test
    void crossOrganizationAdminCreationIsRejectedAndOrganizationIsForced() throws Exception {
        // A client-supplied organizationId is ignored: the user always belongs
        // to the authenticated actor's organization.
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAdminBody("Org B Second", "admin-b2@multi.test", orgA.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(userRepository.findByEmailIgnoreCase("admin-b2@multi.test").orElseThrow()
                .getOrganization().getId()).isEqualTo(orgB.getId());

        // An admin of org A cannot inspect or modify an org B user.
        mockMvc.perform(get("/api/v1/users/" + adminB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/users/" + adminB.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"DEVELOPER\"}"))
                .andExpect(status().isNotFound());
        assertThat(userRepository.findById(adminB.getId()).orElseThrow().getPrimaryRoleName())
                .isEqualTo(RoleName.ADMIN);
    }

    @Test
    void organizationIsolationRemainsIntactWithMultipleAdmins() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAdminBody("Admin Two", "admin-two@multi.test", null)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(get("/api/v1/users?size=100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("admin-a@multi.test", "dev-a@multi.test", "admin-two@multi.test")
                .doesNotContain("admin-b@multi.test");

        String snapshot = mockMvc.perform(get("/api/v1/live/analytics/snapshot")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(snapshot).contains("\"recentActivity\":[]");
    }

    @Test
    void singleAdminOrganizationStillEnforcesLastAdminGuards() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + adminA.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(patch("/api/v1/users/" + adminA.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"DEVELOPER\"}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(userRepository.countActiveByRole(orgA.getId(), RoleName.ADMIN)).isEqualTo(1L);

        // With a second admin present, demoting the first is allowed.
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAdminBody("Admin Two", "admin-two@multi.test", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/users/" + adminA.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"DEVELOPER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
        assertThat(userRepository.countActiveByRole(orgA.getId(), RoleName.ADMIN)).isEqualTo(1L);
    }
}
