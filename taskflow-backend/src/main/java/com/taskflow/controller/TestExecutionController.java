package com.taskflow.controller;

import com.taskflow.dto.test.TestExecutionResponse;
import com.taskflow.dto.test.UpdateTestExecutionRequest;
import com.taskflow.service.TestExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/test-runs/{runId}/executions")
public class TestExecutionController {

    private final TestExecutionService testExecutionService;

    public TestExecutionController(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }

    @GetMapping
    public ResponseEntity<List<TestExecutionResponse>> list(@PathVariable Long runId) {
        return ResponseEntity.ok(testExecutionService.listByRun(runId));
    }

    @PatchMapping("/{executionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<TestExecutionResponse> record(
            @PathVariable Long runId,
            @PathVariable Long executionId,
            @Valid @RequestBody UpdateTestExecutionRequest request
    ) {
        return ResponseEntity.ok(testExecutionService.recordResult(runId, executionId, request));
    }

    @PostMapping("/{executionId}/create-bug")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER')")
    public ResponseEntity<TestExecutionResponse> createBug(
            @PathVariable Long runId,
            @PathVariable Long executionId
    ) {
        return ResponseEntity.ok(testExecutionService.createBugFromFailure(runId, executionId));
    }
}
