package com.taskflow.dto.bug;

import com.taskflow.enums.BugStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBugStatusRequest(
        @NotNull(message = "Target bug status is required")
        BugStatus status,

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        String remarks
) {
}
