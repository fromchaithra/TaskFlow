package com.taskflow.dto.analytics;

import com.taskflow.validation.ValidAnalyticsRange;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Common, validated filter contract shared by every analytics endpoint.
 *
 * <p>Cross-field rules (range order, maximum span, paired comparison range,
 * non-overlap) are enforced by {@link ValidAnalyticsRange}.</p>
 */
@ValidAnalyticsRange
public record AnalyticsQuery(

        @NotNull(message = "Range start date is required")
        LocalDate from,

        @NotNull(message = "Range end date is required")
        LocalDate to,

        LocalDate compareFrom,

        LocalDate compareTo,

        @NotNull(message = "Granularity is required")
        AnalyticsGranularity granularity,

        @Size(max = 50, message = "At most 50 projects can be selected")
        List<@Positive(message = "Project ids must be positive") Long> projectIds,

        @Size(max = 100, message = "At most 100 teams can be selected")
        List<@Positive(message = "Team ids must be positive") Long> teamIds,

        @Size(max = 100, message = "At most 100 employees can be selected")
        List<@Positive(message = "Employee ids must be positive") Long> employeeIds
) {

    public AnalyticsQuery {
        if (granularity == null) {
            granularity = AnalyticsGranularity.WEEK;
        }
        projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
        teamIds = teamIds == null ? List.of() : List.copyOf(teamIds);
        employeeIds = employeeIds == null ? List.of() : List.copyOf(employeeIds);
    }

    public boolean hasComparison() {
        return compareFrom != null && compareTo != null;
    }
}
