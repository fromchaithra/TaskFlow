package com.taskflow.controller;

import com.taskflow.dto.setup.FirstAdminRegistrationRequest;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.service.FirstAdminProvisioningService;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SetupControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private FirstAdminProvisioningService provisioningService;

    private Organization organization;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        organization = organizationRepository.save(new Organization(
                "TaskFlow Enterprise", "setup-test", "Software", "UTC", "MON-FRI"));
        roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));
    }

    private static String registrationJson(String name, String email) {
        return """
                {"fullName":"%s","email":"%s","password":"StrongPass123!","confirmPassword":"StrongPass123!"}
                """.formatted(name, email);
    }

    @Test
    void statusIsOpenWhenNoAdminExists() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminExists").value(false))
                .andExpect(jsonPath("$.firstAdminRegistrationAllowed").value(true));
    }

    @Test
    void firstAdminCanRegisterAndLogin() throws Exception {
        mockMvc.perform(post("/api/v1/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("Alice Admin", "alice.admin@taskflow.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice.admin@taskflow.test"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User admin = userRepository.findByEmailIgnoreCase("alice.admin@taskflow.test").orElseThrow();
        // A public registration creates its own organization; it never joins the seeded one.
        assertThat(admin.getOrganization().getId()).isNotEqualTo(organization.getId());
        assertThat(admin.hasRole(RoleName.ADMIN)).isTrue();
        assertThat(organizationRepository.count()).isEqualTo(2L);
        assertThat(passwordEncoder.matches("StrongPass123!", admin.getPasswordHash())).isTrue();
        assertThat(userRepository.countByRole(RoleName.ADMIN)).isEqualTo(1L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice.admin@taskflow.test","password":"StrongPass123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void publicRegistrationCreatesIndependentOrganizationWhenAdminAlreadyExists() throws Exception {
        provisioningService.registerFirstAdmin(new FirstAdminRegistrationRequest(
                "First Admin", "first@taskflow.test", "StrongPass123!", "StrongPass123!"));
        User firstAdmin = userRepository.findByEmailIgnoreCase("first@taskflow.test").orElseThrow();

        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminExists").value(true))
                .andExpect(jsonPath("$.firstAdminRegistrationAllowed").value(true));

        mockMvc.perform(post("/api/v1/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("Second Admin", "second@taskflow.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("second@taskflow.test"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User secondAdmin = userRepository.findByEmailIgnoreCase("second@taskflow.test").orElseThrow();
        assertThat(secondAdmin.getOrganization().getId())
                .isNotEqualTo(firstAdmin.getOrganization().getId());
        assertThat(secondAdmin.getOrganization().getId()).isNotEqualTo(organization.getId());
        assertThat(secondAdmin.hasRole(RoleName.ADMIN)).isTrue();
        assertThat(userRepository.countByRole(RoleName.ADMIN)).isEqualTo(2L);
        assertThat(organizationRepository.count()).isEqualTo(3L);
    }

    @Test
    void duplicateEmailRegistrationIsRejectedWithoutCreatingAnOrganization() throws Exception {
        provisioningService.registerFirstAdmin(new FirstAdminRegistrationRequest(
                "First Admin", "duplicate@taskflow.test", "StrongPass123!", "StrongPass123!"));
        long orgsBefore = organizationRepository.count();

        mockMvc.perform(post("/api/v1/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("Other Admin", "DUPLICATE@taskflow.test")))
                .andExpect(status().isConflict());

        assertThat(organizationRepository.count()).isEqualTo(orgsBefore);
        assertThat(userRepository.countByRole(RoleName.ADMIN)).isEqualTo(1L);
    }

    @Test
    void clientSuppliedOrganizationIdAndRoleAreIgnored() throws Exception {
        mockMvc.perform(post("/api/v1/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Safe Admin","email":"safe@taskflow.test",
                                 "password":"StrongPass123!","confirmPassword":"StrongPass123!",
                                 "organizationId":%d,"organization_id":%d,"role":"DEVELOPER","roleName":"DEVELOPER"}
                                """.formatted(organization.getId(), organization.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User admin = userRepository.findByEmailIgnoreCase("safe@taskflow.test").orElseThrow();
        assertThat(admin.getOrganization().getId()).isNotEqualTo(organization.getId());
        assertThat(admin.hasRole(RoleName.ADMIN)).isTrue();
    }

    @Test
    void invalidRegistrationLeavesNoPartialOrganizationOrUser() throws Exception {
        long orgsBefore = organizationRepository.count();
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/api/v1/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"X","email":"not-an-email","password":"short",
                                 "confirmPassword":"different"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(organizationRepository.count()).isEqualTo(orgsBefore);
        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    void mismatchedPasswordsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Alice Admin","email":"alice@taskflow.test",
                                 "password":"StrongPass123!","confirmPassword":"Different123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void adminCanRegisterEmployeesAndAnotherAdmin() throws Exception {
        provisioningService.registerFirstAdmin(new FirstAdminRegistrationRequest(
                "Alice Admin", "alice.admin@taskflow.test", "StrongPass123!", "StrongPass123!"));
        User admin = userRepository.findByEmailIgnoreCase("alice.admin@taskflow.test").orElseThrow();
        String bearer = "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(admin));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Dana Developer","email":"dana.dev@taskflow.test",
                                 "password":"StrongPass123!","roleName":"DEVELOPER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DEVELOPER"));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Bob Admin","email":"bob.admin@taskflow.test",
                                 "password":"StrongPass123!","roleName":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        Long adminOrgId = admin.getOrganization().getId();
        User employee = userRepository.findByEmailIgnoreCase("dana.dev@taskflow.test").orElseThrow();
        assertThat(employee.getOrganization().getId()).isEqualTo(adminOrgId);
        assertThat(passwordEncoder.matches("StrongPass123!", employee.getPasswordHash())).isTrue();
        User secondAdmin = userRepository.findByEmailIgnoreCase("bob.admin@taskflow.test").orElseThrow();
        assertThat(secondAdmin.getOrganization().getId()).isEqualTo(adminOrgId);
        assertThat(secondAdmin.hasRole(RoleName.ADMIN)).isTrue();
        assertThat(userRepository.countActiveByRole(adminOrgId, RoleName.ADMIN)).isEqualTo(2L);
        assertThat(userRepository.countByRole(RoleName.ADMIN)).isEqualTo(2L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dana.dev@taskflow.test","password":"StrongPass123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("DEVELOPER"));
    }

    @Test
    void nonAdminCannotRegisterEmployees() throws Exception {
        provisioningService.registerFirstAdmin(new FirstAdminRegistrationRequest(
                "Alice Admin", "alice.admin@taskflow.test", "StrongPass123!", "StrongPass123!"));
        User admin = userRepository.findByEmailIgnoreCase("alice.admin@taskflow.test").orElseThrow();
        String adminBearer = "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(admin));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Peter PM","email":"peter.pm@taskflow.test",
                                 "password":"StrongPass123!","roleName":"PROJECT_MANAGER"}
                                """))
                .andExpect(status().isCreated());

        User pm = userRepository.findByEmailIgnoreCase("peter.pm@taskflow.test").orElseThrow();
        String pmBearer = "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(pm));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, pmBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Eve Dev","email":"eve.dev@taskflow.test",
                                 "password":"StrongPass123!","roleName":"DEVELOPER"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(userRepository.existsByEmailIgnoreCase("eve.dev@taskflow.test")).isFalse();
    }

    @Test
    void concurrentPublicRegistrationsCreateSeparateOrganizations() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        provisioningService.registerFirstAdmin(new FirstAdminRegistrationRequest(
                                "Admin " + index,
                                "concurrent" + index + "@taskflow.test",
                                "StrongPass123!",
                                "StrongPass123!"));
                        successes.incrementAndGet();
                    } catch (Exception ex) {
                        rejections.incrementAndGet();
                    }
                    return null;
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(2);
        assertThat(rejections.get()).isZero();
        assertThat(userRepository.countByRole(RoleName.ADMIN)).isEqualTo(2L);
        User first = userRepository.findByEmailIgnoreCase("concurrent0@taskflow.test").orElseThrow();
        User second = userRepository.findByEmailIgnoreCase("concurrent1@taskflow.test").orElseThrow();
        assertThat(first.getOrganization().getId()).isNotEqualTo(second.getOrganization().getId());
        assertThat(organizationRepository.count()).isEqualTo(3L); // seeded + two new
    }
}
