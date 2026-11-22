package com.taskflow.dto.organization;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.Team;

import java.time.Instant;
import java.util.List;

public record TeamDetailResponse(
        Long id,
        String name,
        String description,
        UserSummaryDto lead,
        boolean active,
        List<UserSummaryDto> members,
        Instant createdAt,
        Instant updatedAt
) {
    public static TeamDetailResponse from(Team team, List<UserSummaryDto> members) {
        return new TeamDetailResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                UserSummaryDto.from(team.getLeadUser()),
                team.isActive(),
                members,
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }
}
