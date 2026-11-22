package com.taskflow.dto.task;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.Task;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TaskDetailResponse(
        Long id,
        Long projectId,
        String projectKey,
        String projectName,
        String taskKey,
        String title,
        String description,
        PriorityLevel priority,
        TaskStatus status,
        UserSummaryDto assignee,
        UserSummaryDto reporter,
        Long sprintId,
        String sprintName,
        Long parentTaskId,
        String parentTaskKey,
        BigDecimal estimatedHours,
        LocalDate dueDate,
        boolean overdue,
        Integer boardOrder,
        Long version,
        long commentCount,
        long attachmentCount,
        List<TaskSummaryResponse> subtasks,
        List<TaskHistoryResponse> history,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskDetailResponse from(
            Task task,
            long commentCount,
            long attachmentCount,
            List<TaskSummaryResponse> subtasks,
            List<TaskHistoryResponse> history
    ) {
        boolean isOverdue = task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDate.now())
                && task.getStatus() != TaskStatus.DONE;
        return new TaskDetailResponse(
                task.getId(),
                task.getProject().getId(),
                task.getProject().getProjectKey(),
                task.getProject().getName(),
                task.getTaskKey(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getStatus(),
                UserSummaryDto.from(task.getAssignee()),
                UserSummaryDto.from(task.getReporter()),
                task.getSprint() != null ? task.getSprint().getId() : null,
                task.getSprint() != null ? task.getSprint().getName() : null,
                task.getParentTask() != null ? task.getParentTask().getId() : null,
                task.getParentTask() != null ? task.getParentTask().getTaskKey() : null,
                task.getEstimatedHours(),
                task.getDueDate(),
                isOverdue,
                task.getBoardOrder(),
                task.getVersion(),
                commentCount,
                attachmentCount,
                subtasks,
                history,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
