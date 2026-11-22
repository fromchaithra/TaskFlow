package com.taskflow.service;

import com.taskflow.dto.user.UpdateUserRoleRequest;
import com.taskflow.dto.user.UpdateUserStatusRequest;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Status/role changes must be Admin-only at the service layer as well. */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceAuthorizationIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Organization org;
    private User admin;
    private User developer;
    private User target;
    private User foreignTarget;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        org = organizationRepository.save(new Organization("Auth Org", "user-auth-org", "Software", "UTC", "MON-FRI"));
        Organization foreign = organizationRepository.save(new Organization("Foreign", "user-auth-foreign", "Finance", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));
        admin = user(org, "EMP-A1", "admin@userauth.test", adminRole);
        developer = user(org, "EMP-A2", "dev@userauth.test", devRole);
        target = user(org, "EMP-A3", "target@userauth.test", devRole);
        foreignTarget = user(foreign, "EMP-B1", "foreign@userauth.test", devRole);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user(Organization organization, String code, String email, Role role) {
        return userRepository.save(new User(organization, code, email,
                passwordEncoder.encode("StrongPass123!"), email, "Test", role));
    }

    private void authenticate(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void adminCanChangeStatusAndRole() {
        authenticate(admin);
        assertThat(userService.updateUserStatus(target.getId(), new UpdateUserStatusRequest(false)).active()).isFalse();
        assertThat(userService.updateUserRole(target.getId(), new UpdateUserRoleRequest(RoleName.PROJECT_MANAGER)).role())
                .isEqualTo(RoleName.PROJECT_MANAGER);
    }

    @Test
    void employeeCannotChangeStatusOrRole() {
        authenticate(developer);
        assertThatThrownBy(() -> userService.updateUserStatus(target.getId(), new UpdateUserStatusRequest(false)))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
        assertThatThrownBy(() -> userService.updateUserRole(target.getId(), new UpdateUserRoleRequest(RoleName.ADMIN)))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void crossOrganizationTargetIsRejected() {
        authenticate(admin);
        assertThatThrownBy(() -> userService.updateUserStatus(foreignTarget.getId(), new UpdateUserStatusRequest(false)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> userService.updateUserRole(foreignTarget.getId(), new UpdateUserRoleRequest(RoleName.ADMIN)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void employeeCannotSelfPromoteThroughService() {
        authenticate(developer);
        assertThatThrownBy(() -> userService.updateUserRole(developer.getId(), new UpdateUserRoleRequest(RoleName.ADMIN)))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
        assertThat(userRepository.findById(developer.getId()).orElseThrow().getPrimaryRoleName())
                .isEqualTo(RoleName.DEVELOPER);
    }
}
