package com.taskflow.service;

import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.live.LiveAnalyticsSnapshot;
import com.taskflow.security.CustomUserDetails;

/** Builds the small, authorized live snapshot from existing analytics services. */
public interface LiveAnalyticsSnapshotService {

    /**
     * Builds a snapshot for the supplied principal. The principal is explicit so
     * the same code path works in request threads and in the coalescing worker.
     * All scope resolution still goes through the existing authorization service.
     */
    LiveAnalyticsSnapshot buildSnapshot(AnalyticsQuery query, CustomUserDetails principal);
}
