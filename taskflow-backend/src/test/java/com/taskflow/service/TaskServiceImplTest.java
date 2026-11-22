package com.taskflow.service;

import com.taskflow.dto.task.AssignTaskRequest;
import com.taskflow.dto.task.CreateTaskRequest;
import com.taskflow.dto.task.KanbanBoardResponse;
import com.taskflow.dto.task.TaskDetailResponse;
import com.taskflow.dto.task.UpdateTaskRequest;
import com.taskflow.dto.task.UpdateTaskStatusRequest;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskHistory;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.NotificationType;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.InvalidWorkflowTransitionException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.SprintTaskRepository;
import com.taskflow.repository.TaskAttachmentRepository;
import com.taskflow.repository.TaskCommentRepository;
import com.taskflow.repository.TaskHistoryRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskHistoryRepository taskHistoryRepository;
    @Mock
    private TaskCommentRepository taskCommentRepository;
    @Mock
    private TaskAttachmentRepository taskAttachmentRepository;
    @Mock
    private SprintTaskRepository sprintTaskRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private NotificationDispatcher notificationDispatcher;

    private ProjectSecurityEvaluator projectSecurityEvaluator;
    private TaskServiceImpl taskService;

    private Organization orgA;
    private Organization orgB;
    private User adminA;
    private User pmA;
    private User devOneA;
    private User devTwoA;
    private User outsiderDevA;
    private User qaA;
    private User adminB;
    private Project projectA;
    private Project projectB;

    @BeforeEach
    void setUp() {
        projectSecurityEvaluator = new ProjectSecurityEvaluator(projectRepository, projectMemberRepository);
        taskService = new TaskServiceImpl(
                taskRepository,
                taskHistoryRepository,
                taskCommentRepository,
                taskAttachmentRepository,
                sprintTaskRepository,
                projectRepository,
                projectMemberRepository,
                sprintRepository,
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
        Role qaRole = new Role(RoleName.QA_TESTER, "QA Tester");

        adminA = buildUser(10L, orgA, "admin@alpha.test", adminRole);
        pmA = buildUser(11L, orgA, "pm@alpha.test", pmRole);
        devOneA = buildUser(12L, orgA, "dev1@alpha.test", devRole);
        devTwoA = buildUser(13L, orgA, "dev2@alpha.test", devRole);
        outsiderDevA = buildUser(14L, orgA, "outsider@alpha.test", devRole);
        qaA = buildUser(15L, orgA, "qa@alpha.test", qaRole);
        adminB = buildUser(20L, orgB, "admin@beta.test", adminRole);

        projectA = buildProject(100L, orgA, "ALPHA", pmA);
        projectB = buildProject(200L, orgB, "BETA", adminB);

        authenticateAs(pmA);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTask_WhenDeveloper_ShouldThrowUnauthorized() {
        authenticateAs(devOneA);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));

        CreateTaskRequest request = new CreateTaskRequest(
                100L, "Unauthorized Task", "Desc", PriorityLevel.HIGH,
                devOneA.getId(), null, null, BigDecimal.valueOf(4), LocalDate.of(2026, 6, 1)
        );

        assertThatThrownBy(() -> taskService.createTask(request))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void createTask_WhenAssigneeIsNotProjectMember_ShouldThrowBusinessRuleException() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(projectA));
        when(userRepository.findById(11L)).thenReturn(Optional.of(pmA));
        when(userRepository.findById(14L)).thenReturn(Optional.of(outsiderDevA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 14L)).thenReturn(false);

        CreateTaskRequest request = new CreateTaskRequest(
                100L, "Build API", "Desc", PriorityLevel.HIGH,
                14L, null, null, BigDecimal.valueOf(4), LocalDate.of(2026, 6, 1)
        );

        assertThatThrownBy(() -> taskService.createTask(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not a member of project");
    }

    @Test
    void createTask_WhenDueDateOutsideProjectTimeline_ShouldThrowBusinessRuleException() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(projectA));

        CreateTaskRequest request = new CreateTaskRequest(
                100L, "Late Task", "Desc", PriorityLevel.MEDIUM,
                null, null, null, BigDecimal.valueOf(2), LocalDate.of(2027, 2, 1)
        );

        assertThatThrownBy(() -> taskService.createTask(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("project end date");
    }

    @Test
    void updateTaskStatus_WhenManagerSkipsReviewTodoToDone_ShouldThrowInvalidWorkflowTransition() {
        Task task = buildTask(500L, projectA, "ALPHA-101", TaskStatus.TODO, devOneA, pmA);
        when(taskRepository.findDetailById(500L)).thenReturn(Optional.of(task));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));

        assertThatThrownBy(() -> taskService.updateTaskStatus(
                500L, new UpdateTaskStatusRequest(TaskStatus.DONE, null, "Skipping review")
        )).isInstanceOf(InvalidWorkflowTransitionException.class);
    }

    @Test
    void updateTaskStatus_WhenDeveloperMarksDoneDirectly_ShouldThrowUnauthorized() {
        authenticateAs(devOneA);
        Task task = buildTask(501L, projectA, "ALPHA-102", TaskStatus.IN_REVIEW, devOneA, pmA);
        when(taskRepository.findDetailById(501L)).thenReturn(Optional.of(task));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 12L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.updateTaskStatus(
                501L, new UpdateTaskStatusRequest(TaskStatus.DONE, null, "Self-approving")
        ))
                .isInstanceOf(UnauthorizedResourceAccessException.class)
                .hasMessageContaining("cannot mark tasks DONE");
    }

    @Test
    void updateTaskStatus_WhenQaApprovesInReviewTask_ShouldTransitionToDoneAndAuditAndNotify() {
        authenticateAs(qaA);
        Task task = buildTask(502L, projectA, "ALPHA-103", TaskStatus.IN_REVIEW, devOneA, pmA);

        when(taskRepository.findDetailById(502L)).thenReturn(Optional.of(task));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 15L)).thenReturn(true);
        when(userRepository.findById(15L)).thenReturn(Optional.of(qaA));
        when(taskRepository.findMaxBoardOrder(100L, TaskStatus.DONE)).thenReturn(0);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskHistoryRepository.findByTaskIdOrderByCreatedAtDesc(502L)).thenReturn(List.of());
        when(taskRepository.findByParentTaskIdOrderByCreatedAtAsc(502L)).thenReturn(List.of());

        TaskDetailResponse response = taskService.updateTaskStatus(
                502L, new UpdateTaskStatusRequest(TaskStatus.DONE, null, "All test cases passed")
        );

        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
        verify(taskHistoryRepository).save(any(TaskHistory.class));
        verify(auditLogWriter).record(
                eq(orgA), eq(qaA), eq(projectA), eq(AuditAction.TASK_STATUS_CHANGED), eq("TASK"), eq(502L), any(), any()
        );
        verify(notificationDispatcher).notifyUser(
                eq(devOneA), eq(NotificationType.TASK_APPROVED), any(), any(), eq("TASK"), eq(502L)
        );
    }

    @Test
    void updateTask_WhenDeveloperModifiesAnotherDevelopersTask_ShouldThrowUnauthorized() {
        authenticateAs(devTwoA);
        Task task = buildTask(503L, projectA, "ALPHA-104", TaskStatus.IN_PROGRESS, devOneA, pmA);

        when(taskRepository.findDetailById(503L)).thenReturn(Optional.of(task));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 13L)).thenReturn(true);

        UpdateTaskRequest request = new UpdateTaskRequest(
                null, "Tampered description", null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> taskService.updateTask(503L, request))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void updateTask_WhenDeveloperModifiesPermittedFieldsOnAssignedTask_ShouldSucceedAndRecordHistory() {
        authenticateAs(devOneA);
        Task task = buildTask(504L, projectA, "ALPHA-105", TaskStatus.IN_PROGRESS, devOneA, pmA);

        when(taskRepository.findDetailById(504L)).thenReturn(Optional.of(task));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 12L)).thenReturn(true);
        when(userRepository.findById(12L)).thenReturn(Optional.of(devOneA));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskHistoryRepository.findByTaskIdOrderByCreatedAtDesc(504L)).thenReturn(List.of());
        when(taskRepository.findByParentTaskIdOrderByCreatedAtAsc(504L)).thenReturn(List.of());

        UpdateTaskRequest request = new UpdateTaskRequest(
                null, "Updated implementation notes", null, null, null, null,
                new BigDecimal("6.50"), null, null, null, "Revised estimate"
        );

        TaskDetailResponse response = taskService.updateTask(504L, request);

        assertThat(response.description()).isEqualTo("Updated implementation notes");
        assertThat(response.estimatedHours()).isEqualByComparingTo("6.50");
        verify(taskHistoryRepository).saveAll(any());
        verify(auditLogWriter).record(
                eq(orgA), eq(devOneA), eq(projectA), eq(AuditAction.TASK_UPDATED), eq("TASK"), eq(504L), any(), any()
        );
    }

    @Test
    void assignTask_WhenNonProjectMember_ShouldThrowBusinessRuleException() {
        Task task = buildTask(505L, projectA, "ALPHA-106", TaskStatus.TODO, null, pmA);
        when(taskRepository.findDetailById(505L)).thenReturn(Optional.of(task));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(userRepository.findById(14L)).thenReturn(Optional.of(outsiderDevA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 14L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.assignTask(505L, new AssignTaskRequest(14L, "Assign")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void getTaskById_WhenCrossOrganizationAdmin_ShouldThrowUnauthorized() {
        authenticateAs(adminB);
        Task task = buildTask(506L, projectA, "ALPHA-107", TaskStatus.TODO, devOneA, pmA);
        when(taskRepository.findDetailById(506L)).thenReturn(Optional.of(task));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));

        assertThatThrownBy(() -> taskService.getTaskById(506L))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void getProjectKanbanBoard_ShouldGroupTasksAcrossAllFiveStatuses() {
        Task todoTask = buildTask(601L, projectA, "ALPHA-101", TaskStatus.TODO, devOneA, pmA);
        Task inProgressTask = buildTask(602L, projectA, "ALPHA-102", TaskStatus.IN_PROGRESS, devOneA, pmA);
        Task blockedTask = buildTask(603L, projectA, "ALPHA-103", TaskStatus.BLOCKED, devTwoA, pmA);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectRepository.findWithManagerById(100L)).thenReturn(Optional.of(projectA));
        when(taskRepository.findKanbanColumnTasks(eq(100L), eq(TaskStatus.TODO), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(todoTask)));
        when(taskRepository.findKanbanColumnTasks(eq(100L), eq(TaskStatus.IN_PROGRESS), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inProgressTask)));
        when(taskRepository.findKanbanColumnTasks(eq(100L), eq(TaskStatus.IN_REVIEW), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(taskRepository.findKanbanColumnTasks(eq(100L), eq(TaskStatus.DONE), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(taskRepository.findKanbanColumnTasks(eq(100L), eq(TaskStatus.BLOCKED), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(blockedTask)));

        KanbanBoardResponse board = taskService.getProjectKanbanBoard(100L, null, null, null, null, 0, 25);

        assertThat(board.totalTasks()).isEqualTo(3L);
        assertThat(board.columns().keySet()).containsExactly(
                TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW, TaskStatus.DONE, TaskStatus.BLOCKED
        );
        assertThat(board.columns().get(TaskStatus.TODO).tasks()).hasSize(1);
        assertThat(board.columns().get(TaskStatus.IN_PROGRESS).tasks()).hasSize(1);
        assertThat(board.columns().get(TaskStatus.BLOCKED).tasks()).hasSize(1);
        assertThat(board.columns().get(TaskStatus.DONE).tasks()).isEmpty();
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

    private Task buildTask(Long id, Project project, String key, TaskStatus status, User assignee, User reporter) {
        Task task = new Task(
                project,
                key,
                "Task " + key,
                "Initial description",
                PriorityLevel.MEDIUM,
                assignee,
                reporter,
                null,
                null,
                new BigDecimal("4.00"),
                LocalDate.of(2026, 6, 15)
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
