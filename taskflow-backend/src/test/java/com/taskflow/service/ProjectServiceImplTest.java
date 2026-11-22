package com.taskflow.service;

import com.taskflow.dto.project.AddProjectMemberRequest;
import com.taskflow.dto.project.CreateProjectRequest;
import com.taskflow.dto.project.ProjectDetailResponse;
import com.taskflow.dto.project.ProjectMemberResponse;
import com.taskflow.dto.project.UpdateProjectMemberRoleRequest;
import com.taskflow.dto.project.UpdateProjectStatusRequest;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.Team;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.SprintStatus;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.InvalidWorkflowTransitionException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private BugRepository bugRepository;
    @Mock
    private AuditLogWriter auditLogWriter;

    private ProjectSecurityEvaluator projectSecurityEvaluator;
    private ProjectServiceImpl projectService;

    private Organization orgA;
    private Organization orgB;
    private Role adminRole;
    private Role pmRole;
    private Role devRole;
    private Role qaRole;
    private User adminA;
    private User pmOneA;
    private User pmTwoA;
    private User devA;
    private User devB;

    @BeforeEach
    void setUp() {
        projectSecurityEvaluator = new ProjectSecurityEvaluator(projectRepository, projectMemberRepository);
        projectService = new ProjectServiceImpl(
                projectRepository,
                projectMemberRepository,
                organizationRepository,
                userRepository,
                teamRepository,
                teamMemberRepository,
                sprintRepository,
                taskRepository,
                bugRepository,
                projectSecurityEvaluator,
                auditLogWriter
        );

        orgA = new Organization("Org Alpha", "org-alpha", "Software", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgA, "id", 1L);

        orgB = new Organization("Org Beta", "org-beta", "Finance", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgB, "id", 2L);

        adminRole = new Role(RoleName.ADMIN, "Admin");
        pmRole = new Role(RoleName.PROJECT_MANAGER, "Project Manager");
        devRole = new Role(RoleName.DEVELOPER, "Developer");
        qaRole = new Role(RoleName.QA_TESTER, "QA Tester");

        adminA = buildUser(10L, orgA, "admin@alpha.test", adminRole, true);
        pmOneA = buildUser(11L, orgA, "pm1@alpha.test", pmRole, true);
        pmTwoA = buildUser(12L, orgA, "pm2@alpha.test", pmRole, true);
        devA = buildUser(13L, orgA, "dev@alpha.test", devRole, true);
        devB = buildUser(20L, orgB, "dev@beta.test", devRole, true);

        authenticateAs(adminA);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProject_WhenDeveloper_ShouldThrowUnauthorized() {
        authenticateAs(devA);
        CreateProjectRequest request = new CreateProjectRequest(
                "Unauthorized Project",
                "UNAUTH",
                "Should fail",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 1),
                ProjectStatus.ACTIVE,
                null,
                null,
                List.of()
        );

        assertThatThrownBy(() -> projectService.createProject(request))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void createProject_WhenDuplicateProjectKey_ShouldThrowDuplicateResourceException() {
        when(projectRepository.existsByOrganizationIdAndProjectKeyIgnoreCase(1L, "CORE")).thenReturn(true);

        CreateProjectRequest request = new CreateProjectRequest(
                "Core Platform",
                "core",
                "Duplicate key",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 1),
                ProjectStatus.ACTIVE,
                null,
                null,
                List.of()
        );

        assertThatThrownBy(() -> projectService.createProject(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("CORE");
    }

    @Test
    void createProject_WhenEndDateBeforeStartDate_ShouldThrowBusinessRuleException() {
        when(projectRepository.existsByOrganizationIdAndProjectKeyIgnoreCase(1L, "DATE1")).thenReturn(false);

        CreateProjectRequest request = new CreateProjectRequest(
                "Invalid Dates",
                "DATE1",
                "End before start",
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 5, 1),
                ProjectStatus.ACTIVE,
                null,
                null,
                List.of()
        );

        assertThatThrownBy(() -> projectService.createProject(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("end date");
    }

    @Test
    void createProject_WhenTeamFromDifferentOrganization_ShouldThrowUnauthorized() {
        Team orgBTeam = new Team(orgB, "Beta Team", "Other org", null);
        ReflectionTestUtils.setField(orgBTeam, "id", 88L);

        when(projectRepository.existsByOrganizationIdAndProjectKeyIgnoreCase(1L, "TEAMX")).thenReturn(false);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(orgA));
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminA));
        when(teamRepository.findWithLeadById(88L)).thenReturn(Optional.of(orgBTeam));

        CreateProjectRequest request = new CreateProjectRequest(
                "Cross Org Team Project",
                "TEAMX",
                "Should fail",
                LocalDate.of(2026, 1, 1),
                null,
                ProjectStatus.PLANNING,
                null,
                88L,
                List.of()
        );

        assertThatThrownBy(() -> projectService.createProject(request))
                .isInstanceOf(UnauthorizedResourceAccessException.class)
                .hasMessageContaining("Team does not belong");
    }

    @Test
    void addProjectMember_WhenMemberFromAnotherOrganization_ShouldThrowUnauthorized() {
        Project project = buildProject(100L, orgA, "TF", pmOneA, ProjectStatus.ACTIVE);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(project));
        when(userRepository.findById(20L)).thenReturn(Optional.of(devB));

        assertThatThrownBy(() -> projectService.addProjectMember(100L, new AddProjectMemberRequest(20L, RoleName.DEVELOPER)))
                .isInstanceOf(UnauthorizedResourceAccessException.class)
                .hasMessageContaining("does not belong to your organization");
    }

    @Test
    void addProjectMember_WhenAlreadyMember_ShouldThrowDuplicateResourceException() {
        Project project = buildProject(100L, orgA, "TF", pmOneA, ProjectStatus.ACTIVE);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(project));
        when(userRepository.findById(13L)).thenReturn(Optional.of(devA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 13L)).thenReturn(true);

        assertThatThrownBy(() -> projectService.addProjectMember(100L, new AddProjectMemberRequest(13L, RoleName.DEVELOPER)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void updateProjectStatus_WhenAnotherProjectManager_ShouldThrowUnauthorized() {
        authenticateAs(pmTwoA);
        Project pmOneProject = buildProject(100L, orgA, "TF", pmOneA, ProjectStatus.ACTIVE);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(pmOneProject));

        assertThatThrownBy(() -> projectService.updateProjectStatus(
                100L,
                new UpdateProjectStatusRequest(ProjectStatus.ON_HOLD)
        )).isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void updateProjectStatus_WhenInvalidTransition_ShouldThrowInvalidWorkflowTransitionException() {
        Project planningProject = buildProject(100L, orgA, "TF", pmOneA, ProjectStatus.PLANNING);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(planningProject));
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(planningProject));

        assertThatThrownBy(() -> projectService.updateProjectStatus(
                100L,
                new UpdateProjectStatusRequest(ProjectStatus.COMPLETED)
        )).isInstanceOf(InvalidWorkflowTransitionException.class);
    }

    @Test
    void archiveProject_WhenActiveSprintExists_ShouldThrowBusinessRuleException() {
        Project project = buildProject(100L, orgA, "TF", pmOneA, ProjectStatus.ACTIVE);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.countByProjectId(100L)).thenReturn(2L);
        when(sprintRepository.existsByProjectIdAndStatus(100L, SprintStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> projectService.archiveProject(100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("active sprint");
    }

    @Test
    void removeProjectMember_WhenMemberHasActiveTasks_ShouldThrowBusinessRuleException() {
        Project project = buildProject(100L, orgA, "TF", pmOneA, ProjectStatus.ACTIVE);
        ProjectMember membership = new ProjectMember(project, devA, RoleName.DEVELOPER, adminA);
        ReflectionTestUtils.setField(membership, "id", 500L);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(100L, 13L)).thenReturn(Optional.of(membership));
        when(taskRepository.existsByProjectIdAndAssigneeIdAndStatusIn(eq(100L), eq(13L), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> projectService.removeProjectMember(100L, 13L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tasks in progress or in review");
    }

    @Test
    void getProjectById_WhenDeveloperNotAssigned_ShouldThrowUnauthorized() {
        authenticateAs(devA);
        Project project = buildProject(100L, orgA, "TF", pmOneA, ProjectStatus.ACTIVE);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 13L)).thenReturn(false);

        assertThatThrownBy(() -> projectService.getProjectById(100L))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void getProjectById_WhenDeveloperAssigned_ShouldReturnDetailsWithProgress() {
        authenticateAs(devA);
        Project project = buildProject(100L, orgA, "TF", pmOneA, ProjectStatus.ACTIVE);
        ProjectMember devMembership = new ProjectMember(project, devA, RoleName.DEVELOPER, pmOneA);
        ReflectionTestUtils.setField(devMembership, "id", 501L);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 13L)).thenReturn(true);
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(100L)).thenReturn(List.of(devMembership));
        when(taskRepository.countByProjectId(100L)).thenReturn(4L);
        when(taskRepository.countByProjectIdAndStatus(eq(100L), any())).thenReturn(3L);
        when(taskRepository.countOverdueByProjectId(eq(100L), any())).thenReturn(1L);
        when(bugRepository.countByProjectIdAndStatusIn(eq(100L), anyCollection())).thenReturn(2L);

        ProjectDetailResponse response = projectService.getProjectById(100L);

        assertThat(response.projectKey()).isEqualTo("TF");
        assertThat(response.progressPercent()).isEqualTo(75);
        assertThat(response.openBugs()).isEqualTo(2L);
        assertThat(response.members()).hasSize(1);
    }

    @Test
    void updateProjectMemberRole_WhenValid_ShouldUpdateRoleAndWriteAuditLog() {
        Project project = buildProject(100L, orgA, "TF", pmOneA, ProjectStatus.ACTIVE);
        ProjectMember membership = new ProjectMember(project, devA, RoleName.DEVELOPER, pmOneA);
        ReflectionTestUtils.setField(membership, "id", 502L);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(100L, 13L)).thenReturn(Optional.of(membership));
        when(projectMemberRepository.save(membership)).thenReturn(membership);
        when(userRepository.getReferenceById(10L)).thenReturn(adminA);

        ProjectMemberResponse response = projectService.updateProjectMemberRole(
                100L,
                13L,
                new UpdateProjectMemberRoleRequest(RoleName.QA_TESTER)
        );

        assertThat(response.projectRole()).isEqualTo(RoleName.QA_TESTER);
        verify(auditLogWriter).record(
                eq(orgA),
                eq(adminA),
                eq(project),
                eq(AuditAction.PROJECT_MEMBER_ROLE_UPDATED),
                eq("PROJECT_MEMBER"),
                eq(502L),
                any(),
                any()
        );
    }

    private User buildUser(Long id, Organization org, String email, Role role, boolean active) {
        User user = new User(org, "EMP-" + id, email, "encoded-hash", "User " + id, "Staff", role);
        user.setActive(active);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Project buildProject(Long id, Organization org, String key, User manager, ProjectStatus status) {
        Project project = new Project(
                org,
                key,
                "Project " + key,
                "Description",
                manager,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
        project.setStatus(status);
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private void authenticateAs(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
