package com.taskflow.service.impl;

import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.AnalyticsScope;
import com.taskflow.entity.Project;
import com.taskflow.entity.Team;
import com.taskflow.entity.User;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.AnalyticsAuthorizationService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authorization helper for analytics requests.
 *
 * <p>Organization isolation is enforced first. Project visibility is derived
 * exclusively from {@link ProjectSecurityEvaluator} and the same repository
 * queries used by the existing dashboard, so analytics cannot broaden a
 * user's access. Explicit project, team and employee filters are validated
 * individually and rejected when they belong to another organization.</p>
 */
@Service
public class AnalyticsAuthorizationServiceImpl implements AnalyticsAuthorizationService {

    private final ProjectRepository projectRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public AnalyticsAuthorizationServiceImpl(
            ProjectRepository projectRepository,
            ProjectSecurityEvaluator projectSecurityEvaluator,
            TeamRepository teamRepository,
            UserRepository userRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsScope resolveScope(AnalyticsQuery query) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        Long organizationId = viewer.getOrganizationId();

        Set<Long> allowedProjectIds = resolveProjectIds(query.projectIds(), viewer, organizationId);
        Set<Long> teamIds = validateTeams(query.teamIds(), organizationId);
        Set<Long> employeeIds = validateEmployees(query.employeeIds(), organizationId);

        return new AnalyticsScope(
                organizationId,
                viewer.getId(),
                viewer.isAdmin(),
                allowedProjectIds,
                teamIds,
                employeeIds,
                query
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> resolveVisibleProjectIds() {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        return visibleProjectIds(viewer, viewer.getOrganizationId());
    }

    private Set<Long> resolveProjectIds(List<Long> requestedIds, CustomUserDetails viewer, Long organizationId) {
        if (!requestedIds.isEmpty()) {
            for (Long projectId : requestedIds) {
                projectSecurityEvaluator.assertCanViewProject(projectId, viewer);
            }
            return Set.copyOf(requestedIds);
        }
        return visibleProjectIds(viewer, organizationId);
    }

    private Set<Long> visibleProjectIds(CustomUserDetails viewer, Long organizationId) {
        if (viewer.isAdmin()) {
            return projectRepository.searchAllProjects(organizationId, null, null, Pageable.unpaged())
                    .stream()
                    .map(Project::getId)
                    .collect(Collectors.toSet());
        }
        return projectRepository.searchMemberProjects(organizationId, viewer.getId(), null, null, Pageable.unpaged())
                .stream()
                .map(Project::getId)
                .collect(Collectors.toSet());
    }

    private Set<Long> validateTeams(List<Long> teamIds, Long organizationId) {
        for (Long teamId : teamIds) {
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
            if (!team.getOrganization().getId().equals(organizationId)) {
                throw new UnauthorizedResourceAccessException("You do not have access to team " + teamId);
            }
        }
        return Set.copyOf(teamIds);
    }

    private Set<Long> validateEmployees(List<Long> employeeIds, Long organizationId) {
        for (Long employeeId : employeeIds) {
            User employee = userRepository.findById(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
            if (!employee.getOrganization().getId().equals(organizationId)) {
                throw new UnauthorizedResourceAccessException("You do not have access to employee " + employeeId);
            }
        }
        return Set.copyOf(employeeIds);
    }
}
