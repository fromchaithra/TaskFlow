package com.taskflow.dto.sprint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateSprintRequest(
        @NotBlank(message = "Sprint name is required")
        @Size(min = 2, max = 120, message = "Sprint name must be between 2 and 120 characters")
        String name,

        @Size(max = 4000, message = "Sprint goal must not exceed 4000 characters")
        String goal,

        @NotNull(message = "Sprint start date is required")
        LocalDate startDate,

        @NotNull(message = "Sprint end date is required")
        LocalDate endDate
) {
}
