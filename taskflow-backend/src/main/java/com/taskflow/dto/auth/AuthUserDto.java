package com.taskflow.dto.auth;

import com.taskflow.enums.RoleName;

import java.time.Instant;
import java.util.List;

public record AuthUserDto(
        Long id,
        Long organizationId,
        String organizationName,
        String employeeCode,
        String email,
        String fullName,
        String jobTitle,
        String avatarColor,
        RoleName role,
        boolean active,
        Instant lastLoginAt,
        List<String> teams
) {
}
