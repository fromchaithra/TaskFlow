package com.taskflow.dto.sprint;

import com.taskflow.dto.task.TaskSummaryResponse;
import com.taskflow.enums.SprintStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SprintReportResponse(
        Long sprintId,
        Long projectId,
        String projectKey,
        String sprintName,
        SprintStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Instant startedAt,
        Instant completedAt,
        long totalTasks,
        long completedTasks,
        long inProgressTasks,
        long inReviewTasks,
        long todoTasks,
        long blockedTasks,
        long remainingTasks,
        double completionPercentage,
        List<TaskSummaryResponse> completedTaskList,
        List<TaskSummaryResponse> incompleteTaskList
) {
}
