package com.taskflow.service.impl;

import com.taskflow.dto.setup.FirstAdminRegistrationRequest;
import com.taskflow.dto.setup.SetupStatusResponse;
import com.taskflow.dto.user.UserResponse;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.FirstAdminProvisioningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Public registration of an independent organization and its first ADMIN.
 *
 * <p>Every successful registration creates a brand new organization and the
 * registering user as that organization's ADMIN. No organization id or role is
 * accepted from the client: the organization is created server-side, the ADMIN
 * role is resolved from the database, and the unique slug is generated here.
 * Organization creation and user creation share one transaction, so a failure
 * never leaves a partially provisioned tenant.</p>
 */
@Service
public class FirstAdminProvisioningServiceImpl implements FirstAdminProvisioningService {

    private static final String DEFAULT_TIMEZONE = "UTC";
    private static final String DEFAULT_WORKING_DAYS = "MON,TUE,WED,THU,FRI";

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogWriter auditLogWriter;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public FirstAdminProvisioningServiceImpl(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditLogWriter auditLogWriter,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder
    ) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditLogWriter = auditLogWriter;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public SetupStatusResponse getSetupStatus() {
        // Public registration is always available: every visitor creates their own
        // organization. adminExists is kept as truthful informational status only.
        boolean adminExists = userRepository.countByRole(RoleName.ADMIN) > 0;
        return new SetupStatusResponse(adminExists, true);
    }

    @Override
    @Transactional
    public UserResponse registerFirstAdmin(FirstAdminRegistrationRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Registration request is required.");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An employee with email " + email + " already exists.");
        }

        Organization organization = organizationRepository.save(new Organization(
                resolveOrganizationName(request),
                generateUniqueSlug(request),
                null,
                DEFAULT_TIMEZONE,
                DEFAULT_WORKING_DAYS
        ));

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing. Verify Flyway migrations."));

        String employeeCode = generateEmployeeCode(organization.getId());
        User admin = new User(
                organization,
                employeeCode,
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                "Organization Administrator",
                adminRole
        );
        User saved = userRepository.save(admin);

        auditLogWriter.record(
                organization,
                null,
                null,
                AuditAction.USER_CREATED,
                "USER",
                saved.getId(),
                null,
                Map.of("email", saved.getEmail(), "role", RoleName.ADMIN.name(),
                        "employeeCode", employeeCode, "organizationId", organization.getId(),
                        "publicRegistration", true)
        );
        return UserResponse.from(saved);
    }

    /** Organization name is presentation metadata supplied by the registrant, never an id or role. */
    private String resolveOrganizationName(FirstAdminRegistrationRequest request) {
        String supplied = request.organizationName();
        if (supplied != null && !supplied.trim().isEmpty()) {
            String trimmed = supplied.trim();
            return trimmed.length() > 150 ? trimmed.substring(0, 150) : trimmed;
        }
        String fullName = request.fullName() == null ? "Organization" : request.fullName().trim();
        String derived = fullName + "'s Organization";
        return derived.length() > 150 ? derived.substring(0, 150) : derived;
    }

    /** Slugs are always generated here so a client cannot target an existing tenant. */
    private String generateUniqueSlug(FirstAdminRegistrationRequest request) {
        String source = resolveOrganizationName(request).toLowerCase(Locale.ROOT);
        String base = source.replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "organization";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60).replaceAll("-+$", "");
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = base + "-" + UUID.randomUUID().toString().substring(0, 8);
            if (organizationRepository.findBySlug(candidate).isEmpty()) {
                return candidate;
            }
        }
        return base + "-" + System.currentTimeMillis();
    }

    /** Employee codes are unique per organization, so each new tenant starts at EMP-0001. */
    private String generateEmployeeCode(Long orgId) {
        if (!userRepository.existsByOrganizationIdAndEmployeeCode(orgId, "EMP-0001")) {
            return "EMP-0001";
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "EMP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
            if (!userRepository.existsByOrganizationIdAndEmployeeCode(orgId, candidate)) {
                return candidate;
            }
        }
        return "EMP-" + System.currentTimeMillis();
    }
}
