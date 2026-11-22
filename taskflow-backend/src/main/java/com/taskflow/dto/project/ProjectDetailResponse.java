package com.taskflow.dto.project;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.Project;
import com.taskflow.enums.ProjectStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProjectDetailResponse(
        Long id,
        String projectKey,
        String name,
        String description,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate,
        UserSummaryDto manager,
        long memberCount,
        long totalTasks,
        long completedTasks,
        long overdueTasks,
        long openBugs,
        int progressPercent,
        List<ProjectMemberResponse> members,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectDetailResponse from(
            Project project,
            List<ProjectMemberResponse> members,
            long totalTasks,
            long completedTasks,
            long overdueTasks,
            long openBugs
    ) {
        int progressPercent = totalTasks > 0
                ? (int) Math.round((completedTasks * 100.0) / totalTasks)
                : 0;
        return new ProjectDetailResponse(
                project.getId(),
                project.getProjectKey(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate(),
                UserSummaryDto.from(project.getManager()),
                members.size(),
                totalTasks,
                completedTasks,
                overdueTasks,
                openBugs,
                progressPercent,
                members,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
