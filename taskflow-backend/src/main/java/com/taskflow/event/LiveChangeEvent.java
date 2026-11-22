package com.taskflow.event;

import java.time.Instant;

/**
 * Internal, minimal signal that an authorized mutation was committed.
 *
 * <p>This type never leaves the backend. It deliberately carries no actor,
 * email, IP address, audit JSON, or user details - only the identifiers needed
 * to decide which live subscriptions are allowed to refresh.</p>
 */
public record LiveChangeEvent(
        Long organizationId,
        Long projectId,
        String entityType,
        Long entityId,
        String action,
        Instant occurredAt
) {
}
