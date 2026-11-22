package com.taskflow.dto.user;

import com.taskflow.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 180)
        String email,

        @Size(max = 100)
        String jobTitle,

        @NotNull(message = "Role is required")
        RoleName roleName,

        @Size(max = 16)
        String avatarColor
) {
}
