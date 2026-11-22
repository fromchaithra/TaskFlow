package com.taskflow.dto.project;

import com.taskflow.enums.ProjectStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProjectStatusRequest(
        @NotNull(message = "Project status is required")
        ProjectStatus status
) {
}
