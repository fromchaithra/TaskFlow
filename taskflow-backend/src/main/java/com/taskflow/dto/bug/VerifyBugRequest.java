package com.taskflow.dto.bug;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VerifyBugRequest(
        @NotNull(message = "Verification decision (approved) is required")
        Boolean approved,

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        String remarks
) {
}
