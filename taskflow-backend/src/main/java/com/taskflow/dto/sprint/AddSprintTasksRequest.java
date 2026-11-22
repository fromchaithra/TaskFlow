package com.taskflow.dto.sprint;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddSprintTasksRequest(
        @NotEmpty(message = "At least one task ID is required")
        List<Long> taskIds
) {
}
