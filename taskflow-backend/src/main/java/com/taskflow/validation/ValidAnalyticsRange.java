package com.taskflow.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Cross-field validation for analytics date ranges. */
@Documented
@Constraint(validatedBy = AnalyticsRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAnalyticsRange {

    String message() default "Invalid analytics range: 'from' must be on or before 'to', "
            + "ranges may not exceed 24 months, and comparison dates must be supplied "
            + "together, ordered and end before 'from'.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
