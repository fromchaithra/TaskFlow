package com.taskflow.service;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.EmployeeWorkloadResponse;
import com.taskflow.dto.analytics.TeamProductivityResponse;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.Task;
import com.taskflow.entity.Team;
import com.taskflow.entity.TeamMember;
import com.taskflow.entity.User;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Employee/team analytics filters must never widen project visibility. */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsProjectVisibilityIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private PasswordEncoder passwordEncoder;

    private final LocalDate today = LocalDate.now();

    private Organization orgA;
    private User adminA;
    private User developer;
    private User isolatedUser;
    private User foreignDeveloper;
    private Project accessibleProject;
    private Project inaccessibleProject;
    private Project foreignProject;
    private Team team;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        orgA = organizationRepository.save(new Organization("Org A", "viz-org-a", "Software", "UTC", "MON-FRI"));
        Organization orgB = organizationRepository.save(new Organization("Org B", "viz-org-b", "Finance", "UTC", "MON-FRI"));

        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role pmRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));

        adminA = user(orgA, "EMP-A1", "admin@viz.test", adminRole);
        User managerA = user(orgA, "EMP-A2", "manager@viz.test", pmRole);
        User managerA2 = user(orgA, "EMP-A3", "manager2@viz.test", pmRole);
        developer = user(orgA, "EMP-A4", "dev@viz.test", devRole);
        isolatedUser = user(orgA, "EMP-A5", "isolated@viz.test", devRole);
        foreignDeveloper = user(orgB, "EMP-B1", "dev-b@viz.test", devRole);

        accessibleProject = projectRepository.save(new Project(orgA, "VISA", "Accessible", "A", managerA,
                today.minusMonths(1), today.plusMonths(1)));
        inaccessibleProject = projectRepository.save(new Project(orgA, "VISB", "Inaccessible", "B", managerA2,
                today.minusMonths(1), today.plusMonths(1)));
        foreignProject = projectRepository.save(new Project(orgB, "VISC", "Foreign", "C", foreignDeveloper,
                today.minusMonths(1), today.plusMonths(1)));

        projectMemberRepository.save(new ProjectMember(accessibleProject, developer, RoleName.DEVELOPER, adminA));
        team = teamRepository.save(new Team(orgA, "Visible Team", "Team", managerA));
        teamMemberRepository.save(new TeamMember(team, developer));

        saveTask(accessibleProject, "VISA-1", developer, TaskStatus.IN_PROGRESS);
        saveTask(accessibleProject, "VISA-2", developer, TaskStatus.DONE);
        saveTask(inaccessibleProject, "VISB-1", developer, TaskStatus.IN_PROGRESS);
        saveTask(foreignProject, "VISC-1", foreignDeveloper, TaskStatus.IN_PROGRESS);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user(Organization org, String code, String email, Role role) {
        return userRepository.save(new User(org, code, email,
                passwordEncoder.encode("StrongPass123!"), email, "Test", role));
    }

    private void saveTask(Project project, String key, User assignee, TaskStatus status) {
        Task task = new Task(project, key, key, "desc", PriorityLevel.MEDIUM, assignee, assignee,
                null, null, BigDecimal.ONE, today.plusDays(1));
        task.setStatus(status);
        taskRepository.save(task);
    }

    private void authenticate(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private AnalyticsQuery query(List<Long> projectIds, List<Long> teamIds, List<Long> employeeIds) {
        return new AnalyticsQuery(today.minusDays(30), today, null, null,
                AnalyticsGranularity.WEEK, projectIds, teamIds, employeeIds);
    }

    private BigDecimal value(EmployeeWorkloadResponse response) {
        assertThat(response.rows()).hasSize(1);
        return response.rows().getFirst().assignedTasks().value();
    }

    @Test
    void employeeFilterRespectsProjectVisibility() {
        authenticate(developer);
        EmployeeWorkloadResponse response = analyticsService.getEmployeeWorkload(
                query(List.of(), List.of(), List.of(developer.getId())));
        assertThat(value(response)).isEqualByComparingTo("2");
    }

    @Test
    void teamFilterRespectsProjectVisibility() {
        authenticate(developer);
        TeamProductivityResponse response = analyticsService.getTeamProductivity(
                query(List.of(), List.of(team.getId()), List.of()));
        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().getFirst().activeTasks().value()).isEqualByComparingTo("1");
    }

    @Test
    void adminStillSeesAllOrganizationProjects() {
        authenticate(adminA);
        EmployeeWorkloadResponse response = analyticsService.getEmployeeWorkload(
                query(List.of(), List.of(), List.of(developer.getId())));
        assertThat(value(response)).isEqualByComparingTo("3");
    }

    @Test
    void employeeFromAnotherOrganizationIsRejected() {
        authenticate(developer);
        assertThatThrownBy(() -> analyticsService.getEmployeeWorkload(
                query(List.of(), List.of(), List.of(foreignDeveloper.getId()))))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void teamFromAnotherOrganizationIsRejected() {
        Team foreignTeam = teamRepository.save(new Team(
                organizationRepository.findById(foreignDeveloper.getOrganization().getId()).orElseThrow(),
                "Foreign Team", "Team", foreignDeveloper));
        authenticate(developer);
        assertThatThrownBy(() -> analyticsService.getTeamProductivity(
                query(List.of(), List.of(foreignTeam.getId()), List.of())))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void userWithNoProjectVisibilitySeesNoWorkload() {
        authenticate(isolatedUser);
        EmployeeWorkloadResponse response = analyticsService.getEmployeeWorkload(
                query(List.of(), List.of(), List.of(isolatedUser.getId())));
        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().getFirst().assignedTasks().availability())
                .isEqualTo(com.taskflow.dto.analytics.DataAvailability.NO_DATA_IN_RANGE);
    }

    @Test
    void explicitInaccessibleProjectFilterIsRejected() {
        authenticate(developer);
        assertThatThrownBy(() -> analyticsService.getEmployeeWorkload(
                query(List.of(inaccessibleProject.getId()), List.of(), List.of(developer.getId()))))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }
}
