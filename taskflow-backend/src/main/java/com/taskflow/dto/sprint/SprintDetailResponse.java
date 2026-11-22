package com.taskflow.dto.sprint;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.dto.task.TaskSummaryResponse;
import com.taskflow.entity.Sprint;
import com.taskflow.enums.SprintStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SprintDetailResponse(
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
        List<TaskSummaryResponse> tasks,
        Instant createdAt,
        Instant updatedAt
) {
    public static SprintDetailResponse from(Sprint sprint, List<TaskSummaryResponse> tasks) {
        return new SprintDetailResponse(
                sprint.getId(),
                sprint.getProject().getId(),
                sprint.getProject().getProjectKey(),
                sprint.getName(),
                sprint.getGoal(),
                sprint.getStartDate(),
                sprint.getEndDate(),
                sprint.getStatus(),
                tasks.size(),
                sprint.getStartedAt(),
                sprint.getCompletedAt(),
                UserSummaryDto.from(sprint.getCreatedBy()),
                tasks,
                sprint.getCreatedAt(),
                sprint.getUpdatedAt()
        );
    }
}
