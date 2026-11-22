package com.taskflow.controller;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.test.CreateTestCaseRequest;
import com.taskflow.dto.test.TestCaseResponse;
import com.taskflow.dto.test.UpdateTestCaseRequest;
import com.taskflow.dto.test.TestExecutionResponse;
import com.taskflow.service.TestCaseService;
import com.taskflow.service.TestExecutionService;
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
@RequestMapping("/api/v1/test-cases")
public class TestCaseController {

    private static final Set<String> ALLOWED_SORT = Set.of("createdAt", "title", "priority");

    private final TestCaseService testCaseService;
    private final TestExecutionService testExecutionService;

    public TestCaseController(TestCaseService testCaseService, TestExecutionService testExecutionService) {
        this.testCaseService = testCaseService;
        this.testExecutionService = testExecutionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<TestCaseResponse> create(@Valid @RequestBody CreateTestCaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(testCaseService.create(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TestCaseResponse>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        String safeSort = ALLOWED_SORT.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(dir, safeSort));
        return ResponseEntity.ok(testCaseService.list(projectId, archived, priority, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestCaseResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(testCaseService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<TestCaseResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateTestCaseRequest request) {
        return ResponseEntity.ok(testCaseService.update(id, request));
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<java.util.List<TestExecutionResponse>> executions(@PathVariable Long id) {
        return ResponseEntity.ok(testExecutionService.listByTestCase(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        testCaseService.archive(id);
        return ResponseEntity.noContent().build();
    }
}
