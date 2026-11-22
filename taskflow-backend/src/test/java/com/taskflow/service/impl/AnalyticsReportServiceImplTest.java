package com.taskflow.service.impl;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.AnalyticsMeta;
import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.AnalyticsScope;
import com.taskflow.dto.analytics.BugTrendResponse;
import com.taskflow.dto.analytics.EmployeeWorkloadResponse;
import com.taskflow.dto.analytics.ExecutiveOverviewResponse;
import com.taskflow.dto.analytics.MetricValue;
import com.taskflow.dto.analytics.ProjectGrowthResponse;
import com.taskflow.dto.analytics.ProjectPerformanceReportResponse;
import com.taskflow.dto.analytics.QaAnalyticsResponse;
import com.taskflow.dto.analytics.SprintBurndownResponse;
import com.taskflow.dto.analytics.SprintVelocityResponse;
import com.taskflow.dto.analytics.TaskCompletionTrendResponse;
import com.taskflow.dto.analytics.TeamProductivityResponse;
import com.taskflow.dto.report.AnalyticsReportRequest;
import com.taskflow.dto.report.ReportFormat;
import com.taskflow.dto.report.ReportType;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.Sprint;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.AnalyticsAuthorizationService;
import com.taskflow.service.AnalyticsService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsReportServiceImplTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock private AnalyticsService analyticsService;
    @Mock private AnalyticsAuthorizationService authorizationService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private ProjectSecurityEvaluator projectSecurityEvaluator;

    private AnalyticsReportServiceImpl service;
    private AnalyticsQuery query;
    private Organization organization;
    private AnalyticsScope scope;

    @BeforeEach
    void setUp() {
        service = new AnalyticsReportServiceImpl(VALIDATOR, analyticsService, authorizationService,
                organizationRepository, sprintRepository, projectSecurityEvaluator);
        query = new AnalyticsQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of());
        organization = new Organization("Org One", "org-one", "Software", "UTC", "MON-FRI");
        ReflectionTestUtils.setField(organization, "id", 1L);
        scope = new AnalyticsScope(1L, 10L, false, Set.of(1L), Set.of(), Set.of(), query);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubBasics() {
        when(authorizationService.resolveScope(same(query))).thenReturn(scope);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
    }

    private static AnalyticsReportRequest request(ReportType type, Long sprintId) {
        return new AnalyticsReportRequest(type, ReportFormat.PDF,
                new AnalyticsQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                        null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of()),
                sprintId);
    }

    // Uses the shared query instance so filter propagation can be verified.
    private AnalyticsReportRequest requestWithQuery(ReportType type) {
        return new AnalyticsReportRequest(type, ReportFormat.PDF, query, null);
    }

    private static AnalyticsMeta meta() {
        return AnalyticsMeta.of(query(), "service test");
    }

    private static AnalyticsQuery query() {
        return new AnalyticsQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.WEEK, List.of(), List.of(), List.of());
    }

    private Object stubAnalytics(ReportType type) {
        return switch (type) {
            case EXECUTIVE_OVERVIEW -> {
                var response = new ExecutiveOverviewResponse(meta(), Map.of(), List.of(), List.of());
                when(analyticsService.getExecutiveOverview(same(query))).thenReturn(response);
                yield response;
            }
            case PROJECT_PERFORMANCE -> {
                var summary = new ProjectPerformanceReportResponse.PortfolioSummary(
                        MetricValue.noData("count"), MetricValue.noData("%"),
                        MetricValue.noData("%"), MetricValue.noData("count"));
                var response = new ProjectPerformanceReportResponse(meta(), List.of(), summary);
                when(analyticsService.getProjectPerformance(same(query))).thenReturn(response);
                yield response;
            }
            case PROJECT_GROWTH -> {
                var response = new ProjectGrowthResponse(meta(), List.of(), MetricValue.noData("count"));
                when(analyticsService.getProjectGrowth(same(query))).thenReturn(response);
                yield response;
            }
            case SPRINT_VELOCITY -> {
                var response = new SprintVelocityResponse(meta(), List.of(), MetricValue.noData("count"));
                when(analyticsService.getSprintVelocity(same(query))).thenReturn(response);
                yield response;
            }
            case SPRINT_BURNOWN -> {
                var response = new SprintBurndownResponse(meta(), List.of(),
                        com.taskflow.dto.analytics.DataAvailability.INSUFFICIENT_HISTORY);
                when(analyticsService.getSprintBurndown(same(query), org.mockito.ArgumentMatchers.eq(7L)))
                        .thenReturn(response);
                yield response;
            }
            case TASK_COMPLETION_TRENDS -> {
                var response = new TaskCompletionTrendResponse(meta(), List.of(), MetricValue.noData("count"));
                when(analyticsService.getTaskCompletionTrends(same(query))).thenReturn(response);
                yield response;
            }
            case TEAM_PRODUCTIVITY -> {
                var response = new TeamProductivityResponse(meta(), List.of());
                when(analyticsService.getTeamProductivity(same(query))).thenReturn(response);
                yield response;
            }
            case EMPLOYEE_WORKLOAD -> {
                var response = new EmployeeWorkloadResponse(meta(), List.of(),
                        com.taskflow.dto.analytics.DataAvailability.NOT_CONFIGURED);
                when(analyticsService.getEmployeeWorkload(same(query))).thenReturn(response);
                yield response;
            }
            case BUG_TRENDS -> {
                var response = new BugTrendResponse(meta(), List.of(),
                        MetricValue.noData("%"), MetricValue.noData("days"));
                when(analyticsService.getBugTrends(same(query))).thenReturn(response);
                yield response;
            }
            case QA_ANALYTICS -> {
                var response = new QaAnalyticsResponse(meta(), Map.of(),
                        MetricValue.noData("%"), MetricValue.noData("%"), List.of());
                when(analyticsService.getQaResults(same(query))).thenReturn(response);
                yield response;
            }
        };
    }

    private void authenticate() {
        Role role = new Role(RoleName.PROJECT_MANAGER, "PM");
        User user = new User(organization, "EMP-10", "pm@org.test", "hash", "PM User", "Manager", role);
        ReflectionTestUtils.setField(user, "id", 10L);
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Sprint sprint(Long orgId, Long projectId) {
        Organization sprintOrg = new Organization("Sprint Org", "sprint-org", "IT", "UTC", "MON-FRI");
        ReflectionTestUtils.setField(sprintOrg, "id", orgId);
        User manager = new User(sprintOrg, "EMP-99", "m@sprint.test", "hash", "Manager", "PM",
                new Role(RoleName.PROJECT_MANAGER, "PM"));
        Project project = new Project(sprintOrg, "SPR", "Sprint Project", "desc", manager,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        ReflectionTestUtils.setField(project, "id", projectId);
        Sprint sprint = new Sprint(project, "Sprint 1", "Goal",
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 20), manager);
        ReflectionTestUtils.setField(sprint, "id", 7L);
        return sprint;
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void dispatchesEveryReportTypeToTheCorrectAnalyticsMethod(ReportType type) {
        stubBasics();
        stubAnalytics(type);
        AnalyticsReportRequest req = type == ReportType.SPRINT_BURNOWN
                ? new AnalyticsReportRequest(type, ReportFormat.PDF, query, 7L)
                : requestWithQuery(type);
        if (type == ReportType.SPRINT_BURNOWN) {
            when(sprintRepository.findById(7L)).thenReturn(Optional.of(sprint(1L, 1L)));
            authenticate();
        }

        AnalyticsReportData data = service.buildReport(req);

        assertThat(data.reportType()).isEqualTo(type);
        assertThat(data.metadata().reportTitle()).isEqualTo(type.label());
        switch (type) {
            case EXECUTIVE_OVERVIEW -> verify(analyticsService).getExecutiveOverview(same(query));
            case PROJECT_PERFORMANCE -> verify(analyticsService).getProjectPerformance(same(query));
            case PROJECT_GROWTH -> verify(analyticsService).getProjectGrowth(same(query));
            case SPRINT_VELOCITY -> verify(analyticsService).getSprintVelocity(same(query));
            case SPRINT_BURNOWN -> verify(analyticsService).getSprintBurndown(same(query),
                    org.mockito.ArgumentMatchers.eq(7L));
            case TASK_COMPLETION_TRENDS -> verify(analyticsService).getTaskCompletionTrends(same(query));
            case TEAM_PRODUCTIVITY -> verify(analyticsService).getTeamProductivity(same(query));
            case EMPLOYEE_WORKLOAD -> verify(analyticsService).getEmployeeWorkload(same(query));
            case BUG_TRENDS -> verify(analyticsService).getBugTrends(same(query));
            case QA_ANALYTICS -> verify(analyticsService).getQaResults(same(query));
        }
    }

    @Test
    void filtersAndComparisonArePreservedInMetadataAndMetrics() {
        AnalyticsQuery filtered = new AnalyticsQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31), AnalyticsGranularity.MONTH,
                List.of(1L), List.of(2L), List.of(3L));
        AnalyticsScope filteredScope = new AnalyticsScope(1L, 10L, false,
                Set.of(1L), Set.of(2L), Set.of(3L), filtered);
        when(authorizationService.resolveScope(same(filtered))).thenReturn(filteredScope);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        MetricValue compared = MetricValue.compared(BigDecimal.valueOf(4), BigDecimal.valueOf(2), "count");
        when(analyticsService.getExecutiveOverview(same(filtered))).thenReturn(
                new ExecutiveOverviewResponse(AnalyticsMeta.of(filtered, "basis"),
                        Map.of("tasksCompletedInRange", compared), List.of(), List.of()));

        AnalyticsReportData data = service.buildReport(new AnalyticsReportRequest(
                ReportType.EXECUTIVE_OVERVIEW, ReportFormat.XLSX, filtered, null));

        assertThat(data.metadata().appliedFilters()).contains(
                "Granularity: MONTH", "Projects: 1 selected", "Teams: 1 selected",
                "Employees: 1 selected", "Comparison: 2025-10-01 to 2025-12-31");
        assertThat(data.metadata().compared()).isTrue();
        assertThat(data.summaryKpis().getFirst().metric().previousValue()).isEqualByComparingTo("2");
        verify(analyticsService).getExecutiveOverview(same(filtered));
    }

    @Test
    void emptyAndUnavailableAnalyticsProduceEmptyReportWithNotes() {
        stubBasics();
        stubAnalytics(ReportType.EXECUTIVE_OVERVIEW);

        AnalyticsReportData data = service.buildReport(requestWithQuery(ReportType.EXECUTIVE_OVERVIEW));

        assertThat(data.emptyDataset()).isTrue();
        assertThat(data.availabilityNotes()).contains(
                "No data in range means no qualifying records were found for this metric during the selected reporting period.",
                "Project status: No data in range",
                "Task priority distribution: No data in range",
                "Task creation trend: No data in range",
                "Employee completion metrics: No data in range",
                "Audit-log activity: No data in range",
                "Bug severity: No data in range");
        assertThat(data.sections()).hasSize(7);
    }

    @Test
    void unavailableMetricIsCarriedThrough() {
        stubBasics();
        MetricValue noData = MetricValue.noData("count");
        when(analyticsService.getExecutiveOverview(same(query))).thenReturn(
                new ExecutiveOverviewResponse(meta(), Map.of("openBugs", noData), List.of(), List.of()));

        AnalyticsReportData data = service.buildReport(requestWithQuery(ReportType.EXECUTIVE_OVERVIEW));

        assertThat(data.summaryKpis().getFirst().metric()).isSameAs(noData);
        assertThat(data.availabilityNotes()).contains("No data in range");
    }

    @Test
    void missingOrganizationThrows() {
        when(authorizationService.resolveScope(same(query))).thenReturn(scope);
        when(organizationRepository.findById(1L)).thenReturn(Optional.empty());
        stubAnalytics(ReportType.PROJECT_GROWTH);

        assertThatThrownBy(() -> service.buildReport(requestWithQuery(ReportType.PROJECT_GROWTH)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void invalidRequestIsRejectedBeforeAuthorization() {
        assertThatThrownBy(() -> service.buildReport(
                new AnalyticsReportRequest(ReportType.EXECUTIVE_OVERVIEW, ReportFormat.PDF, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(analyticsService, never()).getExecutiveOverview(org.mockito.ArgumentMatchers.any());
        verify(authorizationService, never()).resolveScope(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authorizationDenialIsPropagated() {
        when(authorizationService.resolveScope(same(query)))
                .thenThrow(new UnauthorizedResourceAccessException("denied"));

        assertThatThrownBy(() -> service.buildReport(requestWithQuery(ReportType.BUG_TRENDS)))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void sprintFromAnotherOrganizationIsRejected() {
        when(authorizationService.resolveScope(same(query))).thenReturn(scope);
        when(sprintRepository.findById(7L)).thenReturn(Optional.of(sprint(2L, 1L)));
        authenticate();

        assertThatThrownBy(() -> service.buildReport(new AnalyticsReportRequest(
                ReportType.SPRINT_BURNOWN, ReportFormat.PDF, query, 7L)))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
        verify(analyticsService, never()).getSprintBurndown(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sprintOutsideProjectFiltersIsRejected() {
        AnalyticsScope narrow = new AnalyticsScope(1L, 10L, false,
                Set.of(99L), Set.of(), Set.of(), query);
        when(authorizationService.resolveScope(same(query))).thenReturn(narrow);
        when(sprintRepository.findById(7L)).thenReturn(Optional.of(sprint(1L, 1L)));
        authenticate();

        assertThatThrownBy(() -> service.buildReport(new AnalyticsReportRequest(
                ReportType.SPRINT_BURNOWN, ReportFormat.PDF, query, 7L)))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void sprintProjectVisibilityIsCheckedWithExistingEvaluator() {
        when(authorizationService.resolveScope(same(query))).thenReturn(scope);
        when(sprintRepository.findById(7L)).thenReturn(Optional.of(sprint(1L, 1L)));
        authenticate();
        stubAnalytics(ReportType.SPRINT_BURNOWN);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));

        service.buildReport(new AnalyticsReportRequest(
                ReportType.SPRINT_BURNOWN, ReportFormat.PDF, query, 7L));

        verify(projectSecurityEvaluator).assertCanViewProject(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(CustomUserDetails.class));
    }
}
