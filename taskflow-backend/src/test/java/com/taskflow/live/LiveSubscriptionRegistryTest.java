package com.taskflow.live;

import com.taskflow.config.LiveAnalyticsSettings;
import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.AnalyticsScope;
import com.taskflow.event.LiveChangeEvent;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.CustomUserDetailsService;
import com.taskflow.service.AnalyticsAuthorizationService;
import com.taskflow.service.LiveSubscriptionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiveSubscriptionRegistryTest {

    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private AnalyticsAuthorizationService authorizationService;
    @Mock private RevokedTokenRepository revokedTokenRepository;

    private LiveSubscriptionRegistry registry;
    private AnalyticsQuery query;

    private static final LiveAnalyticsSettings SETTINGS =
            new LiveAnalyticsSettings(1, 2, 840_000L, 20_000L, 1_000L, 5, 600_000L, 60_000L, 10);

    @BeforeEach
    void setUp() {
        registry = new LiveSubscriptionRegistry(SETTINGS, userDetailsService,
                authorizationService, revokedTokenRepository);
        query = new AnalyticsQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of());
    }

    private CustomUserDetails principal(Long id, Long orgId, boolean admin) {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getId()).thenReturn(id);
        when(principal.getOrganizationId()).thenReturn(orgId);
        when(principal.isAdmin()).thenReturn(admin);
        org.mockito.Mockito.doReturn(Set.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")))
                .when(principal).getAuthorities();
        return principal;
    }

    private LiveSubscriptionRegistry.Subscription subscribe(CustomUserDetails user, Set<Long> projects) {
        AnalyticsScope scope = new AnalyticsScope(user.getOrganizationId(), user.getId(),
                user.isAdmin(), projects, Set.of(), Set.of(), query);
        return registry.register(new SseEmitter(), user, query, scope, "jti-" + user.getId());
    }

    @Test
    void organizationAndProjectIsolationAreEnforcedOnDispatch() {
        var subscription = subscribe(principal(10L, 1L, false), Set.of(100L));

        assertThat(registry.allows(subscription, new LiveChangeEvent(1L, 100L, "TASK", 1L, "TASK_UPDATED", Instant.now()))).isTrue();
        assertThat(registry.allows(subscription, new LiveChangeEvent(2L, 100L, "TASK", 1L, "TASK_UPDATED", Instant.now()))).isFalse();
        assertThat(registry.allows(subscription, new LiveChangeEvent(1L, 200L, "TASK", 1L, "TASK_UPDATED", Instant.now()))).isFalse();
        // Organization-level events are admin-only.
        assertThat(registry.allows(subscription, new LiveChangeEvent(1L, null, "USER", 1L, "USER_UPDATED", Instant.now()))).isFalse();
    }

    @Test
    void adminSeesAllProjectsButNotOtherOrganizations() {
        var subscription = subscribe(principal(11L, 1L, true), Set.of(100L, 200L));

        assertThat(registry.allows(subscription, new LiveChangeEvent(1L, 200L, "TASK", 1L, "TASK_UPDATED", Instant.now()))).isTrue();
        assertThat(registry.allows(subscription, new LiveChangeEvent(1L, null, "USER", 1L, "USER_UPDATED", Instant.now()))).isTrue();
        assertThat(registry.allows(subscription, new LiveChangeEvent(2L, 200L, "TASK", 1L, "TASK_UPDATED", Instant.now()))).isFalse();
    }

    @Test
    void perUserAndGlobalLimitsAreEnforcedAndFreedOnUnregister() {
        CustomUserDetails user = principal(20L, 1L, false);
        subscribe(user, Set.of(100L));

        assertThatThrownBy(() -> subscribe(user, Set.of(100L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limit");

        var first = registry.all().getFirst();
        registry.unregister(first.id());
        assertThat(registry.size()).isZero();
        assertThat(subscribe(user, Set.of(100L))).isNotNull();
    }

    @Test
    void closeAllForUserRemovesOnlyThatUsersSubscriptions() {
        var one = subscribe(principal(30L, 1L, false), Set.of(100L));
        var two = subscribe(principal(31L, 1L, false), Set.of(100L));

        registry.closeAllForUser(30L);

        assertThat(one.isClosed()).isTrue();
        assertThat(two.isClosed()).isFalse();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void disabledUserIsDisconnectedOnRevalidation() {
        CustomUserDetails user = principal(40L, 1L, false);
        when(user.getOrganizationId()).thenReturn(1L);
        var subscription = subscribe(user, Set.of(100L));
        when(userDetailsService.loadUserById(40L)).thenReturn(user);
        when(user.isEnabled()).thenReturn(false);

        registry.revalidateAll();

        assertThat(subscription.isClosed()).isTrue();
        assertThat(registry.size()).isZero();
    }

    @Test
    void revokedTokenIsDisconnectedOnRevalidation() {
        CustomUserDetails user = principal(41L, 1L, false);
        when(user.isEnabled()).thenReturn(true);
        when(user.getOrganizationId()).thenReturn(1L);
        var subscription = subscribe(user, Set.of(100L));
        when(userDetailsService.loadUserById(41L)).thenReturn(user);
        when(revokedTokenRepository.existsByTokenJti("jti-41")).thenReturn(true);

        registry.revalidateAll();

        assertThat(subscription.isClosed()).isTrue();
        assertThat(registry.size()).isZero();
    }
}
