package com.taskflow.controller;

import com.taskflow.config.LiveAnalyticsSettings;
import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.AnalyticsScope;
import com.taskflow.dto.live.LiveAnalyticsSnapshot;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.service.AnalyticsAuthorizationService;
import com.taskflow.service.LiveAnalyticsSnapshotService;
import com.taskflow.service.LiveSubscriptionRegistry;
import com.taskflow.util.SecurityUtils;
import io.jsonwebtoken.Claims;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Secure, bounded live analytics endpoints. Clients are never sent an internal
 * domain event; only the approved {@link LiveAnalyticsSnapshot} payload.
 */
@RestController
@RequestMapping("/api/v1/live")
public class LiveAnalyticsController {

    private final LiveSubscriptionRegistry registry;
    private final LiveAnalyticsSnapshotService snapshotService;
    private final AnalyticsAuthorizationService authorizationService;
    private final LiveAnalyticsSettings settings;
    private final Validator validator;
    private final JwtService jwtService;

    public LiveAnalyticsController(LiveSubscriptionRegistry registry,
                                   LiveAnalyticsSnapshotService snapshotService,
                                   AnalyticsAuthorizationService authorizationService,
                                   LiveAnalyticsSettings settings,
                                   Validator validator,
                                   JwtService jwtService) {
        this.registry = registry;
        this.snapshotService = snapshotService;
        this.authorizationService = authorizationService;
        this.settings = settings;
        this.validator = validator;
        this.jwtService = jwtService;
    }

    @GetMapping(value = "/analytics", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) List<String> projectIds,
            @RequestParam(required = false) List<String> teamIds,
            @RequestParam(required = false) List<String> employeeIds,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        AnalyticsQuery query = query(from, to, granularity, projectIds, teamIds, employeeIds);
        CustomUserDetails principal = SecurityUtils.currentUser();
        // Re-resolve authorization on every connection. Client values cannot
        // widen scope: resolveScope validates each filter against the viewer.
        AnalyticsScope scope = authorizationService.resolveScope(query);

        SseEmitter emitter = new SseEmitter(settings.emitterTimeoutMs());
        var subscription = registry.register(emitter, principal, query, scope, jti(authorization));
        try {
            emitter.send(SseEmitter.event()
                    .reconnectTime(3_000L)
                    .comment("connected"));
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(snapshotService.buildSnapshot(query, principal)));
        } catch (IOException | RuntimeException ex) {
            registry.unregister(subscription.id());
            throw new BusinessRuleException("Unable to start live analytics stream.");
        }
        return emitter;
    }

    @GetMapping(value = "/analytics/snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public LiveAnalyticsSnapshot snapshot(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) List<String> projectIds,
            @RequestParam(required = false) List<String> teamIds,
            @RequestParam(required = false) List<String> employeeIds
    ) {
        AnalyticsQuery query = query(from, to, granularity, projectIds, teamIds, employeeIds);
        // Enforce the same scope resolution as the stream even though this
        // endpoint runs on a normal request thread.
        authorizationService.resolveScope(query);
        return snapshotService.buildSnapshot(query, SecurityUtils.currentUser());
    }

    private AnalyticsQuery query(String from, String to, String granularity,
                                  List<String> projectIds, List<String> teamIds, List<String> employeeIds) {
        try {
            LocalDate fromDate = from == null || from.isBlank()
                    ? LocalDate.now().minusDays(30) : LocalDate.parse(from);
            LocalDate toDate = to == null || to.isBlank()
                    ? LocalDate.now() : LocalDate.parse(to);
            AnalyticsGranularity gran = granularity == null || granularity.isBlank()
                    ? AnalyticsGranularity.WEEK
                    : AnalyticsGranularity.valueOf(granularity.trim().toUpperCase());
            AnalyticsQuery candidate = new AnalyticsQuery(
                    fromDate, toDate, null, null, gran,
                    ids(projectIds), ids(teamIds), ids(employeeIds));
            Set<ConstraintViolation<AnalyticsQuery>> violations = validator.validate(candidate);
            if (!violations.isEmpty()) {
                String detail = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining("; "));
                throw new BusinessRuleException("Invalid live analytics query - " + detail);
            }
            return candidate;
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid live analytics query parameters.");
        }
    }

    private static List<Long> ids(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String value : values) {
            try {
                long parsed = Long.parseLong(value);
                if (parsed > 0) {
                    result.add(parsed);
                }
            } catch (NumberFormatException ignored) {
                // Invalid ids are rejected by validation through scope resolution.
            }
        }
        return result;
    }

    private String jti(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            Claims claims = jwtService.parseAndValidateToken(authorization.substring(7).trim());
            return claims.getId();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
