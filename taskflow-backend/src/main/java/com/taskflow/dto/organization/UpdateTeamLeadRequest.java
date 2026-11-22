package com.taskflow.dto.organization;

import jakarta.validation.constraints.NotNull;

public record UpdateTeamLeadRequest(
        @NotNull(message = "Team lead user ID is required")
        Long leadUserId
) {
}
