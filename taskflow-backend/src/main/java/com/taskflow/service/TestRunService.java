package com.taskflow.service;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.test.TestExecutionResponse;
import com.taskflow.dto.test.UpdateTestExecutionRequest;
import com.taskflow.dto.test.CreateTestRunRequest;
import com.taskflow.dto.test.TestRunResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TestRunService {
    TestRunResponse create(CreateTestRunRequest request);
    PagedResponse<TestRunResponse> list(Long projectId, String status, Long assignedToId, Pageable pageable);
    TestRunResponse getById(Long id);
    TestRunResponse updateStatus(Long id, String status);
}
