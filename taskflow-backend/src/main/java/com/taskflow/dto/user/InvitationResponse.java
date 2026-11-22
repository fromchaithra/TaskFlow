package com.taskflow.dto.user;

import java.time.Instant;

public record InvitationResponse(
        UserResponse user,
        String activationLink,
        Instant expiresAt
) {
}
