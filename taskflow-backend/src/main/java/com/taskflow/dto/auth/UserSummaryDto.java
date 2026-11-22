package com.taskflow.dto.auth;

import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;

public record UserSummaryDto(
        Long id,
        String employeeCode,
        String fullName,
        String email,
        String jobTitle,
        String avatarColor,
        RoleName role,
        boolean active
) {
    public static UserSummaryDto from(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryDto(
                user.getId(),
                user.getEmployeeCode(),
                user.getFullName(),
                user.getEmail(),
                user.getJobTitle(),
                user.getAvatarColor(),
                user.getPrimaryRoleName(),
                user.isActive()
        );
    }
}
