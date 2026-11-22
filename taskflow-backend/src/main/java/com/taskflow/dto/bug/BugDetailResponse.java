package com.taskflow.dto.bug;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.Bug;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.PriorityLevel;

import java.time.Instant;
import java.util.List;

public record BugDetailResponse(
        Long id,
        Long projectId,
        String projectKey,
        String projectName,
        String bugKey,
        Long relatedTaskId,
        String relatedTaskKey,
        String title,
        String description,
        String stepsToReproduce,
        BugSeverity severity,
        PriorityLevel priority,
        BugStatus status,
        UserSummaryDto reporter,
        UserSummaryDto assignee,
        Long version,
        long commentCount,
        long attachmentCount,
        List<BugHistoryResponse> history,
        Instant createdAt,
        Instant updatedAt
) {
    public static BugDetailResponse from(
            Bug bug,
            long commentCount,
            long attachmentCount,
            List<BugHistoryResponse> history
    ) {
        return new BugDetailResponse(
                bug.getId(),
                bug.getProject().getId(),
                bug.getProject().getProjectKey(),
                bug.getProject().getName(),
                bug.getBugKey(),
                bug.getRelatedTask() != null ? bug.getRelatedTask().getId() : null,
                bug.getRelatedTask() != null ? bug.getRelatedTask().getTaskKey() : null,
                bug.getTitle(),
                bug.getDescription(),
                bug.getStepsToReproduce(),
                bug.getSeverity(),
                bug.getPriority(),
                bug.getStatus(),
                UserSummaryDto.from(bug.getReporter()),
                UserSummaryDto.from(bug.getAssignee()),
                bug.getVersion(),
                commentCount,
                attachmentCount,
                history,
                bug.getCreatedAt(),
                bug.getUpdatedAt()
        );
    }
}
