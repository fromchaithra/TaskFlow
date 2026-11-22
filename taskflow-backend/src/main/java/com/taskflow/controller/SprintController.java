package com.taskflow.controller;

import com.taskflow.dto.sprint.AddSprintTasksRequest;
import com.taskflow.dto.sprint.CreateSprintRequest;
import com.taskflow.dto.sprint.SprintDetailResponse;
import com.taskflow.dto.sprint.SprintReportResponse;
import com.taskflow.dto.sprint.SprintSummaryResponse;
import com.taskflow.dto.sprint.UpdateSprintRequest;
import com.taskflow.enums.SprintStatus;
import com.taskflow.service.SprintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/sprints")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @PostMapping
    @PreAuthorize("@projectGuard.canManageProject(#projectId, authentication)")
    public ResponseEntity<SprintDetailResponse> createSprint(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateSprintRequest request
    ) {
        SprintDetailResponse created = sprintService.createSprint(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("@projectGuard.canViewProject(#projectId, authentication)")
    public ResponseEntity<List<SprintSummaryResponse>> listProjectSprints(
            @PathVariable Long projectId,
            @RequestParam(required = false) SprintStatus status
    ) {
        return ResponseEntity.ok(sprintService.listProjectSprints(projectId, status));
    }

    @GetMapping("/{sprintId}")
    @PreAuthorize("@projectGuard.canViewProject(#projectId, authentication)")
    public ResponseEntity<SprintDetailResponse> getSprintById(
            @PathVariable Long projectId,
            @PathVariable Long sprintId
    ) {
        return ResponseEntity.ok(sprintService.getSprintById(projectId, sprintId));
    }

    @PutMapping("/{sprintId}")
    @PreAuthorize("@projectGuard.canManageProject(#projectId, authentication)")
    public ResponseEntity<SprintDetailResponse> updateSprint(
            @PathVariable Long projectId,
            @PathVariable Long sprintId,
            @Valid @RequestBody UpdateSprintRequest request
    ) {
        return ResponseEntity.ok(sprintService.updateSprint(projectId, sprintId, request));
    }

    @PostMapping("/{sprintId}/start")
    @PreAuthorize("@projectGuard.canManageProject(#projectId, authentication)")
    public ResponseEntity<SprintDetailResponse> startSprint(
            @PathVariable Long projectId,
            @PathVariable Long sprintId
    ) {
        return ResponseEntity.ok(sprintService.startSprint(projectId, sprintId));
    }

    @PostMapping("/{sprintId}/complete")
    @PreAuthorize("@projectGuard.canManageProject(#projectId, authentication)")
    public ResponseEntity<SprintDetailResponse> completeSprint(
            @PathVariable Long projectId,
            @PathVariable Long sprintId
    ) {
        return ResponseEntity.ok(sprintService.completeSprint(projectId, sprintId));
    }

    @PostMapping("/{sprintId}/cancel")
    @PreAuthorize("@projectGuard.canManageProject(#projectId, authentication)")
    public ResponseEntity<SprintDetailResponse> cancelSprint(
            @PathVariable Long projectId,
            @PathVariable Long sprintId
    ) {
        return ResponseEntity.ok(sprintService.cancelSprint(projectId, sprintId));
    }

    @PostMapping("/{sprintId}/tasks")
    @PreAuthorize("@projectGuard.canManageProject(#projectId, authentication)")
    public ResponseEntity<SprintDetailResponse> addTasksToSprint(
            @PathVariable Long projectId,
            @PathVariable Long sprintId,
            @Valid @RequestBody AddSprintTasksRequest request
    ) {
        return ResponseEntity.ok(sprintService.addTasksToSprint(projectId, sprintId, request));
    }

    @DeleteMapping("/{sprintId}/tasks/{taskId}")
    @PreAuthorize("@projectGuard.canManageProject(#projectId, authentication)")
    public ResponseEntity<Void> removeTaskFromSprint(
            @PathVariable Long projectId,
            @PathVariable Long sprintId,
            @PathVariable Long taskId
    ) {
        sprintService.removeTaskFromSprint(projectId, sprintId, taskId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sprintId}/report")
    @PreAuthorize("@projectGuard.canViewProject(#projectId, authentication)")
    public ResponseEntity<SprintReportResponse> getSprintReport(
            @PathVariable Long projectId,
            @PathVariable Long sprintId
    ) {
        return ResponseEntity.ok(sprintService.getSprintReport(projectId, sprintId));
    }
}
