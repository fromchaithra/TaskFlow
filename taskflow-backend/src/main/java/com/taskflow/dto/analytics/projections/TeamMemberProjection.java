package com.taskflow.dto.analytics.projections;

/** Team membership pair used for team-based analytics filters. */
public record TeamMemberProjection(Long teamId, Long userId) {
}
