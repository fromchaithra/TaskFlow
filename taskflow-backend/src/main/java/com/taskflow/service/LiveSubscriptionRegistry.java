package com.taskflow.service;

import com.taskflow.config.LiveAnalyticsSettings;
import com.taskflow.dto.analytics.AnalyticsScope;
import com.taskflow.dto.live.LiveScopeKey;
import com.taskflow.event.LiveChangeEvent;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded registry of live SSE subscriptions.
 *
 * <p>No database transaction or connection is held for the lifetime of a
 * subscription. Authorization is resolved once at connect, re-resolved on
 * every reconnect, and revalidated periodically; failed, timed-out and
 * no-longer-authorized subscriptions are closed and removed.</p>
 */
@Component
public class LiveSubscriptionRegistry {

    private static final Logger log = LoggerFactory.getLogger(LiveSubscriptionRegistry.class);

    private final LiveAnalyticsSettings settings;
    private final CustomUserDetailsService userDetailsService;
    private final AnalyticsAuthorizationService authorizationService;
    private final RevokedTokenRepository revokedTokenRepository;

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public LiveSubscriptionRegistry(LiveAnalyticsSettings settings,
                                     CustomUserDetailsService userDetailsService,
                                     AnalyticsAuthorizationService authorizationService,
                                     RevokedTokenRepository revokedTokenRepository) {
        this.settings = settings;
        this.userDetailsService = userDetailsService;
        this.authorizationService = authorizationService;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public synchronized Subscription register(SseEmitter emitter, CustomUserDetails principal,
                                               com.taskflow.dto.analytics.AnalyticsQuery query,
                                               AnalyticsScope scope, String jti) {
        long userCount = subscriptions.values().stream()
                .filter(s -> s.userId().equals(principal.getId())).count();
        if (userCount >= settings.maxConnectionsPerUser()) {
            throw new BusinessRuleException("Live connection limit reached for this user.");
        }
        if (subscriptions.size() >= settings.maxConnectionsGlobal()) {
            throw new BusinessRuleException("Live connection limit reached; please try again later.");
        }
        Subscription subscription = new Subscription(
                UUID.randomUUID().toString(), emitter, principal, principal.getId(), query, scope,
                LiveScopeKey.of(scope, principal), jti);
        subscriptions.put(subscription.id(), subscription);
        log.debug("Registered live subscription {} for org {}", subscription.id(), scope.organizationId());
        return subscription;
    }

    public void unregister(String id) {
        Subscription removed = subscriptions.remove(id);
        if (removed != null) {
            removed.close();
        }
    }

    public List<Subscription> all() {
        return List.copyOf(subscriptions.values());
    }

    public List<Subscription> forScope(LiveScopeKey key) {
        List<Subscription> result = new ArrayList<>();
        for (Subscription subscription : subscriptions.values()) {
            if (subscription.scopeKey().equals(key)) {
                result.add(subscription);
            }
        }
        return List.copyOf(result);
    }

    public int size() {
        return subscriptions.size();
    }

    public long countForUser(Long userId) {
        return subscriptions.values().stream().filter(s -> s.userId().equals(userId)).count();
    }

    public void closeAllForUser(Long userId) {
        for (Subscription subscription : new ArrayList<>(subscriptions.values())) {
            if (subscription.userId().equals(userId)) {
                unregister(subscription.id());
            }
        }
    }

    public void closeAll() {
        for (String id : new ArrayList<>(subscriptions.keySet())) {
            unregister(id);
        }
    }

    /** True when this subscription is allowed to observe the committed change. */
    public boolean allows(Subscription subscription, LiveChangeEvent event) {
        if (!subscription.organizationId().equals(event.organizationId())) {
            return false;
        }
        if (event.projectId() == null) {
            // Organization-level events are visible only to admins.
            return subscription.admin();
        }
        if (!subscription.admin() && !subscription.projectIds().contains(event.projectId())) {
            return false;
        }
        return true;
    }

    @Scheduled(fixedDelayString = "${taskflow.live.heartbeat-ms:20000}")
    public void sendHeartbeats() {
        for (Subscription subscription : all()) {
            try {
                subscription.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | RuntimeException ex) {
                unregister(subscription.id());
            }
        }
    }

    /**
     * Re-resolves authorization for every live connection. Users that were
     * disabled, deleted, had their token revoked, or lost project visibility
     * are disconnected within one revalidation interval.
     */
    @Scheduled(fixedDelayString = "${taskflow.live.revalidate-ms:600000}")
    public void revalidateAll() {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            for (Subscription subscription : all()) {
                try {
                    CustomUserDetails fresh = userDetailsService.loadUserById(subscription.userId());
                    boolean revoked = subscription.jti() != null
                            && revokedTokenRepository.existsByTokenJti(subscription.jti());
                    if (!fresh.isEnabled() || revoked
                            || !fresh.getOrganizationId().equals(subscription.organizationId())) {
                        unregister(subscription.id());
                        continue;
                    }
                    SecurityContextHolder.setContext(new SecurityContextImpl(
                            new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities())));
                    AnalyticsScope scope = authorizationService.resolveScope(subscription.query());
                    if (!LiveScopeKey.of(scope).equals(subscription.scopeKey())) {
                        unregister(subscription.id());
                        continue;
                    }
                    subscription.refresh(fresh, scope);
                } catch (RuntimeException ex) {
                    log.debug("Live subscription {} failed revalidation: {}", subscription.id(), ex.getMessage());
                    unregister(subscription.id());
                }
            }
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    /** Mutable state is limited to revalidation refresh; identity fields are final. */
    public static final class Subscription {
        private final String id;
        private final SseEmitter emitter;
        private final Long userId;
        private final com.taskflow.dto.analytics.AnalyticsQuery query;
        private final String jti;
        private final AtomicBoolean closed = new AtomicBoolean();

        private volatile CustomUserDetails principal;
        private volatile AnalyticsScope scope;
        private volatile LiveScopeKey scopeKey;

        private Subscription(String id, SseEmitter emitter, CustomUserDetails principal, Long userId,
                              com.taskflow.dto.analytics.AnalyticsQuery query, AnalyticsScope scope,
                              LiveScopeKey scopeKey, String jti) {
            this.id = id;
            this.emitter = emitter;
            this.principal = principal;
            this.userId = userId;
            this.query = query;
            this.scope = scope;
            this.scopeKey = scopeKey;
            this.jti = jti;
            emitter.onCompletion(this::close);
            emitter.onTimeout(this::close);
            emitter.onError(ex -> close());
        }

        synchronized void refresh(CustomUserDetails principal, AnalyticsScope scope) {
            this.principal = principal;
            this.scope = scope;
            this.scopeKey = LiveScopeKey.of(scope, principal);
        }

        void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                    // Already completed or disconnected by the container.
                }
            }
        }

        public String id() { return id; }
        public SseEmitter emitter() { return emitter; }
        public CustomUserDetails principal() { return principal; }
        public Long userId() { return userId; }
        public com.taskflow.dto.analytics.AnalyticsQuery query() { return query; }
        public AnalyticsScope scope() { return scope; }
        public LiveScopeKey scopeKey() { return scopeKey; }
        public String jti() { return jti; }
        public Long organizationId() { return scope.organizationId(); }
        public boolean admin() { return scope.admin(); }
        public java.util.Set<Long> projectIds() { return scope.allowedProjectIds(); }
        public boolean isClosed() { return closed.get(); }
    }
}
