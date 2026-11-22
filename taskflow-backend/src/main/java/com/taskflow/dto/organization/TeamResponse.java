package com.taskflow.dto.organization;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.Team;

import java.time.Instant;

public record TeamResponse(
        Long id,
        String name,
        String description,
        UserSummaryDto lead,
        long memberCount,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static TeamResponse from(Team team, long memberCount) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                UserSummaryDto.from(team.getLeadUser()),
                memberCount,
                team.isActive(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }
}
