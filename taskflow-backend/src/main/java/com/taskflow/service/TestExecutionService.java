package com.taskflow.service;

import com.taskflow.dto.test.TestExecutionResponse;
import com.taskflow.dto.test.UpdateTestExecutionRequest;
import java.util.List;

public interface TestExecutionService {
    TestExecutionResponse recordResult(Long runId, Long executionId, UpdateTestExecutionRequest request);
    java.util.List<TestExecutionResponse> listByRun(Long runId);
    java.util.List<TestExecutionResponse> listByTestCase(Long testCaseId);
    TestExecutionResponse createBugFromFailure(Long runId, Long executionId);
}
