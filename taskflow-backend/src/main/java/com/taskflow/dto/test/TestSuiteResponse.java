package com.taskflow.dto.test;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.TestSuite;

import java.time.Instant;

public record TestSuiteResponse(
        Long id,
        Long projectId,
        String projectKey,
        String name,
        String description,
        boolean archived,
        UserSummaryDto createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static TestSuiteResponse from(TestSuite ts) {
        return new TestSuiteResponse(
                ts.getId(),
                ts.getProject().getId(),
                ts.getProject().getProjectKey(),
                ts.getName(),
                ts.getDescription(),
                ts.isArchived(),
                UserSummaryDto.from(ts.getCreatedBy()),
                ts.getCreatedAt(),
                ts.getUpdatedAt()
        );
    }
}
