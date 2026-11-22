package com.taskflow.controller;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.test.CreateTestSuiteRequest;
import com.taskflow.dto.test.UpdateTestSuiteRequest;
import com.taskflow.dto.test.TestSuiteResponse;
import com.taskflow.service.TestSuiteService;
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
@RequestMapping("/api/v1/test-suites")
public class TestSuiteController {

    private static final Set<String> ALLOWED_SORT = Set.of("createdAt", "name");

    private final TestSuiteService testSuiteService;

    public TestSuiteController(TestSuiteService testSuiteService) {
        this.testSuiteService = testSuiteService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<TestSuiteResponse> create(@Valid @RequestBody CreateTestSuiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(testSuiteService.create(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TestSuiteResponse>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        String safeSort = ALLOWED_SORT.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(dir, safeSort));
        return ResponseEntity.ok(testSuiteService.list(projectId, archived, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestSuiteResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(testSuiteService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<TestSuiteResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateTestSuiteRequest request) {
        return ResponseEntity.ok(testSuiteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        testSuiteService.archive(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cases/{caseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<Void> addCase(@PathVariable Long id, @PathVariable Long caseId) {
        testSuiteService.addCase(id, caseId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/cases/{caseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<Void> removeCase(@PathVariable Long id, @PathVariable Long caseId) {
        testSuiteService.removeCase(id, caseId);
        return ResponseEntity.noContent().build();
    }
}
