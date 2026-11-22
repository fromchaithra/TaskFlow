package com.taskflow.dto.common;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiErrorResponse ofValidation(int status, String message, String path, List<FieldViolation> violations) {
        return new ApiErrorResponse(Instant.now(), status, "VALIDATION_ERROR", message, path, violations);
    }
}
