package com.taskflow.dto.task;

import com.taskflow.enums.TaskStatus;

import java.util.Map;

public record KanbanBoardResponse(
        Long projectId,
        String projectKey,
        String projectName,
        int page,
        int limitPerColumn,
        long totalTasks,
        Map<TaskStatus, KanbanColumnResponse> columns
) {
}
