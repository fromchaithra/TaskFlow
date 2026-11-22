package com.taskflow.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Cross-field validation for report requests. */
@Documented
@Constraint(validatedBy = ReportRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidReportRequest {

    String message() default "Sprint burndown reports require a positive sprintId.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
