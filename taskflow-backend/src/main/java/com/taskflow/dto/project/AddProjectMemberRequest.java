package com.taskflow.dto.project;

import com.taskflow.enums.RoleName;
import jakarta.validation.constraints.NotNull;

public record AddProjectMemberRequest(
        @NotNull(message = "User ID is required")
        Long userId,

        RoleName projectRole
) {
}
