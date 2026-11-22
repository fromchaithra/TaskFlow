package com.taskflow.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(min = 2, max = 150, message = "Project name must be between 2 and 150 characters")
        String name,

        @Size(max = 4000, message = "Description must not exceed 4000 characters")
        String description,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        LocalDate endDate,

        Long managerId
) {
}
