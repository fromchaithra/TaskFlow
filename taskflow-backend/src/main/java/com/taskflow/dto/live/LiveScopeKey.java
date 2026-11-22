package com.taskflow.dto.live;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.AnalyticsScope;
import com.taskflow.security.CustomUserDetails;

import java.time.LocalDate;
import java.util.Set;

/**
 * All authorization dimensions and filters that must be equivalent before two
 * subscriptions may receive the same snapshot.
 */
public record LiveScopeKey(
        Long organizationId,
        String role,
        boolean admin,
        Set<Long> projectIds,
        Set<Long> teamIds,
        Set<Long> employeeIds,
        boolean userFiltered,
        LocalDate from,
        LocalDate to,
        AnalyticsGranularity granularity
) {

    public static LiveScopeKey of(AnalyticsScope scope) {
        return of(scope, null);
    }

    public static LiveScopeKey of(AnalyticsScope scope, CustomUserDetails principal) {
        AnalyticsQuery query = scope.query();
        boolean userFiltered = !scope.teamIds().isEmpty() || !scope.employeeIds().isEmpty();
        String role = principal == null || principal.getPrimaryRole() == null
                ? (scope.admin() ? "ADMIN" : "NON_ADMIN")
                : principal.getPrimaryRole().name();
        return new LiveScopeKey(
                scope.organizationId(),
                role,
                scope.admin(),
                Set.copyOf(scope.allowedProjectIds()),
                Set.copyOf(scope.teamIds()),
                Set.copyOf(scope.employeeIds()),
                userFiltered,
                query.from(),
                query.to(),
                query.granularity()
        );
    }
}
