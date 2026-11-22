package com.taskflow.dto.auth;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AuthUserDto user
) {
    public static AuthResponse bearer(String accessToken, long expiresIn, AuthUserDto user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
