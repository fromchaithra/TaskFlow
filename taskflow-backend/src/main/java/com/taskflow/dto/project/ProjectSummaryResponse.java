package com.taskflow.dto.project;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.Project;
import com.taskflow.enums.ProjectStatus;

import java.time.Instant;
import java.time.LocalDate;

public record ProjectSummaryResponse(
        Long id,
        String projectKey,
        String name,
        String description,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate,
        UserSummaryDto manager,
        long memberCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectSummaryResponse from(Project project, long memberCount) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getProjectKey(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate(),
                UserSummaryDto.from(project.getManager()),
                memberCount,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
