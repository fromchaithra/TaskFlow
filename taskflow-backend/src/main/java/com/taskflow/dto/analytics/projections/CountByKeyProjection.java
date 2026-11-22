package com.taskflow.dto.analytics.projections;

/** Generic grouped count (project id, team id, user id, ...). */
public record CountByKeyProjection(Long key, Long count) {
}
