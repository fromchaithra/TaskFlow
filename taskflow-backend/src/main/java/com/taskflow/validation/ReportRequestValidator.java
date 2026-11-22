package com.taskflow.validation;

import com.taskflow.dto.report.AnalyticsReportRequest;
import com.taskflow.dto.report.ReportType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ReportRequestValidator implements ConstraintValidator<ValidReportRequest, AnalyticsReportRequest> {

    @Override
    public boolean isValid(AnalyticsReportRequest request, ConstraintValidatorContext context) {
        if (request == null || request.type() == null || request.format() == null) {
            return true; // @NotNull reports these fields.
        }
        if (request.type() == ReportType.SPRINT_BURNOWN) {
            return request.sprintId() != null && request.sprintId() > 0;
        }
        return true;
    }
}
