package com.taskflow.validation;

import com.taskflow.dto.analytics.AnalyticsQuery;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.temporal.ChronoUnit;

public class AnalyticsRangeValidator implements ConstraintValidator<ValidAnalyticsRange, AnalyticsQuery> {

    private static final long MAX_RANGE_MONTHS = 24;

    @Override
    public boolean isValid(AnalyticsQuery query, ConstraintValidatorContext context) {
        if (query == null || query.from() == null || query.to() == null) {
            return true; // @NotNull reports the missing field.
        }
        if (query.from().isAfter(query.to())) {
            return false;
        }
        if (ChronoUnit.MONTHS.between(query.from(), query.to()) > MAX_RANGE_MONTHS) {
            return false;
        }

        boolean hasCompareFrom = query.compareFrom() != null;
        boolean hasCompareTo = query.compareTo() != null;
        if (hasCompareFrom != hasCompareTo) {
            return false;
        }
        if (!hasCompareFrom) {
            return true;
        }
        if (query.compareFrom().isAfter(query.compareTo())) {
            return false;
        }
        if (ChronoUnit.MONTHS.between(query.compareFrom(), query.compareTo()) > MAX_RANGE_MONTHS) {
            return false;
        }
        // The comparison period must be strictly before the reporting period.
        return query.compareTo().isBefore(query.from());
    }
}
