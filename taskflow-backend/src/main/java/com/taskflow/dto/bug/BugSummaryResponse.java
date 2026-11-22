package com.taskflow.dto.bug;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.Bug;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.PriorityLevel;

import java.time.Instant;

public record BugSummaryResponse(
        Long id,
        Long projectId,
        String projectKey,
        String bugKey,
        Long relatedTaskId,
        String relatedTaskKey,
        String title,
        BugSeverity severity,
        PriorityLevel priority,
        BugStatus status,
        UserSummaryDto reporter,
        UserSummaryDto assignee,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static BugSummaryResponse from(Bug bug) {
        return new BugSummaryResponse(
                bug.getId(),
                bug.getProject().getId(),
                bug.getProject().getProjectKey(),
                bug.getBugKey(),
                bug.getRelatedTask() != null ? bug.getRelatedTask().getId() : null,
                bug.getRelatedTask() != null ? bug.getRelatedTask().getTaskKey() : null,
                bug.getTitle(),
                bug.getSeverity(),
                bug.getPriority(),
                bug.getStatus(),
                UserSummaryDto.from(bug.getReporter()),
                UserSummaryDto.from(bug.getAssignee()),
                bug.getVersion(),
                bug.getCreatedAt(),
                bug.getUpdatedAt()
        );
    }
}
