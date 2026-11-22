package com.taskflow.dto.analytics.projections;

import java.time.Instant;

/** Projection of a status-change event timestamp with its acting entity id. */
public record StatusEventProjection(Long entityId, Instant occurredAt, String newValue) {
}
