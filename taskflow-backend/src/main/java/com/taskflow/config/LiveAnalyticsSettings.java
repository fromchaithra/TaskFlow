package com.taskflow.config;

/**
 * Bounded runtime settings for the live analytics foundation.
 *
 * <p>Values are intentionally hard-coded constants for this phase: no new
 * configuration surface, no external infrastructure, and every resource is
 * capped. Tests construct custom instances to exercise the limits.</p>
 */
public record LiveAnalyticsSettings(
        int maxConnectionsPerUser,
        int maxConnectionsGlobal,
        long emitterTimeoutMs,
        long heartbeatIntervalMs,
        long coalesceWindowMs,
        int maxPendingSignals,
        long revalidateIntervalMs,
        long trendCacheTtlMs,
        int activityPageSize
) {

    public static LiveAnalyticsSettings defaults() {
        return new LiveAnalyticsSettings(
                3,          // connections per user
                200,        // global connections
                840_000L,   // 14 min emitter timeout (below the 15 min JWT lifetime)
                20_000L,    // heartbeat
                1_000L,     // coalescing window
                200,        // bounded pending signal queue
                600_000L,   // revalidate authorization every 10 min
                60_000L,    // historical trend cache TTL
                10          // activity feed page size
        );
    }

    public LiveAnalyticsSettings {
        if (maxConnectionsPerUser < 1 || maxConnectionsGlobal < 1
                || emitterTimeoutMs < 1 || heartbeatIntervalMs < 1
                || coalesceWindowMs < 1 || maxPendingSignals < 1
                || revalidateIntervalMs < 1 || trendCacheTtlMs < 1
                || activityPageSize < 1) {
            throw new IllegalArgumentException("Live analytics settings must be positive");
        }
    }
}
