package com.taskflow.dto.analytics.projections;

import java.time.Instant;

/** A QA execution result name and the timestamp used to bucket it. */
public record QaResultProjection(String result, Instant occurredAt) {
}
