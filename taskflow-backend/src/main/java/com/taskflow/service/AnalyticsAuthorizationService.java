package com.taskflow.service;

import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.AnalyticsScope;

/**
 * Resolves the organization and project scope for an analytics request by
 * reusing the application's existing project security rules.
 */
public interface AnalyticsAuthorizationService {

    /**
     * Validates the requested filters against the current user and returns the
     * authoritative set of projects the user may include in analytics results.
     */
    AnalyticsScope resolveScope(AnalyticsQuery query);

    /**
     * Authoritative project ids visible to the current viewer without applying
     * any client-supplied filters. Used by the live activity feed so it cannot
     * expose events from projects the viewer may not see.
     */
    java.util.Set<Long> resolveVisibleProjectIds();
}
