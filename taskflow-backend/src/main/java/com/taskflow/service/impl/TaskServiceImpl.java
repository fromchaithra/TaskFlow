package com.taskflow.service.impl;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.task.AssignTaskRequest;
import com.taskflow.dto.task.CreateTaskRequest;
import com.taskflow.dto.task.KanbanBoardResponse;
import com.taskflow.dto.task.KanbanColumnResponse;
import com.taskflow.dto.task.TaskDetailResponse;
import com.taskflow.dto.task.TaskHistoryResponse;
import com.taskflow.dto.task.TaskSummaryResponse;
import com.taskflow.dto.task.UpdateTaskRequest;
import com.taskflow.dto.task.UpdateTaskStatusRequest;
import com.taskflow.entity.Project;
import com.taskflow.entity.Sprint;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskHistory;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.NotificationType;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.SprintStatus;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.InvalidWorkflowTransitionException;
import com.taskflow.exception.ResourceNotFoundException;
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
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.NotificationDispatcher;
import com.taskflow.service.TaskService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TaskServiceImpl implements TaskService {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_STATUS_TRANSITIONS = buildStatusTransitions();

    private final TaskRepository taskRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final SprintTaskRepository sprintTaskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;
    private final AuditLogWriter auditLogWriter;
    private final NotificationDispatcher notificationDispatcher;

    public TaskServiceImpl(
            TaskRepository taskRepository,
            TaskHistoryRepository taskHistoryRepository,
            TaskCommentRepository taskCommentRepository,
            TaskAttachmentRepository taskAttachmentRepository,
            SprintTaskRepository sprintTaskRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            SprintRepository sprintRepository,
            UserRepository userRepository,
            ProjectSecurityEvaluator projectSecurityEvaluator,
            AuditLogWriter auditLogWriter,
            NotificationDispatcher notificationDispatcher
    ) {
        this.taskRepository = taskRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.taskCommentRepository = taskCommentRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.sprintTaskRepository = sprintTaskRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.sprintRepository = sprintRepository;
        this.userRepository = userRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
        this.auditLogWriter = auditLogWriter;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Override
    @Transactional
    public TaskDetailResponse createTask(CreateTaskRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(request.projectId(), actorDetails);

        Project project = projectRepository.findByIdForUpdate(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + request.projectId()));

        if (project.getStatus() == ProjectStatus.ARCHIVED || project.getStatus() == ProjectStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot create tasks in a " + project.getStatus() + " project.");
        }

        validateDueDateAgainstProject(request.dueDate(), project);

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        User assignee = resolveProjectAssignee(request.assigneeId(), project);
        Sprint sprint = resolveProjectSprint(request.sprintId(), project);
        Task parentTask = resolveParentTask(request.parentTaskId(), project, null);

        String taskKey = project.nextTaskKey();
        PriorityLevel priority = request.priority() != null ? request.priority() : PriorityLevel.MEDIUM;

        Task task = new Task(
                project,
                taskKey,
                request.title().trim(),
                normalizeOptionalText(request.description()),
                priority,
                assignee,
                actor,
                sprint,
                parentTask,
                request.estimatedHours(),
                request.dueDate()
        );
        Integer maxOrder = taskRepository.findMaxBoardOrder(project.getId(), TaskStatus.TODO);
        task.setBoardOrder((maxOrder != null ? maxOrder : -1) + 1);

        projectRepository.save(project);
        Task saved = taskRepository.save(task);

        taskHistoryRepository.save(new TaskHistory(
                saved,
                actor,
                "CREATED",
                null,
                saved.getStatus().name(),
                "Task " + saved.getTaskKey() + " created"
        ));

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.TASK_CREATED,
                "TASK",
                saved.getId(),
                null,
                buildTaskSnapshot(saved)
        );

        if (assignee != null && !assignee.getId().equals(actor.getId())) {
            notificationDispatcher.notifyUser(
                    assignee,
                    NotificationType.TASK_ASSIGNED,
                    "New task assigned: " + saved.getTaskKey(),
                    actor.getFullName() + " assigned you to '" + saved.getTitle() + "'.",
                    "TASK",
                    saved.getId()
            );
        }

        return toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TaskSummaryResponse> listTasks(
            Long projectId,
            Long assigneeId,
            TaskStatus status,
            PriorityLevel priority,
            Long sprintId,
            boolean overdueOnly,
            String search,
            Pageable pageable
    ) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        if (projectId != null) {
            projectSecurityEvaluator.assertCanViewProject(projectId, viewer);
        }

        Page<Task> taskPage = taskRepository.searchAuthorizedTasks(
                viewer.getOrganizationId(),
                viewer.getId(),
                viewer.isAdmin(),
                projectId,
                assigneeId,
                status,
                priority,
                sprintId,
                overdueOnly,
                LocalDate.now(),
                normalizeSearch(search),
                pageable
        );
        return PagedResponse.from(taskPage.map(TaskSummaryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TaskSummaryResponse> listMyTasks(
            Long projectId,
            TaskStatus status,
            PriorityLevel priority,
            boolean overdueOnly,
            String search,
            Pageable pageable
    ) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        if (projectId != null) {
            projectSecurityEvaluator.assertCanViewProject(projectId, viewer);
        }

        Page<Task> taskPage = taskRepository.searchAuthorizedTasks(
                viewer.getOrganizationId(),
                viewer.getId(),
                viewer.isAdmin(),
                projectId,
                viewer.getId(),
                status,
                priority,
                null,
                overdueOnly,
                LocalDate.now(),
                normalizeSearch(search),
                pageable
        );
        return PagedResponse.from(taskPage.map(TaskSummaryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskById(Long taskId) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        Task task = findTaskOrThrow(taskId);
        projectSecurityEvaluator.assertCanViewProject(task.getProject().getId(), viewer);
        return toDetailResponse(task);
    }

    @Override
    @Transactional
    public TaskDetailResponse updateTask(Long taskId, UpdateTaskRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Task task = findTaskOrThrow(taskId);
        Project project = task.getProject();

        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        if (request.expectedVersion() != null && !request.expectedVersion().equals(task.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Task.class, taskId);
        }

        boolean isManagerOrAdmin = canManageTaskProject(project, actorDetails);
        boolean isAssignee = isTaskAssignee(task, actorDetails);

        if (!isManagerOrAdmin) {
            if (!isAssignee) {
                throw new UnauthorizedResourceAccessException(
                        "You are not authorized to modify this task."
                );
            }
            enforceNonManagerFieldRestrictions(task, request, actorDetails.getPrimaryRole());
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        Map<String, Object> previousSnapshot = buildTaskSnapshot(task);
        List<TaskHistory> historyEntries = new ArrayList<>();
        String remarks = normalizeOptionalText(request.remarks());

        boolean deadlineChanged = false;
        LocalDate previousDueDate = task.getDueDate();
        User previousAssignee = task.getAssignee();
        User nextAssignee = previousAssignee;

        if (isManagerOrAdmin) {
            if (request.title() != null) {
                String nextTitle = request.title().trim();
                if (nextTitle.length() < 3) {
                    throw new BusinessRuleException("Task title must be at least 3 characters.");
                }
                if (!nextTitle.equals(task.getTitle())) {
                    historyEntries.add(new TaskHistory(task, actor, "title", task.getTitle(), nextTitle, remarks));
                    task.setTitle(nextTitle);
                }
            }

            if (request.priority() != null && request.priority() != task.getPriority()) {
                historyEntries.add(new TaskHistory(
                        task, actor, "priority", task.getPriority().name(), request.priority().name(), remarks
                ));
                task.setPriority(request.priority());
            }

            if (request.dueDate() != null && !Objects.equals(request.dueDate(), task.getDueDate())) {
                validateDueDateAgainstProject(request.dueDate(), project);
                historyEntries.add(new TaskHistory(
                        task,
                        actor,
                        "due_date",
                        formatDate(task.getDueDate()),
                        formatDate(request.dueDate()),
                        remarks
                ));
                task.setDueDate(request.dueDate());
                deadlineChanged = true;
            }

            if (request.sprintId() != null) {
                Long currentSprintId = task.getSprint() != null ? task.getSprint().getId() : null;
                if (!request.sprintId().equals(currentSprintId)) {
                    Sprint nextSprint = resolveProjectSprint(request.sprintId(), project);
                    historyEntries.add(new TaskHistory(
                            task,
                            actor,
                            "sprint",
                            task.getSprint() != null ? task.getSprint().getName() : "Backlog",
                            nextSprint.getName(),
                            remarks
                    ));
                    task.setSprint(nextSprint);
                }
            }

            if (request.parentTaskId() != null) {
                Long currentParentId = task.getParentTask() != null ? task.getParentTask().getId() : null;
                if (!request.parentTaskId().equals(currentParentId)) {
                    Task nextParent = resolveParentTask(request.parentTaskId(), project, task.getId());
                    historyEntries.add(new TaskHistory(
                            task,
                            actor,
                            "parent_task",
                            task.getParentTask() != null ? task.getParentTask().getTaskKey() : "None",
                            nextParent.getTaskKey(),
                            remarks
                    ));
                    task.setParentTask(nextParent);
                }
            }

            if (request.assigneeId() != null) {
                Long currentAssigneeId = previousAssignee != null ? previousAssignee.getId() : null;
                if (!request.assigneeId().equals(currentAssigneeId)) {
                    nextAssignee = resolveProjectAssignee(request.assigneeId(), project);
                    historyEntries.add(new TaskHistory(
                            task,
                            actor,
                            "assignee",
                            formatAssignee(previousAssignee),
                            formatAssignee(nextAssignee),
                            remarks
                    ));
                    task.setAssignee(nextAssignee);
                }
            }
        }

        if (request.description() != null) {
            String nextDescription = normalizeOptionalText(request.description());
            if (!Objects.equals(nextDescription, task.getDescription())) {
                historyEntries.add(new TaskHistory(
                        task,
                        actor,
                        "description",
                        truncateForHistory(task.getDescription()),
                        truncateForHistory(nextDescription),
                        remarks
                ));
                task.setDescription(nextDescription);
            }
        }

        if (request.estimatedHours() != null && !Objects.equals(request.estimatedHours(), task.getEstimatedHours())) {
            historyEntries.add(new TaskHistory(
                    task,
                    actor,
                    "estimated_hours",
                    task.getEstimatedHours() != null ? task.getEstimatedHours().toPlainString() : null,
                    request.estimatedHours().toPlainString(),
                    remarks
            ));
            task.setEstimatedHours(request.estimatedHours());
        }

        if (request.boardOrder() != null && !Objects.equals(request.boardOrder(), task.getBoardOrder())) {
            task.setBoardOrder(request.boardOrder());
        }

        Task saved = taskRepository.save(task);
        if (!historyEntries.isEmpty()) {
            taskHistoryRepository.saveAll(historyEntries);
        }

        if (deadlineChanged) {
            Map<String, Object> prevDeadline = new HashMap<>();
            prevDeadline.put("taskKey", saved.getTaskKey());
            prevDeadline.put("dueDate", formatDate(previousDueDate));

            Map<String, Object> nextDeadline = new HashMap<>();
            nextDeadline.put("taskKey", saved.getTaskKey());
            nextDeadline.put("dueDate", formatDate(saved.getDueDate()));

            auditLogWriter.record(
                    project.getOrganization(),
                    actor,
                    project,
                    AuditAction.TASK_DEADLINE_MODIFIED,
                    "TASK",
                    saved.getId(),
                    prevDeadline,
                    nextDeadline
            );
        }

        if (!historyEntries.isEmpty() || request.boardOrder() != null) {
            auditLogWriter.record(
                    project.getOrganization(),
                    actor,
                    project,
                    AuditAction.TASK_UPDATED,
                    "TASK",
                    saved.getId(),
                    previousSnapshot,
                    buildTaskSnapshot(saved)
            );
        }

        if (nextAssignee != null
                && !Objects.equals(nextAssignee.getId(), previousAssignee != null ? previousAssignee.getId() : null)
                && !nextAssignee.getId().equals(actor.getId())) {
            NotificationType type = previousAssignee == null
                    ? NotificationType.TASK_ASSIGNED
                    : NotificationType.TASK_REASSIGNED;
            notificationDispatcher.notifyUser(
                    nextAssignee,
                    type,
                    "Task assigned: " + saved.getTaskKey(),
                    actor.getFullName() + " assigned you to '" + saved.getTitle() + "'.",
                    "TASK",
                    saved.getId()
            );
        }

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public TaskDetailResponse updateTaskStatus(Long taskId, UpdateTaskStatusRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Task task = findTaskOrThrow(taskId);
        Project project = task.getProject();

        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        TaskStatus currentStatus = task.getStatus();
        TaskStatus targetStatus = request.status();

        if (currentStatus == targetStatus) {
            if (request.boardOrder() != null && !request.boardOrder().equals(task.getBoardOrder())) {
                if (!canManageTaskProject(project, actorDetails) && !isTaskAssignee(task, actorDetails)) {
                    throw new UnauthorizedResourceAccessException("You are not allowed to reorder this task.");
                }
                task.setBoardOrder(request.boardOrder());
                task = taskRepository.save(task);
            }
            return toDetailResponse(task);
        }

        enforceStatusTransitionPermissions(task, currentStatus, targetStatus, actorDetails);
        validateStatusTransition(currentStatus, targetStatus);

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        // Capture the assignee at the exact moment of a DONE transition. This is
        // stored in task_history for employee completion attribution and is NULL
        // for all other transitions (including unassigned completions).
        User completedAssignee = targetStatus == TaskStatus.DONE ? task.getAssignee() : null;

        task.setStatus(targetStatus);
        if (request.boardOrder() != null) {
            task.setBoardOrder(request.boardOrder());
        } else {
            Integer maxOrder = taskRepository.findMaxBoardOrder(project.getId(), targetStatus);
            task.setBoardOrder((maxOrder != null ? maxOrder : -1) + 1);
        }

        Task saved = taskRepository.save(task);
        String remarks = normalizeOptionalText(request.remarks());

        taskHistoryRepository.save(new TaskHistory(
                saved,
                actor,
                completedAssignee,
                "status",
                currentStatus.name(),
                targetStatus.name(),
                remarks
        ));

        Map<String, Object> prevMap = new HashMap<>();
        prevMap.put("taskKey", saved.getTaskKey());
        prevMap.put("status", currentStatus.name());

        Map<String, Object> nextMap = new HashMap<>();
        nextMap.put("taskKey", saved.getTaskKey());
        nextMap.put("status", targetStatus.name());
        if (remarks != null) {
            nextMap.put("remarks", remarks);
        }

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.TASK_STATUS_CHANGED,
                "TASK",
                saved.getId(),
                prevMap,
                nextMap
        );

        dispatchStatusTransitionNotifications(saved, currentStatus, targetStatus, actor);

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public TaskDetailResponse assignTask(Long taskId, AssignTaskRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Task task = findTaskOrThrow(taskId);
        Project project = task.getProject();

        projectSecurityEvaluator.assertCanManageProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        User previousAssignee = task.getAssignee();
        User nextAssignee = resolveProjectAssignee(request.assigneeId(), project);

        Long prevId = previousAssignee != null ? previousAssignee.getId() : null;
        Long nextId = nextAssignee != null ? nextAssignee.getId() : null;
        if (Objects.equals(prevId, nextId)) {
            return toDetailResponse(task);
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        task.setAssignee(nextAssignee);
        Task saved = taskRepository.save(task);

        String remarks = normalizeOptionalText(request.remarks());
        taskHistoryRepository.save(new TaskHistory(
                saved,
                actor,
                "assignee",
                formatAssignee(previousAssignee),
                formatAssignee(nextAssignee),
                remarks
        ));

        Map<String, Object> prevMap = new HashMap<>();
        prevMap.put("taskKey", saved.getTaskKey());
        prevMap.put("assigneeId", prevId);

        Map<String, Object> nextMap = new HashMap<>();
        nextMap.put("taskKey", saved.getTaskKey());
        nextMap.put("assigneeId", nextId);

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.TASK_ASSIGNED,
                "TASK",
                saved.getId(),
                prevMap,
                nextMap
        );

        if (nextAssignee != null && !nextAssignee.getId().equals(actor.getId())) {
            NotificationType type = previousAssignee == null
                    ? NotificationType.TASK_ASSIGNED
                    : NotificationType.TASK_REASSIGNED;
            notificationDispatcher.notifyUser(
                    nextAssignee,
                    type,
                    "Task assigned: " + saved.getTaskKey(),
                    actor.getFullName() + " assigned you to '" + saved.getTitle() + "'.",
                    "TASK",
                    saved.getId()
            );
        }

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public void deleteTask(Long taskId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Task task = findTaskOrThrow(taskId);
        Project project = task.getProject();

        projectSecurityEvaluator.assertCanManageProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        if (taskRepository.existsByParentTaskId(taskId)) {
            throw new BusinessRuleException(
                    "Cannot delete task " + task.getTaskKey() + " while it still has subtasks. Delete subtasks first."
            );
        }

        Map<String, Object> snapshot = buildTaskSnapshot(task);
        Long deletedTaskId = task.getId();

        taskHistoryRepository.deleteByTaskId(deletedTaskId);
        taskCommentRepository.deleteByTaskId(deletedTaskId);
        taskAttachmentRepository.deleteByTaskId(deletedTaskId);
        sprintTaskRepository.deleteByTaskId(deletedTaskId);
        taskRepository.delete(task);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.TASK_DELETED,
                "TASK",
                deletedTaskId,
                snapshot,
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public KanbanBoardResponse getProjectKanbanBoard(
            Long projectId,
            Long sprintId,
            Long assigneeId,
            PriorityLevel priority,
            String search,
            int page,
            int limitPerColumn
    ) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(projectId, viewer);

        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limitPerColumn, 1), 100);
        String normalizedSearch = normalizeSearch(search);
        Pageable columnPageRequest = PageRequest.of(safePage, safeLimit);

        Map<TaskStatus, KanbanColumnResponse> columns = new LinkedHashMap<>();
        long totalTasks = 0L;

        for (TaskStatus status : TaskStatus.values()) {
            Page<Task> columnPage = taskRepository.findKanbanColumnTasks(
                    projectId,
                    status,
                    sprintId,
                    assigneeId,
                    priority,
                    normalizedSearch,
                    columnPageRequest
            );
            List<TaskSummaryResponse> cards = columnPage.getContent()
                    .stream()
                    .map(TaskSummaryResponse::from)
                    .toList();
            totalTasks += columnPage.getTotalElements();
            columns.put(status, new KanbanColumnResponse(
                    status,
                    columnPage.getTotalElements(),
                    safePage,
                    safeLimit,
                    columnPage.hasNext(),
                    cards
            ));
        }

        return new KanbanBoardResponse(
                project.getId(),
                project.getProjectKey(),
                project.getName(),
                safePage,
                safeLimit,
                totalTasks,
                columns
        );
    }

    private Task findTaskOrThrow(Long taskId) {
        return taskRepository.findDetailById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    private boolean canManageTaskProject(Project project, CustomUserDetails user) {
        if (!project.getOrganization().getId().equals(user.getOrganizationId())) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        return user.getPrimaryRole() == RoleName.PROJECT_MANAGER
                && project.getManager().getId().equals(user.getId());
    }

    private boolean isTaskAssignee(Task task, CustomUserDetails user) {
        return task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
    }

    private void ensureProjectMutable(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot modify tasks in an archived project.");
        }
    }

    private void enforceNonManagerFieldRestrictions(Task task, UpdateTaskRequest request, RoleName role) {
        boolean attemptedManagerField =
                (request.title() != null && !request.title().trim().equals(task.getTitle()))
                        || (request.priority() != null && request.priority() != task.getPriority())
                        || (request.dueDate() != null && !Objects.equals(request.dueDate(), task.getDueDate()))
                        || (request.sprintId() != null && !Objects.equals(
                                request.sprintId(), task.getSprint() != null ? task.getSprint().getId() : null))
                        || (request.parentTaskId() != null && !Objects.equals(
                                request.parentTaskId(), task.getParentTask() != null ? task.getParentTask().getId() : null))
                        || (request.assigneeId() != null && !Objects.equals(
                                request.assigneeId(), task.getAssignee() != null ? task.getAssignee().getId() : null));

        if (attemptedManagerField) {
            throw new UnauthorizedResourceAccessException(
                    "Only an admin or the assigned project manager can modify title, priority, deadline, sprint, parent task, or assignee."
            );
        }

        if (role == RoleName.QA_TESTER
                && request.estimatedHours() != null
                && !Objects.equals(request.estimatedHours(), task.getEstimatedHours())) {
            throw new UnauthorizedResourceAccessException(
                    "QA testers cannot modify estimated hours on tasks."
            );
        }
    }

    private void enforceStatusTransitionPermissions(
            Task task,
            TaskStatus current,
            TaskStatus target,
            CustomUserDetails actor
    ) {
        if (canManageTaskProject(task.getProject(), actor)) {
            return;
        }

        RoleName role = actor.getPrimaryRole();
        boolean isAssignee = isTaskAssignee(task, actor);

        if (role == RoleName.DEVELOPER) {
            if (!isAssignee) {
                throw new UnauthorizedResourceAccessException(
                        "Developers can only change the status of tasks assigned to them."
                );
            }
            if (target == TaskStatus.DONE) {
                throw new UnauthorizedResourceAccessException(
                        "Developers cannot mark tasks DONE directly. Submit the task to IN_REVIEW for QA or Project Manager approval."
                );
            }
            if (current == TaskStatus.DONE) {
                throw new UnauthorizedResourceAccessException(
                        "Developers cannot reopen completed tasks."
                );
            }
            return;
        }

        if (role == RoleName.QA_TESTER) {
            boolean isReviewAction = current == TaskStatus.IN_REVIEW
                    && (target == TaskStatus.DONE || target == TaskStatus.IN_PROGRESS || target == TaskStatus.BLOCKED);
            boolean isReopenAction = current == TaskStatus.DONE && target == TaskStatus.IN_PROGRESS;
            if (isReviewAction || isReopenAction || isAssignee) {
                return;
            }
            throw new UnauthorizedResourceAccessException(
                    "QA testers can only review IN_REVIEW tasks, reopen DONE tasks, or transition tasks assigned to them."
            );
        }

        // Non-managing Project Manager who happens to be a member on another PM's project
        if (!isAssignee || target == TaskStatus.DONE || current == TaskStatus.DONE) {
            throw new UnauthorizedResourceAccessException(
                    "You are not authorized to perform this status transition on project " + task.getProject().getProjectKey() + "."
            );
        }
    }

    private void validateStatusTransition(TaskStatus current, TaskStatus target) {
        Set<TaskStatus> allowed = ALLOWED_STATUS_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidWorkflowTransitionException(
                    "Invalid task status transition from " + current + " to " + target + "."
            );
        }
    }

    private User resolveProjectAssignee(Long assigneeId, Project project) {
        if (assigneeId == null) {
            return null;
        }
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignee not found: " + assigneeId));
        if (!assignee.getOrganization().getId().equals(project.getOrganization().getId())) {
            throw new UnauthorizedResourceAccessException("Assignee does not belong to your organization.");
        }
        if (!assignee.isActive()) {
            throw new BusinessRuleException("Cannot assign a task to an inactive employee.");
        }
        boolean isMember = project.getManager().getId().equals(assigneeId)
                || projectMemberRepository.existsByProjectIdAndUserId(project.getId(), assigneeId);
        if (!isMember) {
            throw new BusinessRuleException(
                    "User " + assignee.getEmail() + " is not a member of project " + project.getProjectKey() + "."
            );
        }
        return assignee;
    }

    private Sprint resolveProjectSprint(Long sprintId, Project project) {
        if (sprintId == null) {
            return null;
        }
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
        if (!sprint.getProject().getId().equals(project.getId())) {
            throw new BusinessRuleException("Sprint does not belong to project " + project.getProjectKey() + ".");
        }
        if (sprint.getStatus() == SprintStatus.COMPLETED || sprint.getStatus() == SprintStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot assign tasks to a " + sprint.getStatus().name().toLowerCase() + " sprint.");
        }
        return sprint;
    }

    private Task resolveParentTask(Long parentTaskId, Project project, Long currentTaskId) {
        if (parentTaskId == null) {
            return null;
        }
        if (currentTaskId != null && parentTaskId.equals(currentTaskId)) {
            throw new BusinessRuleException("A task cannot be its own parent.");
        }
        Task parent = taskRepository.findById(parentTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent task not found: " + parentTaskId));
        if (!parent.getProject().getId().equals(project.getId())) {
            throw new BusinessRuleException("Parent task must belong to the same project.");
        }
        if (parent.getParentTask() != null) {
            throw new BusinessRuleException("Subtasks cannot have nested child subtasks.");
        }
        return parent;
    }

    private void validateDueDateAgainstProject(LocalDate dueDate, Project project) {
        if (dueDate == null) {
            return;
        }
        if (project.getStartDate() != null && dueDate.isBefore(project.getStartDate())) {
            throw new BusinessRuleException("Task due date cannot be earlier than the project start date.");
        }
        if (project.getEndDate() != null && dueDate.isAfter(project.getEndDate())) {
            throw new BusinessRuleException("Task due date cannot be later than the project end date.");
        }
    }

    private void dispatchStatusTransitionNotifications(
            Task task,
            TaskStatus previousStatus,
            TaskStatus targetStatus,
            User actor
    ) {
        if (targetStatus == TaskStatus.IN_REVIEW) {
            User manager = task.getProject().getManager();
            if (manager != null && !manager.getId().equals(actor.getId())) {
                notificationDispatcher.notifyUser(
                        manager,
                        NotificationType.TASK_REVIEW_REQUESTED,
                        "Review requested: " + task.getTaskKey(),
                        actor.getFullName() + " moved '" + task.getTitle() + "' to IN_REVIEW.",
                        "TASK",
                        task.getId()
                );
            }
        } else if (previousStatus == TaskStatus.IN_REVIEW && targetStatus == TaskStatus.DONE) {
            User assignee = task.getAssignee();
            if (assignee != null && !assignee.getId().equals(actor.getId())) {
                notificationDispatcher.notifyUser(
                        assignee,
                        NotificationType.TASK_APPROVED,
                        "Task approved: " + task.getTaskKey(),
                        actor.getFullName() + " approved '" + task.getTitle() + "' as DONE.",
                        "TASK",
                        task.getId()
                );
            }
        } else if (previousStatus == TaskStatus.IN_REVIEW && targetStatus == TaskStatus.IN_PROGRESS) {
            User assignee = task.getAssignee();
            if (assignee != null && !assignee.getId().equals(actor.getId())) {
                notificationDispatcher.notifyUser(
                        assignee,
                        NotificationType.TASK_REJECTED,
                        "Rework requested: " + task.getTaskKey(),
                        actor.getFullName() + " returned '" + task.getTitle() + "' to IN_PROGRESS.",
                        "TASK",
                        task.getId()
                );
            }
        }
    }

    private TaskDetailResponse toDetailResponse(Task task) {
        Long taskId = task.getId();
        long commentCount = taskCommentRepository.countByTaskId(taskId);
        long attachmentCount = taskAttachmentRepository.countByTaskId(taskId);
        List<TaskSummaryResponse> subtasks = taskRepository.findByParentTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(TaskSummaryResponse::from)
                .toList();
        List<TaskHistoryResponse> history = taskHistoryRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(TaskHistoryResponse::from)
                .toList();
        return TaskDetailResponse.from(task, commentCount, attachmentCount, subtasks, history);
    }

    private Map<String, Object> buildTaskSnapshot(Task task) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("taskKey", task.getTaskKey());
        snapshot.put("projectId", task.getProject().getId());
        snapshot.put("title", task.getTitle());
        snapshot.put("priority", task.getPriority().name());
        snapshot.put("status", task.getStatus().name());
        snapshot.put("assigneeId", task.getAssignee() != null ? task.getAssignee().getId() : null);
        snapshot.put("sprintId", task.getSprint() != null ? task.getSprint().getId() : null);
        snapshot.put("dueDate", formatDate(task.getDueDate()));
        snapshot.put("estimatedHours", task.getEstimatedHours() != null ? task.getEstimatedHours().toPlainString() : null);
        return snapshot;
    }

    private String formatAssignee(User user) {
        return user != null ? user.getFullName() + " (" + user.getEmail() + ")" : "Unassigned";
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    private String truncateForHistory(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 250 ? text : text.substring(0, 247) + "...";
    }

    private String normalizeSearch(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }

    private String normalizeOptionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Map<TaskStatus, Set<TaskStatus>> buildStatusTransitions() {
        Map<TaskStatus, Set<TaskStatus>> map = new EnumMap<>(TaskStatus.class);
        map.put(TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        map.put(TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED, TaskStatus.TODO));
        map.put(TaskStatus.IN_REVIEW, EnumSet.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        map.put(TaskStatus.BLOCKED, EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        map.put(TaskStatus.DONE, EnumSet.of(TaskStatus.IN_PROGRESS));
        return map;
    }
}
