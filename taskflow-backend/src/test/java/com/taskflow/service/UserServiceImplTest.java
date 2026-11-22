package com.taskflow.service;

import com.taskflow.dto.user.UpdateUserRequest;
import com.taskflow.dto.user.UpdateUserRoleRequest;
import com.taskflow.dto.user.UpdateUserStatusRequest;
import com.taskflow.dto.user.UserResponse;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.ActivationTokenRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private ActivationTokenRepository activationTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogWriter auditLogWriter;

    private UserServiceImpl userService;
    private Organization organization;
    private Role adminRole;
    private Role developerRole;
    private Role pmRole;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userRepository,
                roleRepository,
                organizationRepository,
                activationTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                auditLogWriter
        );

        organization = new Organization("TaskFlow Enterprise", "taskflow", "Software", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(organization, "id", 1L);

        adminRole = new Role(RoleName.ADMIN, "Admin");
        developerRole = new Role(RoleName.DEVELOPER, "Developer");
        pmRole = new Role(RoleName.PROJECT_MANAGER, "Project Manager");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateUserRole_WhenRoleChanges_ShouldRevokeRefreshTokensAndWriteAuditLog() {
        User admin = buildUser(1L, "admin@taskflow.test", adminRole);
        User developer = buildUser(2L, "dev@taskflow.test", developerRole);
        authenticateAs(admin);

        when(userRepository.findById(2L)).thenReturn(Optional.of(developer));
        when(roleRepository.findByName(RoleName.PROJECT_MANAGER)).thenReturn(Optional.of(pmRole));
        when(userRepository.save(developer)).thenReturn(developer);
        when(userRepository.getReferenceById(1L)).thenReturn(admin);

        UserResponse response = userService.updateUserRole(2L, new UpdateUserRoleRequest(RoleName.PROJECT_MANAGER));

        assertThat(response.role()).isEqualTo(RoleName.PROJECT_MANAGER);
        verify(refreshTokenRepository).revokeAllActiveForUser(2L);
        verify(auditLogWriter).record(
                eq(organization),
                eq(admin),
                eq(null),
                eq(AuditAction.USER_ROLE_CHANGED),
                eq("USER"),
                eq(2L),
                any(),
                any()
        );
    }

    @Test
    void updateUserRole_WhenSameRoleRequested_ShouldNotRevokeTokensOrWriteAuditLog() {
        User admin = buildUser(1L, "admin@taskflow.test", adminRole);
        User developer = buildUser(2L, "dev@taskflow.test", developerRole);
        authenticateAs(admin);

        when(userRepository.findById(2L)).thenReturn(Optional.of(developer));

        UserResponse response = userService.updateUserRole(2L, new UpdateUserRoleRequest(RoleName.DEVELOPER));

        assertThat(response.role()).isEqualTo(RoleName.DEVELOPER);
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any());
        verify(auditLogWriter, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateUserRole_WhenDemotingLastActiveAdmin_ShouldThrowBusinessRuleException() {
        User admin = buildUser(1L, "admin@taskflow.test", adminRole);
        authenticateAs(admin);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countActiveByRole(1L, RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateUserRole(1L, new UpdateUserRoleRequest(RoleName.DEVELOPER)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("last active organization administrator");

        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any());
    }

    @Test
    void updateUser_WhenDeveloperTargetsAnotherUser_ShouldThrowUnauthorized() {
        User developer = buildUser(2L, "dev@taskflow.test", developerRole);
        authenticateAs(developer);

        assertThatThrownBy(() -> userService.updateUser(3L, new UpdateUserRequest("Tampered Name", "Lead", "#08BBD4")))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void updateUserStatus_WhenAdminDeactivatesSelf_ShouldThrowBusinessRuleException() {
        User admin = buildUser(1L, "admin@taskflow.test", adminRole);
        authenticateAs(admin);

        assertThatThrownBy(() -> userService.updateUserStatus(1L, new UpdateUserStatusRequest(false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own administrator account");
    }

    private User buildUser(Long id, String email, Role role) {
        User user = new User(organization, "EMP-" + id, email, "encoded-hash", "User " + id, "Engineer", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void authenticateAs(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
