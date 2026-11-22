package com.taskflow.dto.user;

import com.taskflow.enums.RoleName;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Role is required")
        RoleName roleName
) {
}
