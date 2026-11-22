package com.taskflow.config;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the sample development employees (EMP-0002 .. EMP-0006).
 *
 * <p>Runs only under the {@code dev} profile, after {@link BootstrapAdminRunner},
 * and is idempotent: an employee is skipped when their email already exists, so
 * restarts never create duplicates. Passwords are hashed with the application's
 * {@link PasswordEncoder} (BCrypt) exactly like {@code BootstrapAdminRunner}.</p>
 */
@Component
@Profile("dev")
@Order(100)
public class DevEmployeeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevEmployeeSeeder.class);

    private record SeedEmployee(
            String employeeCode,
            String email,
            String fullName,
            String jobTitle,
            RoleName role
    ) {
    }

    private static final List<SeedEmployee> SAMPLE_EMPLOYEES = List.of(
            new SeedEmployee("EMP-0002", "manager@taskflow.com", "Priya Menon", "Project Manager", RoleName.PROJECT_MANAGER),
            new SeedEmployee("EMP-0003", "developer1@taskflow.com", "Arjun Rao", "Software Engineer", RoleName.DEVELOPER),
            new SeedEmployee("EMP-0004", "developer2@taskflow.com", "Sneha Kulkarni", "Software Engineer", RoleName.DEVELOPER),
            new SeedEmployee("EMP-0005", "qa@taskflow.com", "Rahul Verma", "QA Engineer", RoleName.QA_TESTER),
            new SeedEmployee("EMP-0006", "developer3@taskflow.com", "Divya Nair", "Software Engineer", RoleName.DEVELOPER)
    );

    private final DevEmployeeSeedProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    public DevEmployeeSeeder(
            DevEmployeeSeedProperties properties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Dev employee seeding is disabled (taskflow.dev-seed.enabled=false).");
            return;
        }
        if (properties.password() == null || properties.password().isBlank()) {
            log.warn("Dev employee seeding skipped: taskflow.dev-seed.password is not set.");
            return;
        }

        Organization organization = organizationRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "Default organization missing. Verify Flyway migrations."));

        String passwordHash = passwordEncoder.encode(properties.password());
        int created = 0;
        int skipped = 0;

        for (SeedEmployee seed : SAMPLE_EMPLOYEES) {
            if (userRepository.existsByEmailIgnoreCase(seed.email())) {
                skipped++;
                continue;
            }
            if (userRepository.existsByOrganizationIdAndEmployeeCode(
                    organization.getId(), seed.employeeCode())) {
                log.warn("Employee code {} is already taken by another account; skipping {}.",
                        seed.employeeCode(), seed.email());
                skipped++;
                continue;
            }

            Role role = roleRepository.findByName(seed.role())
                    .orElseThrow(() -> new IllegalStateException(
                            "Role missing: " + seed.role() + ". Verify Flyway migrations."));

            User employee = new User(
                    organization,
                    seed.employeeCode(),
                    seed.email(),
                    passwordHash,
                    seed.fullName(),
                    seed.jobTitle(),
                    role
            );
            userRepository.save(employee);
            created++;
            log.info("Seeded dev employee {} ({}) with role {}.",
                    seed.email(), seed.employeeCode(), seed.role());
        }

        log.info("Dev employee seeding complete: {} created, {} already present.", created, skipped);
    }
}
