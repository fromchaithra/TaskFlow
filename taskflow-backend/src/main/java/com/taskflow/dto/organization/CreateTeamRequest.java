package com.taskflow.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTeamRequest(
        @NotBlank(message = "Team name is required")
        @Size(min = 2, max = 100)
        String name,

        @Size(max = 2000)
        String description,

        Long leadUserId,

        List<Long> memberIds
) {
}
