package com.taskflow.service.impl;

import com.taskflow.dto.analytics.AnalyticsMeta;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.AnalyticsScope;
import com.taskflow.dto.report.AnalyticsReportRequest;
import com.taskflow.dto.report.ReportMetadata;
import com.taskflow.dto.report.ReportType;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Sprint;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.AnalyticsAuthorizationService;
import com.taskflow.service.AnalyticsReportService;
import com.taskflow.service.AnalyticsService;
import com.taskflow.service.report.ReportModelFactory;
import com.taskflow.util.SecurityUtils;
import com.taskflow.util.report.ReportMetadataFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates report assembly without touching analytics calculations.
 *
 * <p>The existing {@link AnalyticsService} remains the only source of metrics;
 * this service resolves scope once for metadata, dispatches to the correct
 * analytics operation and converts the response into a renderer-neutral model.</p>
 */
@Service
public class AnalyticsReportServiceImpl implements AnalyticsReportService {

    private final Validator validator;
    private final AnalyticsService analyticsService;
    private final AnalyticsAuthorizationService authorizationService;
    private final OrganizationRepository organizationRepository;
    private final SprintRepository sprintRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;

    public AnalyticsReportServiceImpl(
            Validator validator,
            AnalyticsService analyticsService,
            AnalyticsAuthorizationService authorizationService,
            OrganizationRepository organizationRepository,
            SprintRepository sprintRepository,
            ProjectSecurityEvaluator projectSecurityEvaluator
    ) {
        this.validator = validator;
        this.analyticsService = analyticsService;
        this.authorizationService = authorizationService;
        this.organizationRepository = organizationRepository;
        this.sprintRepository = sprintRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsReportData buildReport(AnalyticsReportRequest request) {
        validate(request);
        AnalyticsQuery query = request.query();
        AnalyticsScope scope = authorizationService.resolveScope(query);

        if (request.type() == ReportType.SPRINT_BURNOWN) {
            validateSprint(request.sprintId(), scope);
        }

        AnalyticsPayload payload = dispatch(request.type(), query, request.sprintId());
        Organization organization = organizationRepository.findById(scope.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization not found: " + scope.organizationId()));
        ReportMetadata metadata = ReportMetadataFactory.create(
                organization, payload.meta(), query, request.type());
        return ReportModelFactory.create(metadata, request.type(), payload.response());
    }

    private void validate(AnalyticsReportRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Report request is required.");
        }
        Set<ConstraintViolation<AnalyticsReportRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> (v.getPropertyPath().toString().isEmpty() ? "request" : v.getPropertyPath().toString())
                            + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new BusinessRuleException("Invalid report request - " + details);
        }
    }

    private void validateSprint(Long sprintId, AnalyticsScope scope) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
        if (!sprint.getProject().getOrganization().getId().equals(scope.organizationId())) {
            throw new UnauthorizedResourceAccessException("You do not have access to this sprint.");
        }
        CustomUserDetails viewer = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(sprint.getProject().getId(), viewer);
        if (!scope.allowedProjectIds().contains(sprint.getProject().getId())) {
            throw new UnauthorizedResourceAccessException("This sprint is outside the selected project filters.");
        }
    }

    private AnalyticsPayload dispatch(ReportType type, AnalyticsQuery query, Long sprintId) {
        return switch (type) {
            case EXECUTIVE_OVERVIEW -> {
                var response = analyticsService.getExecutiveOverview(query);
                yield new AnalyticsPayload(response.meta(), response);
            }
            case PROJECT_PERFORMANCE -> {
                var response = analyticsService.getProjectPerformance(query);
                yield new AnalyticsPayload(response.meta(), response);
            }
            case PROJECT_GROWTH -> {
                var response = analyticsService.getProjectGrowth(query);
                yield new AnalyticsPayload(response.meta(), response);
            }
            case SPRINT_VELOCITY -> {
                var response = analyticsService.getSprintVelocity(query);
                yield new AnalyticsPayload(response.meta(), response);
            }
            case SPRINT_BURNOWN -> {
                var response = analyticsService.getSprintBurndown(query, sprintId);
                yield new AnalyticsPayload(response.meta(), response);
            }
            case TASK_COMPLETION_TRENDS -> {
                var response = analyticsService.getTaskCompletionTrends(query);
                yield new AnalyticsPayload(response.meta(), response);
            }
            case TEAM_PRODUCTIVITY -> {
                var response = analyticsService.getTeamProductivity(query);
                yield new AnalyticsPayload(response.meta(), response);
            }
            case EMPLOYEE_WORKLOAD -> {
                var response = analyticsService.getEmployeeWorkload(query);
                yield new AnalyticsPayload(response.meta(), response);
            }
            case BUG_TRENDS -> {
                var response = analyticsService.getBugTrends(query);
                yield new AnalyticsPayload(response.meta(), response);
            }
            case QA_ANALYTICS -> {
                var response = analyticsService.getQaResults(query);
                yield new AnalyticsPayload(response.meta(), response);
            }
        };
    }

    private record AnalyticsPayload(AnalyticsMeta meta, Object response) {
    }
}
