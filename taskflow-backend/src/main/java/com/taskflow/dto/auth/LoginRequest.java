package com.taskflow.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 180)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128)
        String password
) {
}
