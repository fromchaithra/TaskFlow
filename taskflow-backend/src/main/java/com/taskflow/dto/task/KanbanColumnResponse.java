package com.taskflow.dto.task;

import com.taskflow.enums.TaskStatus;

import java.util.List;

public record KanbanColumnResponse(
        TaskStatus status,
        long totalCount,
        int page,
        int limit,
        boolean hasMore,
        List<TaskSummaryResponse> tasks
) {
}
