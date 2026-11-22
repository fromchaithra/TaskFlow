package com.taskflow.dto.bug;

import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.PriorityLevel;
import jakarta.validation.constraints.Size;

public record UpdateBugRequest(
        @Size(min = 3, max = 200, message = "Bug title must be between 3 and 200 characters")
        String title,

        @Size(min = 5, max = 8000, message = "Bug description must be between 5 and 8000 characters")
        String description,

        @Size(max = 8000, message = "Steps to reproduce must not exceed 8000 characters")
        String stepsToReproduce,

        BugSeverity severity,

        PriorityLevel priority,

        Long relatedTaskId,

        Long assigneeId,

        Long expectedVersion,

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        String remarks
) {
}
