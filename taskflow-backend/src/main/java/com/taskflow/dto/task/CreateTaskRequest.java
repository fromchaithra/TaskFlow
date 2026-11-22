package com.taskflow.dto.task;

import com.taskflow.enums.PriorityLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTaskRequest(
        @NotNull(message = "Project ID is required")
        Long projectId,

        @NotBlank(message = "Task title is required")
        @Size(min = 3, max = 200, message = "Task title must be between 3 and 200 characters")
        String title,

        @Size(max = 8000, message = "Task description must not exceed 8000 characters")
        String description,

        PriorityLevel priority,

        Long assigneeId,

        Long sprintId,

        Long parentTaskId,

        @DecimalMin(value = "0.25", message = "Estimated hours must be at least 0.25")
        @DecimalMax(value = "9999.99", message = "Estimated hours must not exceed 9999.99")
        BigDecimal estimatedHours,

        LocalDate dueDate
) {
}
