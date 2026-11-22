package com.taskflow.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120)
        String fullName,

        @Size(max = 100)
        String jobTitle,

        @Size(max = 16)
        String avatarColor
) {
}
