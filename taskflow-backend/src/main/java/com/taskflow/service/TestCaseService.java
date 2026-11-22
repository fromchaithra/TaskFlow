package com.taskflow.service;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.test.CreateTestCaseRequest;
import com.taskflow.dto.test.TestCaseResponse;
import com.taskflow.dto.test.UpdateTestCaseRequest;
import org.springframework.data.domain.Pageable;

public interface TestCaseService {
    TestCaseResponse create(CreateTestCaseRequest request);
    PagedResponse<TestCaseResponse> list(Long projectId, Boolean archived, String priority, String search, Pageable pageable);
    TestCaseResponse getById(Long id);
    TestCaseResponse update(Long id, UpdateTestCaseRequest request);
    void archive(Long id);
}
