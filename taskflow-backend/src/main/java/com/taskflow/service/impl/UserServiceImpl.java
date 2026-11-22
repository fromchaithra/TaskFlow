package com.taskflow.service.impl;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.user.CreateUserRequest;
import com.taskflow.dto.user.InviteUserRequest;
import com.taskflow.dto.user.InvitationResponse;
import com.taskflow.entity.ActivationToken;
import com.taskflow.repository.ActivationTokenRepository;
import com.taskflow.util.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
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
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.UserService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogWriter auditLogWriter;
    @Value("${taskflow.app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    public UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            OrganizationRepository organizationRepository,
            ActivationTokenRepository activationTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuditLogWriter auditLogWriter
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsers(String search, RoleName role, Boolean active, Pageable pageable) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        String normalizedSearch = normalizeSearch(search);
        Boolean effectiveActive = viewer.isAdmin() ? active : Boolean.TRUE;

        Page<UserResponse> page = userRepository
                .searchUsers(viewer.getOrganizationId(), normalizedSearch, role, effectiveActive, pageable)
                .map(UserResponse::from);
        return PagedResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        if (!viewer.isAdmin() && !viewer.getId().equals(userId)) {
            throw new UnauthorizedResourceAccessException("You can only inspect your own profile.");
        }
        User target = findOrgUserOrThrow(userId, viewer.getOrganizationId());
        return UserResponse.from(target);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        // Creating an ADMIN (like any other user here) is reserved for an
        // authenticated administrator of this organization. The controller
        // enforces this too; keep the explicit check as defence in depth.
        if (!actorDetails.isAdmin()) {
            throw new UnauthorizedResourceAccessException("Only administrators can create users.");
        }
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An employee with email " + email + " already exists.");
        }

        Organization org = organizationRepository.findById(actorDetails.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        Role role = roleRepository.findByName(request.roleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.roleName()));

        String employeeCode = generateEmployeeCode(org.getId());
        User employee = new User(
                org,
                employeeCode,
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.jobTitle() != null ? request.jobTitle().trim() : null,
                role
        );
        if (request.avatarColor() != null && !request.avatarColor().isBlank()) {
            employee.setAvatarColor(request.avatarColor().trim());
        }

        User saved = userRepository.save(employee);
        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                org,
                actor,
                null,
                AuditAction.USER_CREATED,
                "USER",
                saved.getId(),
                null,
                Map.of("email", saved.getEmail(), "role", role.getName().name(), "employeeCode", employeeCode)
        );
        return UserResponse.from(saved);
    }

    @Override
    @Transactional
    public InvitationResponse inviteUser(InviteUserRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        if (!actorDetails.isAdmin()) {
            throw new UnauthorizedResourceAccessException("Only administrators can invite users.");
        }
        // An authenticated admin may invite another ADMIN into their own
        // organization. The organization below is always the actor's, never
        // taken from the request, so cross-organization creation is impossible.
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An employee with email " + email + " already exists.");
        }

        Organization org = organizationRepository.findById(actorDetails.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        Role role = roleRepository.findByName(request.roleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.roleName()));

        String employeeCode = generateEmployeeCode(org.getId());
        // Unusable random password; the employee sets their own during activation.
        String unusableHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());

        User employee = new User(
                org,
                employeeCode,
                email,
                unusableHash,
                request.fullName().trim(),
                request.jobTitle() != null ? request.jobTitle().trim() : null,
                role
        );
        employee.setActive(false);
        if (request.avatarColor() != null && !request.avatarColor().isBlank()) {
            employee.setAvatarColor(request.avatarColor().trim());
        }
        User saved = userRepository.save(employee);

        // Invalidate any prior pending tokens, then issue a fresh one.
        activationTokenRepository.invalidatePendingForUser(saved.getId());

        String rawToken = TokenHasher.generateRawToken(32);
        String tokenHash = TokenHasher.sha256Hex(rawToken);
        java.time.Instant expiresAt = java.time.Instant.now().plusSeconds(24 * 60 * 60);
        activationTokenRepository.save(new ActivationToken(saved, tokenHash, expiresAt));

        String activationLink = appBaseUrl + "/activate?token=" + rawToken;

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                org,
                actor,
                null,
                AuditAction.USER_CREATED,
                "USER",
                saved.getId(),
                null,
                java.util.Map.of("email", saved.getEmail(), "role", role.getName().name(),
                        "employeeCode", employeeCode, "invited", true)
        );

        return new InvitationResponse(UserResponse.from(saved), activationLink, expiresAt);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        if (!viewer.isAdmin() && !viewer.getId().equals(userId)) {
            throw new UnauthorizedResourceAccessException("Only administrators can modify another employee's details.");
        }

        User target = findOrgUserOrThrow(userId, viewer.getOrganizationId());
        Map<String, Object> before = Map.of(
                "fullName", target.getFullName(),
                "jobTitle", target.getJobTitle() != null ? target.getJobTitle() : ""
        );

        target.setFullName(request.fullName().trim());
        target.setJobTitle(request.jobTitle() != null ? request.jobTitle().trim() : null);
        if (request.avatarColor() != null && !request.avatarColor().isBlank()) {
            target.setAvatarColor(request.avatarColor().trim());
        }

        User saved = userRepository.save(target);
        User actor = userRepository.getReferenceById(viewer.getId());
        auditLogWriter.record(
                saved.getOrganization(),
                actor,
                null,
                AuditAction.USER_UPDATED,
                "USER",
                saved.getId(),
                before,
                Map.of("fullName", saved.getFullName(), "jobTitle", saved.getJobTitle() != null ? saved.getJobTitle() : "")
        );
        return UserResponse.from(saved);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        // Defence in depth: the controller already requires ADMIN, but internal callers must not
        // be able to bypass the role check by invoking the service directly.
        if (!actorDetails.isAdmin()) {
            throw new UnauthorizedResourceAccessException("Only administrators can change user status.");
        }
        if (actorDetails.getId().equals(userId) && Boolean.FALSE.equals(request.active())) {
            throw new BusinessRuleException("You cannot deactivate your own administrator account.");
        }

        User target = findOrgUserOrThrow(userId, actorDetails.getOrganizationId());
        boolean previousStatus = target.isActive();
        boolean nextStatus = Boolean.TRUE.equals(request.active());

        if (!nextStatus && target.hasRole(RoleName.ADMIN)) {
            long activeAdmins = userRepository.countActiveByRole(actorDetails.getOrganizationId(), RoleName.ADMIN);
            if (activeAdmins <= 1) {
                throw new BusinessRuleException("Cannot deactivate the last active organization administrator.");
            }
        }

        target.setActive(nextStatus);
        User saved = userRepository.save(target);

        if (!nextStatus) {
            refreshTokenRepository.revokeAllActiveForUser(saved.getId());
        }

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                saved.getOrganization(),
                actor,
                null,
                nextStatus ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                "USER",
                saved.getId(),
                Map.of("active", previousStatus),
                Map.of("active", nextStatus)
        );
        return UserResponse.from(saved);
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(Long userId, UpdateUserRoleRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        if (!actorDetails.isAdmin()) {
            throw new UnauthorizedResourceAccessException("Only administrators can change user roles.");
        }
        User target = findOrgUserOrThrow(userId, actorDetails.getOrganizationId());
        RoleName currentRole = target.getPrimaryRoleName();
        RoleName nextRoleName = request.roleName();

        if (currentRole == nextRoleName) {
            return UserResponse.from(target);
        }

        if (currentRole == RoleName.ADMIN && nextRoleName != RoleName.ADMIN) {
            long activeAdmins = userRepository.countActiveByRole(actorDetails.getOrganizationId(), RoleName.ADMIN);
            if (activeAdmins <= 1) {
                throw new BusinessRuleException("Cannot reassign the last active organization administrator.");
            }
        }

        Role nextRole = roleRepository.findByName(nextRoleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + nextRoleName));

        target.replaceRole(nextRole);
        User saved = userRepository.save(target);
        refreshTokenRepository.revokeAllActiveForUser(saved.getId());

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                saved.getOrganization(),
                actor,
                null,
                AuditAction.USER_ROLE_CHANGED,
                "USER",
                saved.getId(),
                Map.of("role", currentRole.name()),
                Map.of("role", nextRoleName.name())
        );
        return UserResponse.from(saved);
    }

    private User findOrgUserOrThrow(Long userId, Long orgId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
        if (!user.getOrganization().getId().equals(orgId)) {
            throw new ResourceNotFoundException("Employee not found: " + userId);
        }
        return user;
    }

    private String generateEmployeeCode(Long orgId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "EMP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            if (!userRepository.existsByOrganizationIdAndEmployeeCode(orgId, candidate)) {
                return candidate;
            }
        }
        return "EMP-" + System.currentTimeMillis();
    }

    private String normalizeSearch(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }
}
