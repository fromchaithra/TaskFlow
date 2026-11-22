package com.taskflow.service.impl;

import com.taskflow.config.LiveAnalyticsSettings;
import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.MetricValue;
import com.taskflow.dto.analytics.TimeBucket;
import com.taskflow.dto.activity.ActivityResponse;
import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.live.LiveAnalyticsSnapshot;
import com.taskflow.dto.live.LiveTaskSummaryResponse;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.service.AnalyticsService;
import com.taskflow.service.ActivityService;
import com.taskflow.service.LiveAnalyticsSnapshotService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assembles the approved live snapshot exclusively from existing analytics
 * services. No metric is recalculated here and no internal event is exposed.
 *
 * <p>The historical completion trend is kept in a short-TTL cache so live
 * mutation events refresh only the current-state metrics. Cache entries are
 * keyed by the full authorization/filter signature, so differently scoped
 * viewers can never share trend data.</p>
 */
@Service
public class LiveAnalyticsSnapshotServiceImpl implements LiveAnalyticsSnapshotService {

    private final AnalyticsService analyticsService;
    private final ActivityService activityService;
    private final LiveAnalyticsSettings settings;
    private final AtomicLong revision = new AtomicLong();
    private final Map<TrendKey, CachedTrend> trendCache = new ConcurrentHashMap<>();

    public LiveAnalyticsSnapshotServiceImpl(AnalyticsService analyticsService,
                                             ActivityService activityService,
                                             LiveAnalyticsSettings settings) {
        this.analyticsService = analyticsService;
        this.activityService = activityService;
        this.settings = settings;
    }

    @Override
    @Transactional(readOnly = true)
    public LiveAnalyticsSnapshot buildSnapshot(AnalyticsQuery query, CustomUserDetails principal) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(new SecurityContextImpl(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));

            LiveTaskSummaryResponse summary = analyticsService.getLiveTaskSummary(query);
            CachedTrend trend = cachedTrend(query, principal);
            PagedResponse<ActivityResponse> activity =
                    activityService.recentActivity(PageRequest.of(0, settings.activityPageSize()));

            return new LiveAnalyticsSnapshot(
                    Instant.now(),
                    revision.incrementAndGet(),
                    summary.kpis(),
                    summary.statusDistribution(),
                    List.copyOf(activity.content()),
                    trend.buckets(),
                    trend.total()
            );
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private CachedTrend cachedTrend(AnalyticsQuery query, CustomUserDetails principal) {
        TrendKey key = TrendKey.of(principal, query);
        long now = System.currentTimeMillis();
        // Keys include viewer + every filter; evict expired entries so the cache cannot grow forever.
        if (trendCache.size() > 256) {
            trendCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
        CachedTrend existing = trendCache.get(key);
        if (existing != null && existing.expiresAt() > now) {
            return existing;
        }
        var response = analyticsService.getTaskCompletionTrends(query);
        CachedTrend fresh = new CachedTrend(
                List.copyOf(response.buckets()),
                response.totalCompleted(),
                now + settings.trendCacheTtlMs()
        );
        trendCache.put(key, fresh);
        return fresh;
    }

    /** Authorization dimensions plus filters - never shared across viewers. */
    private record TrendKey(
            Long viewerId,
            Long organizationId,
            List<Long> projectIds,
            List<Long> teamIds,
            List<Long> employeeIds,
            LocalDate from,
            LocalDate to,
            AnalyticsGranularity granularity
    ) {
        static TrendKey of(CustomUserDetails principal, AnalyticsQuery query) {
            return new TrendKey(
                    principal.getId(),
                    principal.getOrganizationId(),
                    List.copyOf(query.projectIds()),
                    List.copyOf(query.teamIds()),
                    List.copyOf(query.employeeIds()),
                    query.from(),
                    query.to(),
                    query.granularity()
            );
        }
    }

    private record CachedTrend(List<TimeBucket> buckets, MetricValue total, long expiresAt) {
    }
}
