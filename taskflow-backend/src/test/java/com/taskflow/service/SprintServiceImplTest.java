package com.taskflow.service;

import com.taskflow.dto.sprint.AddSprintTasksRequest;
import com.taskflow.dto.sprint.CreateSprintRequest;
import com.taskflow.dto.sprint.SprintDetailResponse;
import com.taskflow.dto.sprint.SprintReportResponse;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.Sprint;
import com.taskflow.entity.SprintTask;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.SprintStatus;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.InvalidWorkflowTransitionException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.SprintTaskRepository;
import com.taskflow.repository.TaskHistoryRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.impl.SprintServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SprintServiceImplTest {

    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private SprintTaskRepository sprintTaskRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskHistoryRepository taskHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private NotificationDispatcher notificationDispatcher;

    private SprintServiceImpl sprintService;

    private Organization orgA;
    private Organization orgB;
    private User adminA;
    private User pmA;
    private User devA;
    private User adminB;
    private Project projectA;
    private Project secondProjectA;
    private Project projectB;

    @BeforeEach
    void setUp() {
        ProjectSecurityEvaluator projectSecurityEvaluator =
                new ProjectSecurityEvaluator(projectRepository, projectMemberRepository);
        sprintService = new SprintServiceImpl(
                sprintRepository,
                sprintTaskRepository,
                projectRepository,
                projectMemberRepository,
                taskRepository,
                taskHistoryRepository,
                userRepository,
                projectSecurityEvaluator,
                auditLogWriter,
                notificationDispatcher
        );

        orgA = new Organization("Alpha Corp", "alpha-corp", "Software", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgA, "id", 1L);

        orgB = new Organization("Beta Corp", "beta-corp", "FinTech", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgB, "id", 2L);

        Role adminRole = new Role(RoleName.ADMIN, "Admin");
        Role pmRole = new Role(RoleName.PROJECT_MANAGER, "Project Manager");
        Role devRole = new Role(RoleName.DEVELOPER, "Developer");

        adminA = buildUser(10L, orgA, "admin@alpha.test", adminRole);
        pmA = buildUser(11L, orgA, "pm@alpha.test", pmRole);
        devA = buildUser(12L, orgA, "dev@alpha.test", devRole);
        adminB = buildUser(20L, orgB, "admin@beta.test", adminRole);

        projectA = buildProject(100L, orgA, "ALPHA", pmA);
        secondProjectA = buildProject(101L, orgA, "GAMMA", adminA);
        projectB = buildProject(200L, orgB, "BETA", adminB);

        authenticateAs(pmA);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSprint_WhenDeveloper_ShouldThrowUnauthorized() {
        authenticateAs(devA);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));

        CreateSprintRequest request = new CreateSprintRequest(
                "Sprint 1", "Goal", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 14)
        );

        assertThatThrownBy(() -> sprintService.createSprint(100L, request))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void createSprint_WhenInvalidDates_ShouldThrowBusinessRuleException() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(projectA));
        when(sprintRepository.existsByProjectIdAndNameIgnoreCase(100L, "Sprint Bad")).thenReturn(false);

        CreateSprintRequest request = new CreateSprintRequest(
                "Sprint Bad", "Reversed dates", LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 10)
        );

        assertThatThrownBy(() -> sprintService.createSprint(100L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("end date must be after start date");
    }

    @Test
    void createSprint_WhenOverlappingActiveSprint_ShouldThrowBusinessRuleException() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(projectA));
        when(sprintRepository.existsByProjectIdAndNameIgnoreCase(100L, "Sprint 2")).thenReturn(false);
        when(sprintRepository.existsOverlappingActiveSprint(
                100L, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 24), null
        )).thenReturn(true);

        CreateSprintRequest request = new CreateSprintRequest(
                "Sprint 2", "Overlaps active", LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 24)
        );

        assertThatThrownBy(() -> sprintService.createSprint(100L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void startSprint_WhenAlreadyCompletedOrCancelled_ShouldThrowInvalidWorkflowTransition() {
        Sprint cancelledSprint = buildSprint(300L, projectA, "Sprint X", SprintStatus.CANCELLED);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(projectA));
        when(sprintRepository.findByIdAndProjectId(300L, 100L)).thenReturn(Optional.of(cancelledSprint));

        assertThatThrownBy(() -> sprintService.startSprint(100L, 300L))
                .isInstanceOf(InvalidWorkflowTransitionException.class)
                .hasMessageContaining("Only PLANNED sprints");
    }

    @Test
    void startSprint_WhenAnotherActiveSprintExists_ShouldThrowBusinessRuleException() {
        Sprint plannedSprint = buildSprint(301L, projectA, "Sprint 2", SprintStatus.PLANNED);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(projectA));
        when(sprintRepository.findByIdAndProjectId(301L, 100L)).thenReturn(Optional.of(plannedSprint));
        when(sprintRepository.existsByProjectIdAndStatusAndIdNot(100L, SprintStatus.ACTIVE, 301L)).thenReturn(true);

        assertThatThrownBy(() -> sprintService.startSprint(100L, 301L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has an active sprint");
    }

    @Test
    void completeSprint_WhenPlanned_ShouldThrowInvalidWorkflowTransition() {
        Sprint plannedSprint = buildSprint(302L, projectA, "Sprint Planned", SprintStatus.PLANNED);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(sprintRepository.findByIdAndProjectId(302L, 100L)).thenReturn(Optional.of(plannedSprint));

        assertThatThrownBy(() -> sprintService.completeSprint(100L, 302L))
                .isInstanceOf(InvalidWorkflowTransitionException.class)
                .hasMessageContaining("Only an ACTIVE sprint");
    }

    @Test
    void addTasksToSprint_WhenTaskBelongsToAnotherProject_ShouldThrowBusinessRuleException() {
        Sprint sprint = buildSprint(303L, projectA, "Sprint 1", SprintStatus.PLANNED);
        Task foreignTask = buildTask(900L, secondProjectA, "GAMMA-101", TaskStatus.TODO);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(sprintRepository.findByIdAndProjectId(303L, 100L)).thenReturn(Optional.of(sprint));
        when(userRepository.findById(11L)).thenReturn(Optional.of(pmA));
        when(taskRepository.findDetailById(900L)).thenReturn(Optional.of(foreignTask));

        assertThatThrownBy(() -> sprintService.addTasksToSprint(100L, 303L, new AddSprintTasksRequest(List.of(900L))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong to project ALPHA");
    }

    @Test
    void addTasksToSprint_WhenDuplicateTaskAlreadyInSprint_ShouldThrowDuplicateResourceException() {
        Sprint sprint = buildSprint(303L, projectA, "Sprint 1", SprintStatus.PLANNED);
        Task task = buildTask(901L, projectA, "ALPHA-101", TaskStatus.TODO);
        SprintTask existingMapping = new SprintTask(sprint, task, pmA);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(sprintRepository.findByIdAndProjectId(303L, 100L)).thenReturn(Optional.of(sprint));
        when(userRepository.findById(11L)).thenReturn(Optional.of(pmA));
        when(taskRepository.findDetailById(901L)).thenReturn(Optional.of(task));
        when(sprintTaskRepository.findBySprintIdAndTaskId(303L, 901L)).thenReturn(Optional.of(existingMapping));

        assertThatThrownBy(() -> sprintService.addTasksToSprint(100L, 303L, new AddSprintTasksRequest(List.of(901L))))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already assigned");
    }

    @Test
    void completeSprint_ShouldSnapshotStatusesReturnIncompleteTasksToBacklogAndAudit() {
        Sprint activeSprint = buildSprint(304L, projectA, "Sprint 1", SprintStatus.ACTIVE);
        Task doneTask = buildTask(910L, projectA, "ALPHA-101", TaskStatus.DONE);
        doneTask.setSprint(activeSprint);
        Task inProgressTask = buildTask(911L, projectA, "ALPHA-102", TaskStatus.IN_PROGRESS);
        inProgressTask.setSprint(activeSprint);

        SprintTask stDone = new SprintTask(activeSprint, doneTask, pmA);
        SprintTask stInProgress = new SprintTask(activeSprint, inProgressTask, pmA);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(sprintRepository.findByIdAndProjectId(304L, 100L)).thenReturn(Optional.of(activeSprint));
        when(userRepository.findById(11L)).thenReturn(Optional.of(pmA));
        when(sprintTaskRepository.findBySprintIdAndRemovedAtIsNull(304L)).thenReturn(List.of(stDone, stInProgress));
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(100L)).thenReturn(List.of());

        SprintDetailResponse response = sprintService.completeSprint(100L, 304L);

        assertThat(response.status()).isEqualTo(SprintStatus.COMPLETED);
        assertThat(stDone.getStatusAtSprintEnd()).isEqualTo(TaskStatus.DONE);
        assertThat(stInProgress.getStatusAtSprintEnd()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(inProgressTask.getSprint()).isNull();
        assertThat(doneTask.getSprint()).isEqualTo(activeSprint);
        verify(auditLogWriter).record(
                eq(orgA), eq(pmA), eq(projectA), eq(AuditAction.SPRINT_COMPLETED), eq("SPRINT"), eq(304L), any(), any()
        );
    }

    @Test
    void getSprintReport_ShouldComputeExactDatabaseDerivedMetrics() {
        Sprint activeSprint = buildSprint(305L, projectA, "Sprint Metrics", SprintStatus.ACTIVE);
        Task t1 = buildTask(920L, projectA, "ALPHA-201", TaskStatus.DONE);
        Task t2 = buildTask(921L, projectA, "ALPHA-202", TaskStatus.DONE);
        Task t3 = buildTask(922L, projectA, "ALPHA-203", TaskStatus.IN_PROGRESS);
        Task t4 = buildTask(923L, projectA, "ALPHA-204", TaskStatus.BLOCKED);
        Task t5 = buildTask(924L, projectA, "ALPHA-205", TaskStatus.TODO);
        Task t6 = buildTask(925L, projectA, "ALPHA-206", TaskStatus.IN_REVIEW);

        List<SprintTask> entries = List.of(
                new SprintTask(activeSprint, t1, pmA),
                new SprintTask(activeSprint, t2, pmA),
                new SprintTask(activeSprint, t3, pmA),
                new SprintTask(activeSprint, t4, pmA),
                new SprintTask(activeSprint, t5, pmA),
                new SprintTask(activeSprint, t6, pmA)
        );

        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(sprintRepository.findByIdAndProjectId(305L, 100L)).thenReturn(Optional.of(activeSprint));
        when(sprintTaskRepository.findBySprintIdAndRemovedAtIsNull(305L)).thenReturn(entries);

        SprintReportResponse report = sprintService.getSprintReport(100L, 305L);

        assertThat(report.totalTasks()).isEqualTo(6L);
        assertThat(report.completedTasks()).isEqualTo(2L);
        assertThat(report.inProgressTasks()).isEqualTo(1L);
        assertThat(report.inReviewTasks()).isEqualTo(1L);
        assertThat(report.todoTasks()).isEqualTo(1L);
        assertThat(report.blockedTasks()).isEqualTo(1L);
        assertThat(report.remainingTasks()).isEqualTo(4L);
        assertThat(report.completionPercentage()).isEqualTo(33.33);
        assertThat(report.completedTaskList()).hasSize(2);
        assertThat(report.incompleteTaskList()).hasSize(4);
    }

    @Test
    void getSprintReport_WhenCrossOrgAdmin_ShouldThrowUnauthorized() {
        authenticateAs(adminB);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));

        assertThatThrownBy(() -> sprintService.getSprintReport(100L, 305L))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    private User buildUser(Long id, Organization org, String email, Role role) {
        User user = new User(org, "EMP-" + id, email, "encoded-hash", "User " + id, "Staff", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Project buildProject(Long id, Organization org, String key, User manager) {
        Project project = new Project(
                org,
                key,
                "Project " + key,
                "Description",
                manager,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
        project.setStatus(ProjectStatus.ACTIVE);
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private Sprint buildSprint(Long id, Project project, String name, SprintStatus status) {
        Sprint sprint = new Sprint(
                project,
                name,
                "Deliver milestone",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 14),
                pmA
        );
        sprint.setStatus(status);
        ReflectionTestUtils.setField(sprint, "id", id);
        return sprint;
    }

    private Task buildTask(Long id, Project project, String key, TaskStatus status) {
        Task task = new Task(
                project,
                key,
                "Task " + key,
                "Desc",
                PriorityLevel.MEDIUM,
                devA,
                pmA,
                null,
                null,
                new BigDecimal("4.00"),
                LocalDate.of(2026, 6, 1)
        );
        task.setStatus(status);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private void authenticateAs(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
