package com.taskflow.dto.bug;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenBugRequest(
        @NotBlank(message = "Reason for reopening the bug is required")
        @Size(min = 3, max = 500, message = "Reason must be between 3 and 500 characters")
        String reason
) {
}
