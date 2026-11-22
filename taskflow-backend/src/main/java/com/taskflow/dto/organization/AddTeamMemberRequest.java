package com.taskflow.dto.organization;

import jakarta.validation.constraints.NotNull;

public record AddTeamMemberRequest(
        @NotNull(message = "User ID is required")
        Long userId
) {
}
