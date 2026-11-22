package com.taskflow.dto.analytics.projections;

import java.time.LocalDate;

/** Bounded daily count used for trend aggregation. */
public interface DailyCountProjection {
    LocalDate getBucketDay();

    Long getTaskCount();
}
