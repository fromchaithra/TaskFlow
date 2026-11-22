package com.taskflow.dto.auth;

public record AuthResult(AuthResponse response, String rawRefreshToken) {
}
