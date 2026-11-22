package com.taskflow.dto.task;

import jakarta.validation.constraints.Size;

public record AssignTaskRequest(
        Long assigneeId,

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        String remarks
) {
}
