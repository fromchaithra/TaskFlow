package com.taskflow.service;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.test.CreateTestSuiteRequest;
import com.taskflow.dto.test.UpdateTestSuiteRequest;
import com.taskflow.dto.test.TestSuiteResponse;
import org.springframework.data.domain.Pageable;

public interface TestSuiteService {
    TestSuiteResponse create(CreateTestSuiteRequest request);
    PagedResponse<TestSuiteResponse> list(Long projectId, Boolean archived, String search, Pageable pageable);
    TestSuiteResponse getById(Long id);
    TestSuiteResponse update(Long id, UpdateTestSuiteRequest request);
    void archive(Long id);
    void addCase(Long suiteId, Long testCaseId);
    void removeCase(Long suiteId, Long testCaseId);
}
