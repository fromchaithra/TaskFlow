package com.taskflow.service;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.TestRun;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.TestRunStatus;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.TestRunRepository;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test-run status changes must respect project visibility, not only organization. */
@SpringBootTest
@ActiveProfiles("test")
class TestRunAuthorizationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TestRunRepository testRunRepository;
    @Autowired private TestRunService testRunService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Organization org;
    private User admin;
    private User qaMember;
    private User qaNonMember;
    private User qaForeign;
    private TestRun run;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        org = organizationRepository.save(new Organization("Org", "run-auth-org", "Software", "UTC", "MON-FRI"));
        Organization foreign = organizationRepository.save(new Organization("Foreign", "run-auth-foreign", "Finance", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role pmRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        Role qaRole = roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));
        assertThat(pmRole).isNotNull();
        assertThat(devRole).isNotNull();

        admin = user(org, "EMP-A1", "admin@runauth.test", adminRole);
        qaMember = user(org, "EMP-A2", "qa-member@runauth.test", qaRole);
        qaNonMember = user(org, "EMP-A3", "qa-outsider@runauth.test", qaRole);
        qaForeign = user(foreign, "EMP-B1", "qa-foreign@runauth.test", qaRole);

        Project project = projectRepository.save(new Project(org, "RUN", "Project", "", admin,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
        projectMemberRepository.save(new ProjectMember(project, qaMember, RoleName.QA_TESTER, admin));
        run = testRunRepository.save(new TestRun(org, project, "QA Run", admin));
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
    void adminCanChangeStatus() {
        authenticate(admin);
        assertThat(testRunService.updateStatus(run.getId(), "IN_PROGRESS").status()).isEqualTo(TestRunStatus.IN_PROGRESS);
    }

    @Test
    void qaMemberOfProjectCanChangeStatus() {
        authenticate(qaMember);
        assertThat(testRunService.updateStatus(run.getId(), "IN_PROGRESS").status()).isEqualTo(TestRunStatus.IN_PROGRESS);
    }

    @Test
    void qaNonMemberCannotChangeStatus() {
        authenticate(qaNonMember);
        assertThatThrownBy(() -> testRunService.updateStatus(run.getId(), "IN_PROGRESS"))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
        assertThat(testRunRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(TestRunStatus.PLANNED);
    }

    @Test
    void qaFromAnotherOrganizationCannotChangeStatus() {
        authenticate(qaForeign);
        assertThatThrownBy(() -> testRunService.updateStatus(run.getId(), "IN_PROGRESS"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
