package com.taskflow.dto.bug;

import com.taskflow.enums.BugSeverity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBugSeverityRequest(
        @NotNull(message = "Bug severity is required")
        BugSeverity severity,

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        String remarks
) {
}
