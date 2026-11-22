package com.taskflow.dto.user;

import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;

import java.time.Instant;

public record UserResponse(
        Long id,
        String employeeCode,
        String email,
        String fullName,
        String jobTitle,
        String avatarColor,
        RoleName role,
        boolean active,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmployeeCode(),
                user.getEmail(),
                user.getFullName(),
                user.getJobTitle(),
                user.getAvatarColor(),
                user.getPrimaryRoleName(),
                user.isActive(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
