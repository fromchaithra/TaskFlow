package com.taskflow.dto.test;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.TestRun;
import com.taskflow.enums.TestRunStatus;

import java.time.Instant;

public record TestRunResponse(
        Long id,
        Long projectId,
        String projectKey,
        Long suiteId,
        String name,
        TestRunStatus status,
        UserSummaryDto assignedTo,
        UserSummaryDto createdBy,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static TestRunResponse from(TestRun tr) {
        return new TestRunResponse(
                tr.getId(),
                tr.getProject().getId(),
                tr.getProject().getProjectKey(),
                tr.getSuite() != null ? tr.getSuite().getId() : null,
                tr.getName(),
                tr.getStatus(),
                tr.getAssignedTo() != null ? UserSummaryDto.from(tr.getAssignedTo()) : null,
                UserSummaryDto.from(tr.getCreatedBy()),
                tr.getStartedAt(),
                tr.getCompletedAt(),
                tr.getCreatedAt(),
                tr.getUpdatedAt()
        );
    }
}
