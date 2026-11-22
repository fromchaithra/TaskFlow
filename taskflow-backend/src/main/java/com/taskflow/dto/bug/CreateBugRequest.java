package com.taskflow.dto.bug;

import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.PriorityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBugRequest(
        @NotNull(message = "Project ID is required")
        Long projectId,

        Long relatedTaskId,

        @NotBlank(message = "Bug title is required")
        @Size(min = 3, max = 200, message = "Bug title must be between 3 and 200 characters")
        String title,

        @NotBlank(message = "Bug description is required")
        @Size(min = 5, max = 8000, message = "Bug description must be between 5 and 8000 characters")
        String description,

        @Size(max = 8000, message = "Steps to reproduce must not exceed 8000 characters")
        String stepsToReproduce,

        @NotNull(message = "Bug severity is required")
        BugSeverity severity,

        @NotNull(message = "Bug priority is required")
        PriorityLevel priority,

        Long assigneeId
) {
}
