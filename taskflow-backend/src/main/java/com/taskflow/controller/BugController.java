package com.taskflow.controller;

import com.taskflow.dto.bug.AddBugCommentRequest;
import com.taskflow.dto.bug.AssignBugRequest;
import com.taskflow.dto.bug.BugCommentResponse;
import com.taskflow.dto.bug.BugDetailResponse;
import com.taskflow.dto.bug.BugSummaryResponse;
import com.taskflow.dto.bug.CreateBugRequest;
import com.taskflow.dto.bug.ReopenBugRequest;
import com.taskflow.dto.bug.UpdateBugCommentRequest;
import com.taskflow.dto.bug.UpdateBugRequest;
import com.taskflow.dto.bug.UpdateBugSeverityRequest;
import com.taskflow.dto.bug.UpdateBugStatusRequest;
import com.taskflow.dto.bug.VerifyBugRequest;
import com.taskflow.dto.common.PagedResponse;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.service.BugService;
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
@RequestMapping("/api/v1/bugs")
public class BugController {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "severity", "priority", "status", "bugKey");

    private final BugService bugService;

    public BugController(BugService bugService) {
        this.bugService = bugService;
    }

    @PostMapping
    public ResponseEntity<BugDetailResponse> createBug(@Valid @RequestBody CreateBugRequest request) {
        BugDetailResponse created = bugService.createBug(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<BugSummaryResponse>> listBugs(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) BugSeverity severity,
            @RequestParam(required = false) BugStatus status,
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

        return ResponseEntity.ok(bugService.listBugs(projectId, assigneeId, severity, status, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BugDetailResponse> getBugById(@PathVariable Long id) {
        return ResponseEntity.ok(bugService.getBugById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BugDetailResponse> updateBug(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBugRequest request
    ) {
        return ResponseEntity.ok(bugService.updateBug(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BugDetailResponse> updateBugStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBugStatusRequest request
    ) {
        return ResponseEntity.ok(bugService.updateBugStatus(id, request));
    }

    @PatchMapping("/{id}/assignee")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<BugDetailResponse> assignBug(
            @PathVariable Long id,
            @Valid @RequestBody AssignBugRequest request
    ) {
        return ResponseEntity.ok(bugService.assignBug(id, request));
    }

    @PatchMapping("/{id}/severity")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<BugDetailResponse> updateBugSeverity(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBugSeverityRequest request
    ) {
        return ResponseEntity.ok(bugService.updateBugSeverity(id, request));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<BugDetailResponse> verifyBug(
            @PathVariable Long id,
            @Valid @RequestBody VerifyBugRequest request
    ) {
        return ResponseEntity.ok(bugService.verifyBug(id, request));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<BugDetailResponse> reopenBug(
            @PathVariable Long id,
            @Valid @RequestBody ReopenBugRequest request
    ) {
        return ResponseEntity.ok(bugService.reopenBug(id, request));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<java.util.List<BugCommentResponse>> listComments(@PathVariable Long id) {
        return ResponseEntity.ok(bugService.listComments(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<BugCommentResponse> addComment(
            @PathVariable Long id,
            @Valid @RequestBody com.taskflow.dto.bug.AddBugCommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bugService.addComment(id, request.content()));
    }

    @PatchMapping("/{id}/comments/{commentId}")
    public ResponseEntity<BugCommentResponse> editComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateBugCommentRequest request
    ) {
        return ResponseEntity.ok(bugService.editComment(id, commentId, request.content()));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id, @PathVariable Long commentId) {
        bugService.deleteComment(id, commentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<Void> deleteBug(@PathVariable Long id) {
        bugService.deleteBug(id);
        return ResponseEntity.noContent().build();
    }
}
