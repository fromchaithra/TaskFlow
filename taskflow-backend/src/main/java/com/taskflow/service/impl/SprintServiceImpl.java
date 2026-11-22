package com.taskflow.service.impl;

import com.taskflow.dto.sprint.AddSprintTasksRequest;
import com.taskflow.dto.sprint.CreateSprintRequest;
import com.taskflow.dto.sprint.SprintDetailResponse;
import com.taskflow.dto.sprint.SprintReportResponse;
import com.taskflow.dto.sprint.SprintSummaryResponse;
import com.taskflow.dto.sprint.UpdateSprintRequest;
import com.taskflow.dto.task.TaskSummaryResponse;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Sprint;
import com.taskflow.entity.SprintTask;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskHistory;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.NotificationType;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.SprintStatus;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.InvalidWorkflowTransitionException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.SprintTaskRepository;
import com.taskflow.repository.TaskHistoryRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.NotificationDispatcher;
import com.taskflow.service.SprintService;
import com.taskflow.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SprintServiceImpl implements SprintService {

    private final SprintRepository sprintRepository;
    private final SprintTaskRepository sprintTaskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final UserRepository userRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;
    private final AuditLogWriter auditLogWriter;
    private final NotificationDispatcher notificationDispatcher;

    public SprintServiceImpl(
            SprintRepository sprintRepository,
            SprintTaskRepository sprintTaskRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            TaskRepository taskRepository,
            TaskHistoryRepository taskHistoryRepository,
            UserRepository userRepository,
            ProjectSecurityEvaluator projectSecurityEvaluator,
            AuditLogWriter auditLogWriter,
            NotificationDispatcher notificationDispatcher
    ) {
        this.sprintRepository = sprintRepository;
        this.sprintTaskRepository = sprintTaskRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.userRepository = userRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
        this.auditLogWriter = auditLogWriter;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Override
    @Transactional
    public SprintDetailResponse createSprint(Long projectId, CreateSprintRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        if (project.getStatus() == ProjectStatus.ARCHIVED || project.getStatus() == ProjectStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot create sprints in a " + project.getStatus() + " project.");
        }

        String normalizedName = request.name().trim();
        if (sprintRepository.existsByProjectIdAndNameIgnoreCase(projectId, normalizedName)) {
            throw new DuplicateResourceException(
                    "A sprint named '" + normalizedName + "' already exists in project " + project.getProjectKey() + "."
            );
        }

        validateSprintDates(request.startDate(), request.endDate(), project, null);

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        Sprint sprint = new Sprint(
                project,
                normalizedName,
                normalizeOptionalText(request.goal()),
                request.startDate(),
                request.endDate(),
                actor
        );
        Sprint saved = sprintRepository.save(sprint);

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.SPRINT_CREATED,
                "SPRINT",
                saved.getId(),
                null,
                buildSprintSnapshot(saved, 0L)
        );

        return SprintDetailResponse.from(saved, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintSummaryResponse> listProjectSprints(Long projectId, SprintStatus status) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(projectId, viewer);

        List<Sprint> sprints = status != null
                ? sprintRepository.findByProjectIdAndStatusOrderByStartDateDesc(projectId, status)
                : sprintRepository.findByProjectIdOrderByStartDateDesc(projectId);

        Map<Long, Long> taskCounts = loadSprintTaskCounts(sprints);
        return sprints.stream()
                .map(sprint -> SprintSummaryResponse.from(sprint, taskCounts.getOrDefault(sprint.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SprintDetailResponse getSprintById(Long projectId, Long sprintId) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(projectId, viewer);

        Sprint sprint = findScopedSprintOrThrow(projectId, sprintId);
        return toDetailResponse(sprint);
    }

    @Override
    @Transactional
    public SprintDetailResponse updateSprint(Long projectId, Long sprintId, UpdateSprintRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Sprint sprint = findScopedSprintOrThrow(projectId, sprintId);
        Project project = sprint.getProject();

        ensureProjectActiveForMutation(project);
        ensureSprintEditable(sprint);

        String normalizedName = request.name().trim();
        if (sprintRepository.existsByProjectIdAndNameIgnoreCaseAndIdNot(projectId, normalizedName, sprintId)) {
            throw new DuplicateResourceException(
                    "A sprint named '" + normalizedName + "' already exists in project " + project.getProjectKey() + "."
            );
        }

        validateSprintDates(request.startDate(), request.endDate(), project, sprintId);

        long taskCount = sprintTaskRepository.countBySprintIdAndRemovedAtIsNull(sprintId);
        Map<String, Object> previous = buildSprintSnapshot(sprint, taskCount);

        sprint.setName(normalizedName);
        sprint.setGoal(normalizeOptionalText(request.goal()));
        sprint.setStartDate(request.startDate());
        sprint.setEndDate(request.endDate());
        Sprint saved = sprintRepository.save(sprint);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.SPRINT_UPDATED,
                "SPRINT",
                saved.getId(),
                previous,
                buildSprintSnapshot(saved, taskCount)
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public SprintDetailResponse startSprint(Long projectId, Long sprintId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Sprint sprint = findScopedSprintOrThrow(projectId, sprintId);
        Project project = sprint.getProject();

        if (project.getStatus() == ProjectStatus.ARCHIVED || project.getStatus() == ProjectStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot start a sprint in a " + project.getStatus() + " project.");
        }

        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new InvalidWorkflowTransitionException(
                    "Only PLANNED sprints can be started. Current sprint status: " + sprint.getStatus() + "."
            );
        }

        if (sprintRepository.existsByProjectIdAndStatusAndIdNot(projectId, SprintStatus.ACTIVE, sprintId)) {
            throw new BusinessRuleException(
                    "Project " + project.getProjectKey() + " already has an active sprint. Complete or cancel it first."
            );
        }

        if (sprintRepository.existsOverlappingActiveSprint(projectId, sprint.getStartDate(), sprint.getEndDate(), sprintId)) {
            throw new BusinessRuleException(
                    "Sprint dates overlap with an existing active sprint in project " + project.getProjectKey() + "."
            );
        }

        sprint.setStatus(SprintStatus.ACTIVE);
        sprint.setStartedAt(Instant.now());
        Sprint saved = sprintRepository.save(sprint);

        long taskCount = sprintTaskRepository.countBySprintIdAndRemovedAtIsNull(sprintId);
        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.SPRINT_STARTED,
                "SPRINT",
                saved.getId(),
                Map.of("status", SprintStatus.PLANNED.name(), "name", saved.getName()),
                buildSprintSnapshot(saved, taskCount)
        );

        notifyProjectMembers(
                project,
                actor,
                NotificationType.SPRINT_STARTED,
                "Sprint started: " + saved.getName(),
                actor.getFullName() + " started sprint '" + saved.getName() + "' in " + project.getProjectKey() + ".",
                saved.getId()
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public SprintDetailResponse completeSprint(Long projectId, Long sprintId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Sprint sprint = findScopedSprintOrThrow(projectId, sprintId);
        Project project = sprint.getProject();

        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new InvalidWorkflowTransitionException(
                    "Only an ACTIVE sprint can be completed. Current sprint status: " + sprint.getStatus() + "."
            );
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        List<SprintTask> activeEntries = sprintTaskRepository.findBySprintIdAndRemovedAtIsNull(sprintId);
        List<Task> tasksToSave = new ArrayList<>();
        List<TaskHistory> historyEntries = new ArrayList<>();
        long completedCount = 0L;
        long carriedBackToBacklog = 0L;

        for (SprintTask entry : activeEntries) {
            Task task = entry.getTask();
            entry.setStatusAtSprintEnd(task.getStatus());
            if (task.getStatus() == TaskStatus.DONE) {
                completedCount++;
            } else {
                carriedBackToBacklog++;
                if (task.getSprint() != null && task.getSprint().getId().equals(sprintId)) {
                    task.setSprint(null);
                    tasksToSave.add(task);
                    historyEntries.add(new TaskHistory(
                            task,
                            actor,
                            "sprint",
                            sprint.getName(),
                            "Backlog",
                            "Returned to backlog upon completion of sprint '" + sprint.getName() + "'"
                    ));
                }
            }
        }

        sprintTaskRepository.saveAll(activeEntries);
        if (!tasksToSave.isEmpty()) {
            taskRepository.saveAll(tasksToSave);
        }
        if (!historyEntries.isEmpty()) {
            taskHistoryRepository.saveAll(historyEntries);
        }

        sprint.setStatus(SprintStatus.COMPLETED);
        sprint.setCompletedAt(Instant.now());
        Sprint saved = sprintRepository.save(sprint);

        Map<String, Object> completionSummary = buildSprintSnapshot(saved, activeEntries.size());
        completionSummary.put("completedTasks", completedCount);
        completionSummary.put("returnedToBacklog", carriedBackToBacklog);

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.SPRINT_COMPLETED,
                "SPRINT",
                saved.getId(),
                Map.of("status", SprintStatus.ACTIVE.name(), "name", saved.getName()),
                completionSummary
        );

        notifyProjectMembers(
                project,
                actor,
                NotificationType.SPRINT_COMPLETED,
                "Sprint completed: " + saved.getName(),
                actor.getFullName() + " completed sprint '" + saved.getName() + "' (" + completedCount + "/" + activeEntries.size() + " tasks done).",
                saved.getId()
        );

        return toDetailResponse(saved, activeEntries);
    }

    @Override
    @Transactional
    public SprintDetailResponse cancelSprint(Long projectId, Long sprintId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Sprint sprint = findScopedSprintOrThrow(projectId, sprintId);
        Project project = sprint.getProject();

        SprintStatus previousStatus = sprint.getStatus();
        if (previousStatus != SprintStatus.PLANNED && previousStatus != SprintStatus.ACTIVE) {
            throw new InvalidWorkflowTransitionException(
                    "Only PLANNED or ACTIVE sprints can be cancelled. Current sprint status: " + previousStatus + "."
            );
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        List<SprintTask> activeEntries = sprintTaskRepository.findBySprintIdAndRemovedAtIsNull(sprintId);
        List<Task> tasksToSave = new ArrayList<>();
        List<TaskHistory> historyEntries = new ArrayList<>();

        for (SprintTask entry : activeEntries) {
            Task task = entry.getTask();
            entry.setStatusAtSprintEnd(task.getStatus());
            if (task.getSprint() != null && task.getSprint().getId().equals(sprintId)) {
                task.setSprint(null);
                tasksToSave.add(task);
                historyEntries.add(new TaskHistory(
                        task,
                        actor,
                        "sprint",
                        sprint.getName(),
                        "Backlog",
                        "Returned to backlog upon cancellation of sprint '" + sprint.getName() + "'"
                ));
            }
        }

        sprintTaskRepository.saveAll(activeEntries);
        if (!tasksToSave.isEmpty()) {
            taskRepository.saveAll(tasksToSave);
        }
        if (!historyEntries.isEmpty()) {
            taskHistoryRepository.saveAll(historyEntries);
        }

        sprint.setStatus(SprintStatus.CANCELLED);
        sprint.setCompletedAt(Instant.now());
        Sprint saved = sprintRepository.save(sprint);

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.SPRINT_CANCELLED,
                "SPRINT",
                saved.getId(),
                Map.of("status", previousStatus.name(), "name", saved.getName()),
                buildSprintSnapshot(saved, activeEntries.size())
        );

        return toDetailResponse(saved, activeEntries);
    }

    @Override
    @Transactional
    public SprintDetailResponse addTasksToSprint(Long projectId, Long sprintId, AddSprintTasksRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Sprint sprint = findScopedSprintOrThrow(projectId, sprintId);
        Project project = sprint.getProject();

        ensureProjectActiveForMutation(project);
        ensureSprintEditable(sprint);

        Set<Long> uniqueTaskIds = new LinkedHashSet<>();
        for (Long taskId : request.taskIds()) {
            if (taskId == null) {
                throw new BusinessRuleException("Task ID cannot be null.");
            }
            if (!uniqueTaskIds.add(taskId)) {
                throw new DuplicateResourceException("Duplicate task ID in request: " + taskId);
            }
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        List<Long> addedTaskIds = new ArrayList<>();
        for (Long taskId : uniqueTaskIds) {
            Task task = taskRepository.findDetailById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

            if (!task.getProject().getId().equals(projectId)) {
                throw new BusinessRuleException(
                        "Task " + task.getTaskKey() + " does not belong to project " + project.getProjectKey() + "."
                );
            }

            Sprint currentSprint = task.getSprint();
            if (currentSprint != null
                    && !currentSprint.getId().equals(sprintId)
                    && (currentSprint.getStatus() == SprintStatus.PLANNED || currentSprint.getStatus() == SprintStatus.ACTIVE)) {
                throw new BusinessRuleException(
                        "Task " + task.getTaskKey() + " is already assigned to sprint '" + currentSprint.getName()
                                + "'. Remove it from that sprint first."
                );
            }

            SprintTask existingMapping = sprintTaskRepository.findBySprintIdAndTaskId(sprintId, taskId).orElse(null);
            if (existingMapping != null) {
                if (existingMapping.getRemovedAt() == null) {
                    throw new DuplicateResourceException(
                            "Task " + task.getTaskKey() + " is already assigned to sprint '" + sprint.getName() + "'."
                    );
                }
                existingMapping.setRemovedAt(null);
                existingMapping.setStatusAtSprintEnd(null);
                sprintTaskRepository.save(existingMapping);
            } else {
                sprintTaskRepository.save(new SprintTask(sprint, task, actor));
            }

            String previousSprintName = currentSprint != null ? currentSprint.getName() : "Backlog";
            task.setSprint(sprint);
            taskRepository.save(task);

            taskHistoryRepository.save(new TaskHistory(
                    task,
                    actor,
                    "sprint",
                    previousSprintName,
                    sprint.getName(),
                    "Added to sprint '" + sprint.getName() + "'"
            ));
            addedTaskIds.add(task.getId());
        }

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.SPRINT_TASK_ADDED,
                "SPRINT",
                sprint.getId(),
                null,
                Map.of("sprintName", sprint.getName(), "addedTaskIds", addedTaskIds)
        );

        return toDetailResponse(sprint);
    }

    @Override
    @Transactional
    public void removeTaskFromSprint(Long projectId, Long sprintId, Long taskId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Sprint sprint = findScopedSprintOrThrow(projectId, sprintId);
        Project project = sprint.getProject();

        ensureProjectActiveForMutation(project);
        ensureSprintEditable(sprint);

        SprintTask mapping = sprintTaskRepository.findBySprintIdAndTaskId(sprintId, taskId)
                .filter(st -> st.getRemovedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task " + taskId + " is not currently assigned to sprint " + sprintId + "."
                ));

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        mapping.setRemovedAt(Instant.now());
        sprintTaskRepository.save(mapping);

        Task task = mapping.getTask();
        if (task.getSprint() != null && task.getSprint().getId().equals(sprintId)) {
            task.setSprint(null);
            taskRepository.save(task);
            taskHistoryRepository.save(new TaskHistory(
                    task,
                    actor,
                    "sprint",
                    sprint.getName(),
                    "Backlog",
                    "Removed from sprint '" + sprint.getName() + "'"
            ));
        }

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.SPRINT_TASK_REMOVED,
                "SPRINT",
                sprint.getId(),
                Map.of("sprintName", sprint.getName(), "taskId", taskId, "taskKey", task.getTaskKey()),
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SprintReportResponse getSprintReport(Long projectId, Long sprintId) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(projectId, viewer);

        Sprint sprint = findScopedSprintOrThrow(projectId, sprintId);
        List<SprintTask> sprintTasks = sprintTaskRepository.findBySprintIdAndRemovedAtIsNull(sprintId);

        boolean useSnapshot = sprint.getStatus() == SprintStatus.COMPLETED
                || sprint.getStatus() == SprintStatus.CANCELLED;

        long todoCount = 0L;
        long inProgressCount = 0L;
        long inReviewCount = 0L;
        long completedCount = 0L;
        long blockedCount = 0L;

        List<TaskSummaryResponse> completedTaskList = new ArrayList<>();
        List<TaskSummaryResponse> incompleteTaskList = new ArrayList<>();

        for (SprintTask entry : sprintTasks) {
            Task task = entry.getTask();
            TaskStatus effectiveStatus = (useSnapshot && entry.getStatusAtSprintEnd() != null)
                    ? entry.getStatusAtSprintEnd()
                    : task.getStatus();

            TaskSummaryResponse summary = TaskSummaryResponse.from(task);

            switch (effectiveStatus) {
                case TODO -> {
                    todoCount++;
                    incompleteTaskList.add(summary);
                }
                case IN_PROGRESS -> {
                    inProgressCount++;
                    incompleteTaskList.add(summary);
                }
                case IN_REVIEW -> {
                    inReviewCount++;
                    incompleteTaskList.add(summary);
                }
                case BLOCKED -> {
                    blockedCount++;
                    incompleteTaskList.add(summary);
                }
                case DONE -> {
                    completedCount++;
                    completedTaskList.add(summary);
                }
            }
        }

        long totalTasks = sprintTasks.size();
        long remainingTasks = totalTasks - completedCount;
        double completionPercentage = totalTasks == 0L
                ? 0.0
                : Math.round((completedCount * 10000.0) / totalTasks) / 100.0;

        return new SprintReportResponse(
                sprint.getId(),
                sprint.getProject().getId(),
                sprint.getProject().getProjectKey(),
                sprint.getName(),
                sprint.getStatus(),
                sprint.getStartDate(),
                sprint.getEndDate(),
                sprint.getStartedAt(),
                sprint.getCompletedAt(),
                totalTasks,
                completedCount,
                inProgressCount,
                inReviewCount,
                todoCount,
                blockedCount,
                remainingTasks,
                completionPercentage,
                completedTaskList,
                incompleteTaskList
        );
    }

    private Sprint findScopedSprintOrThrow(Long projectId, Long sprintId) {
        return sprintRepository.findByIdAndProjectId(sprintId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sprint " + sprintId + " not found in project " + projectId + "."
                ));
    }

    private void ensureProjectActiveForMutation(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot modify sprints in an archived project.");
        }
    }

    private void ensureSprintEditable(Sprint sprint) {
        if (sprint.getStatus() == SprintStatus.COMPLETED || sprint.getStatus() == SprintStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Cannot modify a " + sprint.getStatus().name().toLowerCase() + " sprint."
            );
        }
    }

    private void validateSprintDates(LocalDate startDate, LocalDate endDate, Project project, Long excludeSprintId) {
        if (!endDate.isAfter(startDate)) {
            throw new BusinessRuleException("Sprint end date must be after start date.");
        }
        if (project.getStartDate() != null && startDate.isBefore(project.getStartDate())) {
            throw new BusinessRuleException("Sprint start date cannot be earlier than the project start date.");
        }
        if (project.getEndDate() != null && endDate.isAfter(project.getEndDate())) {
            throw new BusinessRuleException("Sprint end date cannot be later than the project end date.");
        }
        if (sprintRepository.existsOverlappingActiveSprint(project.getId(), startDate, endDate, excludeSprintId)) {
            throw new BusinessRuleException(
                    "Sprint dates overlap with an active sprint in project " + project.getProjectKey() + "."
            );
        }
    }

    private Map<Long, Long> loadSprintTaskCounts(List<Sprint> sprints) {
        if (sprints.isEmpty()) {
            return Map.of();
        }
        List<Long> sprintIds = sprints.stream().map(Sprint::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : sprintTaskRepository.countActiveTasksBySprintIds(sprintIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private SprintDetailResponse toDetailResponse(Sprint sprint) {
        List<SprintTask> entries = sprintTaskRepository.findBySprintIdAndRemovedAtIsNull(sprint.getId());
        return toDetailResponse(sprint, entries);
    }

    private SprintDetailResponse toDetailResponse(Sprint sprint, List<SprintTask> entries) {
        List<TaskSummaryResponse> tasks = entries.stream()
                .map(entry -> TaskSummaryResponse.from(entry.getTask()))
                .toList();
        return SprintDetailResponse.from(sprint, tasks);
    }

    private void notifyProjectMembers(
            Project project,
            User actor,
            NotificationType type,
            String title,
            String message,
            Long sprintId
    ) {
        List<ProjectMember> members = projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(project.getId());
        for (ProjectMember pm : members) {
            User recipient = pm.getUser();
            if (recipient != null && !recipient.getId().equals(actor.getId())) {
                notificationDispatcher.notifyUser(recipient, type, title, message, "SPRINT", sprintId);
            }
        }
    }

    private Map<String, Object> buildSprintSnapshot(Sprint sprint, long taskCount) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("name", sprint.getName());
        snapshot.put("status", sprint.getStatus().name());
        snapshot.put("startDate", sprint.getStartDate().toString());
        snapshot.put("endDate", sprint.getEndDate().toString());
        snapshot.put("taskCount", taskCount);
        return snapshot;
    }

    private String normalizeOptionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
