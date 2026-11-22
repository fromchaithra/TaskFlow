package com.taskflow.live;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.LiveEventCoalescer;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Verifies that live signals are emitted only for committed mutations. */
@SpringBootTest
@ActiveProfiles("test")
class LiveChangeEventIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogWriter auditLogWriter;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private LiveEventCoalescer coalescer;

    private Organization organization;
    private User actor;
    private Project project;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        organization = organizationRepository.save(
                new Organization("Live Org", "live-events", "Software", "UTC", "MON-FRI"));
        Role role = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        actor = userRepository.save(new User(organization, "EMP-L1", "live@taskflow.test",
                passwordEncoder.encode("StrongPass123!"), "Live Manager", "Manager", role));
        project = projectRepository.save(new Project(organization, "LIVE", "Live Project", "desc",
                actor, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    @Test
    void committedMutationEmitsMinimalAfterCommitEvent() {
        transaction().executeWithoutResult(status -> auditLogWriter.record(
                organization, actor, project, AuditAction.TASK_CREATED,
                "TASK", 42L, null, java.util.Map.of("status", "TODO")));

        verify(coalescer).enqueue(argThat(event ->
                event.organizationId().equals(organization.getId())
                        && event.projectId().equals(project.getId())
                        && "TASK".equals(event.entityType())
                        && 42L == event.entityId()
                        && "TASK_CREATED".equals(event.action())
                        && event.occurredAt() != null));
    }

    @Test
    void rolledBackMutationEmitsNoEvent() {
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            auditLogWriter.record(organization, actor, project, AuditAction.TASK_CREATED,
                    "TASK", 99L, null, null);
            throw new IllegalStateException("simulated rollback");
        })).isInstanceOf(IllegalStateException.class);

        verify(coalescer, never()).enqueue(any());
    }

    @Test
    void eventPayloadContainsNoSensitiveFields() {
        transaction().executeWithoutResult(status -> auditLogWriter.record(
                organization, actor, project, AuditAction.TASK_ASSIGNED,
                "TASK", 7L, null, java.util.Map.of("email", "secret@taskflow.test")));

        verify(coalescer).enqueue(argThat(event -> {
            String text = event.toString();
            return !text.contains("secret@taskflow.test")
                    && !text.contains("password")
                    && !text.contains("token")
                    && !text.contains("ipAddress");
        }));
    }
}
