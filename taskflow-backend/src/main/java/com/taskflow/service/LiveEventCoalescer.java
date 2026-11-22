package com.taskflow.service;

import com.taskflow.config.LiveAnalyticsSettings;
import com.taskflow.dto.live.LiveAnalyticsSnapshot;
import com.taskflow.dto.live.LiveScopeKey;
import com.taskflow.event.LiveChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounds and coalesces committed change signals before any client work occurs.
 *
 * <p>Many events for the same authorization/filter signature collapse into one
 * snapshot computation, which is then pushed to every subscriber of that
 * signature. This avoids query-per-event-per-user behaviour.</p>
 */
@Component
public class LiveEventCoalescer {

    private static final Logger log = LoggerFactory.getLogger(LiveEventCoalescer.class);

    private final LiveSubscriptionRegistry registry;
    private final LiveAnalyticsSnapshotService snapshotService;
    private final BlockingQueue<LiveChangeEvent> pending;
    private final Set<LiveScopeKey> dirtyKeys = ConcurrentHashMap.newKeySet();

    public LiveEventCoalescer(LiveSubscriptionRegistry registry,
                              LiveAnalyticsSnapshotService snapshotService,
                              LiveAnalyticsSettings settings) {
        this.registry = registry;
        this.snapshotService = snapshotService;
        this.pending = new ArrayBlockingQueue<>(settings.maxPendingSignals());
    }

    public void enqueue(LiveChangeEvent event) {
        if (!pending.offer(event)) {
            pending.poll();
            pending.offer(event);
        }
    }

    public int pendingCount() {
        return pending.size();
    }

    @Scheduled(fixedDelayString = "${taskflow.live.coalesce-ms:1000}")
    public void processPending() {
        List<LiveChangeEvent> batch = new ArrayList<>();
        pending.drainTo(batch);
        if (batch.isEmpty()) {
            return;
        }
        markAffectedScopes(batch);
        for (LiveScopeKey key : new ArrayList<>(dirtyKeys)) {
            dirtyKeys.remove(key);
            publishSnapshot(key);
        }
    }

    /** Periodically refreshes the cached historical trend for active scopes. */
    @Scheduled(fixedDelayString = "${taskflow.live.trend-refresh-ms:60000}")
    public void refreshPeriodicSnapshots() {
        for (LiveScopeKey key : registry.all().stream()
                .map(LiveSubscriptionRegistry.Subscription::scopeKey)
                .distinct().toList()) {
            publishSnapshot(key);
        }
    }

    private void markAffectedScopes(List<LiveChangeEvent> batch) {
        for (var subscription : registry.all()) {
            if (subscription.isClosed()) {
                continue;
            }
            for (LiveChangeEvent event : batch) {
                if (registry.allows(subscription, event)) {
                    dirtyKeys.add(subscription.scopeKey());
                    break;
                }
            }
        }
    }

    private void publishSnapshot(LiveScopeKey key) {
        List<LiveSubscriptionRegistry.Subscription> audience = registry.forScope(key);
        if (audience.isEmpty()) {
            return;
        }
        LiveSubscriptionRegistry.Subscription representative = audience.getFirst();
        LiveAnalyticsSnapshot snapshot;
        try {
            snapshot = snapshotService.buildSnapshot(representative.query(), representative.principal());
        } catch (RuntimeException ex) {
            log.warn("Live snapshot refresh failed for org {}: {}", key.organizationId(), ex.getMessage());
            return;
        }
        for (var subscription : audience) {
            try {
                subscription.emitter().send(SseEmitter.event().name("snapshot").data(snapshot));
            } catch (IOException | RuntimeException ex) {
                registry.unregister(subscription.id());
            }
        }
    }
}
