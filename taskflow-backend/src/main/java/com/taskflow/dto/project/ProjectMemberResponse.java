package com.taskflow.dto.project;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.ProjectMember;
import com.taskflow.enums.RoleName;

import java.time.Instant;

public record ProjectMemberResponse(
        Long id,
        UserSummaryDto user,
        RoleName projectRole,
        UserSummaryDto addedBy,
        Instant joinedAt
) {
    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(),
                UserSummaryDto.from(member.getUser()),
                member.getProjectRole(),
                UserSummaryDto.from(member.getAddedBy()),
                member.getJoinedAt()
        );
    }
}
