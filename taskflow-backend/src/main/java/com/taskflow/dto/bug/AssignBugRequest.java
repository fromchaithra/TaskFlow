package com.taskflow.dto.bug;

import jakarta.validation.constraints.Size;

public record AssignBugRequest(
        Long assigneeId,

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        String remarks
) {
}
