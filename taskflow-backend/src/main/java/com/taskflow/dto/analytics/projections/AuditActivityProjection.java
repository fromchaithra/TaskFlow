package com.taskflow.dto.analytics.projections;

import java.time.LocalDate;

/** Bounded audit-log aggregation used by executive reporting. */
public interface AuditActivityProjection {
    LocalDate getBucketDay();

    String getAction();

    Long getEventCount();
}
