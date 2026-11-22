package com.taskflow.dto.analytics;

import java.util.Set;

/**
 * Resolved, authorization-checked scope for a single analytics request.
 *
 * <p>{@code allowedProjectIds} is the authoritative project set derived from
 * the existing project security rules; analytics queries must use it instead
 * of re-deriving visibility per row.</p>
 */
public record AnalyticsScope(
        Long organizationId,
        Long viewerId,
        boolean admin,
        Set<Long> allowedProjectIds,
        Set<Long> teamIds,
        Set<Long> employeeIds,
        AnalyticsQuery query
) {
}
