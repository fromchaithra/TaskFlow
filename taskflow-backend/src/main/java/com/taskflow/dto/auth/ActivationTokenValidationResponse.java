package com.taskflow.dto.auth;

import java.time.Instant;

public record ActivationTokenValidationResponse(
        String email,
        String fullName,
        Instant expiresAt
) {
}
