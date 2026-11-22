package com.taskflow.controller;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.task.AssignTaskRequest;
import com.taskflow.dto.task.CreateTaskRequest;
import com.taskflow.dto.task.TaskDetailResponse;
import com.taskflow.dto.task.TaskSummaryResponse;
import com.taskflow.dto.task.UpdateTaskRequest;
import com.taskflow.dto.task.UpdateTaskStatusRequest;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.TaskStatus;
import com.taskflow.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "dueDate", "priority", "status", "boardOrder", "taskKey");

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<TaskDetailResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskDetailResponse created = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TaskSummaryResponse>> listTasks(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) PriorityLevel priority,
            @RequestParam(required = false) Long sprintId,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(taskService.listTasks(
                projectId, assigneeId, status, priority, sprintId, overdueOnly, search, pageable
        ));
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<PagedResponse<TaskSummaryResponse>> listMyTasks(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) PriorityLevel priority,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dueDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(taskService.listMyTasks(
                projectId, status, priority, overdueOnly, search, pageable
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDetailResponse> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDetailResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        return ResponseEntity.ok(taskService.updateTask(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskDetailResponse> updateTaskStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskStatusRequest request
    ) {
        return ResponseEntity.ok(taskService.updateTaskStatus(id, request));
    }

    @PatchMapping("/{id}/assignee")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<TaskDetailResponse> assignTask(
            @PathVariable Long id,
            @Valid @RequestBody AssignTaskRequest request
    ) {
        return ResponseEntity.ok(taskService.assignTask(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));
    }
}
