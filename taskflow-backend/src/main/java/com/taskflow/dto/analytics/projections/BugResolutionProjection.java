package com.taskflow.dto.analytics.projections;

import java.time.Instant;

/** First resolution event per bug, paired with the bug creation time. */
public record BugResolutionProjection(Long bugId, Instant createdAt, Instant resolvedAt) {
}
