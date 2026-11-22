package com.taskflow.dto.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Active flag is required")
        Boolean active
) {
}
