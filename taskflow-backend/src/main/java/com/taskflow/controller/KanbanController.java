package com.taskflow.controller;

import com.taskflow.dto.task.KanbanBoardResponse;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/kanban")
public class KanbanController {

    private final TaskService taskService;

    public KanbanController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("@projectGuard.canViewProject(#projectId, authentication)")
    public ResponseEntity<KanbanBoardResponse> getProjectKanbanBoard(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) PriorityLevel priority,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int limitPerColumn
    ) {
        return ResponseEntity.ok(taskService.getProjectKanbanBoard(
                projectId, sprintId, assigneeId, priority, search, page, limitPerColumn
        ));
    }
}
