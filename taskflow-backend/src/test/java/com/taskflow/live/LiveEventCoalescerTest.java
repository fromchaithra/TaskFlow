package com.taskflow.live;

import com.taskflow.config.LiveAnalyticsSettings;
import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.live.LiveAnalyticsSnapshot;
import com.taskflow.dto.live.LiveScopeKey;
import com.taskflow.event.LiveChangeEvent;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.service.LiveAnalyticsSnapshotService;
import com.taskflow.service.LiveEventCoalescer;
import com.taskflow.service.LiveSubscriptionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiveEventCoalescerTest {

    @Mock private LiveSubscriptionRegistry registry;
    @Mock private LiveAnalyticsSnapshotService snapshotService;

    private LiveEventCoalescer coalescer;
    private AnalyticsQuery query;
    private CustomUserDetails principal;
    private LiveSubscriptionRegistry.Subscription subscription;
    private LiveScopeKey key;

    private static final LiveAnalyticsSettings SETTINGS =
            new LiveAnalyticsSettings(3, 200, 840_000L, 20_000L, 1_000L, 5, 600_000L, 60_000L, 10);

    @BeforeEach
    void setUp() {
        coalescer = new LiveEventCoalescer(registry, snapshotService, SETTINGS);
        query = new AnalyticsQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of());
        principal = mock(CustomUserDetails.class);
        key = LiveScopeKey.of(new com.taskflow.dto.analytics.AnalyticsScope(
                1L, 10L, false, Set.of(100L), Set.of(), Set.of(), query));
        subscription = mock(LiveSubscriptionRegistry.Subscription.class);
        when(subscription.isClosed()).thenReturn(false);
        when(subscription.scopeKey()).thenReturn(key);
        when(subscription.query()).thenReturn(query);
        when(subscription.principal()).thenReturn(principal);
        when(subscription.emitter()).thenReturn(new SseEmitter());
    }

    @Test
    void multipleEventsForSameScopeProduceOneSnapshot() {
        when(registry.all()).thenReturn(List.of(subscription));
        when(registry.allows(eq(subscription), any())).thenReturn(true);
        when(registry.forScope(key)).thenReturn(List.of(subscription));
        when(snapshotService.buildSnapshot(query, principal)).thenReturn(snapshot());

        for (int i = 0; i < 5; i++) {
            coalescer.enqueue(event(1L, 100L));
        }
        assertThat(coalescer.pendingCount()).isEqualTo(5);

        coalescer.processPending();

        verify(snapshotService, times(1)).buildSnapshot(query, principal);
        assertThat(coalescer.pendingCount()).isZero();
    }

    @Test
    void eventsForUnauthorizedScopesProduceNoSnapshot() {
        when(registry.all()).thenReturn(List.of(subscription));
        when(registry.allows(eq(subscription), any())).thenReturn(false);

        coalescer.enqueue(event(2L, 999L));
        coalescer.processPending();

        verify(snapshotService, never()).buildSnapshot(any(), any());
    }

    @Test
    void pendingSignalsAreBounded() {
        for (int i = 0; i < 20; i++) {
            coalescer.enqueue(event(1L, 100L));
        }
        assertThat(coalescer.pendingCount()).isEqualTo(SETTINGS.maxPendingSignals());
    }

    @Test
    void failedEmitterIsUnregistered() {
        SseEmitter broken = mock(SseEmitter.class);
        try {
            org.mockito.Mockito.doThrow(new java.io.IOException("broken"))
                    .when(broken).send(any(SseEmitter.SseEventBuilder.class));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        when(subscription.emitter()).thenReturn(broken);
        when(registry.all()).thenReturn(List.of(subscription));
        when(registry.allows(eq(subscription), any())).thenReturn(true);
        when(registry.forScope(key)).thenReturn(List.of(subscription));
        when(snapshotService.buildSnapshot(query, principal)).thenReturn(snapshot());

        coalescer.enqueue(event(1L, 100L));
        coalescer.processPending();

        verify(registry).unregister(subscription.id());
    }

    private static LiveChangeEvent event(Long orgId, Long projectId) {
        return new LiveChangeEvent(orgId, projectId, "TASK", 1L, "TASK_STATUS_CHANGED", Instant.now());
    }

    private static LiveAnalyticsSnapshot snapshot() {
        return new LiveAnalyticsSnapshot(Instant.now(), 1L, java.util.Map.of(),
                java.util.Map.of(), List.of(), List.of(), com.taskflow.dto.analytics.MetricValue.noData("count"));
    }
}
