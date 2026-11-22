package com.taskflow.dto.project;

import com.taskflow.enums.RoleName;
import jakarta.validation.constraints.NotNull;

public record UpdateProjectMemberRoleRequest(
        @NotNull(message = "Project role is required")
        RoleName projectRole
) {
}
