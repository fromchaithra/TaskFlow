package com.taskflow.dto.test;

import java.util.List;

public record CreateTestRunRequest(
        Long projectId,
        Long suiteId,
        String name,
        Long assignedToId,
        List<Long> testCaseIds
) {}
