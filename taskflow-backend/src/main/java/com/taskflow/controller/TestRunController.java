package com.taskflow.controller;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.test.TestExecutionResponse;
import com.taskflow.dto.test.UpdateTestExecutionRequest;
import com.taskflow.dto.test.CreateTestRunRequest;
import com.taskflow.dto.test.TestRunResponse;
import com.taskflow.service.TestRunService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/test-runs")
public class TestRunController {

    private static final Set<String> ALLOWED_SORT = Set.of("createdAt", "name", "status");

    private final TestRunService testRunService;

    public TestRunController(TestRunService testRunService) {
        this.testRunService = testRunService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<TestRunResponse> create(@Valid @RequestBody CreateTestRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(testRunService.create(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TestRunResponse>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assignedToId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        String safeSort = ALLOWED_SORT.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(dir, safeSort));
        return ResponseEntity.ok(testRunService.list(projectId, status, assignedToId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestRunResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(testRunService.getById(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<TestRunResponse> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ResponseEntity.ok(testRunService.updateStatus(id, status));
    }
}
