package com.taskflow.controller;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.project.AddProjectMemberRequest;
import com.taskflow.dto.project.CreateProjectRequest;
import com.taskflow.dto.project.ProjectDetailResponse;
import com.taskflow.dto.project.ProjectMemberResponse;
import com.taskflow.dto.project.ProjectSummaryResponse;
import com.taskflow.dto.project.UpdateProjectMemberRoleRequest;
import com.taskflow.dto.project.UpdateProjectRequest;
import com.taskflow.dto.project.UpdateProjectStatusRequest;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.service.ProjectService;
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

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "name", "projectKey", "startDate", "updatedAt");

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<ProjectDetailResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectDetailResponse created = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ProjectSummaryResponse>> listProjects(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));

        return ResponseEntity.ok(projectService.listProjects(status, search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@projectGuard.canViewProject(#id, authentication)")
    public ResponseEntity<ProjectDetailResponse> getProjectById(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@projectGuard.canManageProject(#id, authentication)")
    public ResponseEntity<ProjectDetailResponse> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@projectGuard.canManageProject(#id, authentication)")
    public ResponseEntity<ProjectDetailResponse> updateProjectStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectStatusRequest request
    ) {
        return ResponseEntity.ok(projectService.updateProjectStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@projectGuard.canManageProject(#id, authentication)")
    public ResponseEntity<ProjectSummaryResponse> archiveProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.archiveProject(id));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("@projectGuard.canViewProject(#id, authentication)")
    public ResponseEntity<List<ProjectMemberResponse>> listProjectMembers(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.listProjectMembers(id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@projectGuard.canManageProject(#id, authentication)")
    public ResponseEntity<ProjectMemberResponse> addProjectMember(
            @PathVariable Long id,
            @Valid @RequestBody AddProjectMemberRequest request
    ) {
        ProjectMemberResponse created = projectService.addProjectMember(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@projectGuard.canManageProject(#id, authentication)")
    public ResponseEntity<Void> removeProjectMember(
            @PathVariable Long id,
            @PathVariable Long userId
    ) {
        projectService.removeProjectMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/members/{userId}/role")
    @PreAuthorize("@projectGuard.canManageProject(#id, authentication)")
    public ResponseEntity<ProjectMemberResponse> updateProjectMemberRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateProjectMemberRoleRequest request
    ) {
        return ResponseEntity.ok(projectService.updateProjectMemberRole(id, userId, request));
    }
}
