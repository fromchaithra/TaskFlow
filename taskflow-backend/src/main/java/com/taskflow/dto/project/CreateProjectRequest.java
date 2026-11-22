package com.taskflow.dto.project;

import com.taskflow.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(min = 2, max = 150, message = "Project name must be between 2 and 150 characters")
        String name,

        @NotBlank(message = "Project key is required")
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$",
                message = "Project key must be 2 to 10 alphanumeric characters starting with a letter"
        )
        String projectKey,

        @Size(max = 4000, message = "Description must not exceed 4000 characters")
        String description,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        LocalDate endDate,

        ProjectStatus initialStatus,

        Long managerId,

        Long teamId,

        List<Long> memberIds
) {
}
