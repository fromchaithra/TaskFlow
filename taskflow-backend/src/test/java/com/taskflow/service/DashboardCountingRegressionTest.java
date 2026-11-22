package com.taskflow.service;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.TaskRepository;
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

/** Project-member joins must not inflate the dashboard "due today" count. */
@SpringBootTest
@ActiveProfiles("test")
class DashboardCountingRegressionTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private DashboardService dashboardService;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        Organization org = organizationRepository.save(
                new Organization("Org", "dashboard-count-org", "Software", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        Role pmRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        Role qaRole = roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));

        User admin = user(org, "EMP-1", "admin@dash.test", adminRole);
        User a = user(org, "EMP-2", "a@dash.test", devRole);
        User b = user(org, "EMP-3", "b@dash.test", qaRole);
        assertThat(pmRole).isNotNull();
        assertThat(a).isNotNull();
        assertThat(b).isNotNull();

        Project project = projectRepository.save(new Project(org, "DASH", "Project", "", admin,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
        projectMemberRepository.save(new ProjectMember(project, admin, RoleName.ADMIN, admin));
        projectMemberRepository.save(new ProjectMember(project, a, RoleName.DEVELOPER, admin));
        projectMemberRepository.save(new ProjectMember(project, b, RoleName.QA_TESTER, admin));

        taskRepository.save(new Task(project, "DASH-1", "Due today", "desc",
                com.taskflow.enums.PriorityLevel.MEDIUM, admin, admin, null, null, null, LocalDate.now()));

        CustomUserDetails principal = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user(Organization org, String code, String email, Role role) {
        return userRepository.save(new User(org, code, email,
                passwordEncoder.encode("StrongPass123!"), email, "Test", role));
    }

    @Test
    void dueTodayCountsEachTaskOnceDespiteMultipleProjectMembers() {
        assertThat(dashboardService.getStats().tasksDueToday()).isEqualTo(1L);
    }
}
