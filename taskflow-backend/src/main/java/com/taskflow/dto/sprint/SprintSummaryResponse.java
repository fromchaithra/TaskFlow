package com.taskflow.dto.sprint;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.Sprint;
import com.taskflow.enums.SprintStatus;

import java.time.Instant;
import java.time.LocalDate;

public record SprintSummaryResponse(
        Long id,
        Long projectId,
        String projectKey,
        String name,
        String goal,
        LocalDate startDate,
        LocalDate endDate,
        SprintStatus status,
        long taskCount,
        Instant startedAt,
        Instant completedAt,
        UserSummaryDto createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static SprintSummaryResponse from(Sprint sprint, long taskCount) {
        return new SprintSummaryResponse(
                sprint.getId(),
                sprint.getProject().getId(),
                sprint.getProject().getProjectKey(),
                sprint.getName(),
                sprint.getGoal(),
                sprint.getStartDate(),
                sprint.getEndDate(),
                sprint.getStatus(),
                taskCount,
                sprint.getStartedAt(),
                sprint.getCompletedAt(),
                UserSummaryDto.from(sprint.getCreatedBy()),
                sprint.getCreatedAt(),
                sprint.getUpdatedAt()
        );
    }
}
