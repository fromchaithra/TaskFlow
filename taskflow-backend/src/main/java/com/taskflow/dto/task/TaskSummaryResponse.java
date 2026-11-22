package com.taskflow.dto.task;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.Task;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TaskSummaryResponse(
        Long id,
        Long projectId,
        String projectKey,
        String taskKey,
        String title,
        PriorityLevel priority,
        TaskStatus status,
        UserSummaryDto assignee,
        UserSummaryDto reporter,
        Long sprintId,
        String sprintName,
        Long parentTaskId,
        BigDecimal estimatedHours,
        LocalDate dueDate,
        boolean overdue,
        Integer boardOrder,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskSummaryResponse from(Task task) {
        boolean isOverdue = task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDate.now())
                && task.getStatus() != TaskStatus.DONE;
        return new TaskSummaryResponse(
                task.getId(),
                task.getProject().getId(),
                task.getProject().getProjectKey(),
                task.getTaskKey(),
                task.getTitle(),
                task.getPriority(),
                task.getStatus(),
                UserSummaryDto.from(task.getAssignee()),
                UserSummaryDto.from(task.getReporter()),
                task.getSprint() != null ? task.getSprint().getId() : null,
                task.getSprint() != null ? task.getSprint().getName() : null,
                task.getParentTask() != null ? task.getParentTask().getId() : null,
                task.getEstimatedHours(),
                task.getDueDate(),
                isOverdue,
                task.getBoardOrder(),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
