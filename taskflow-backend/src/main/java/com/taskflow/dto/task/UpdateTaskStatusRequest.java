package com.taskflow.dto.task;

import com.taskflow.enums.TaskStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTaskStatusRequest(
        @NotNull(message = "Target task status is required")
        TaskStatus status,

        @Min(value = 0, message = "Board order must be zero or positive")
        Integer boardOrder,

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        String remarks
) {
}
