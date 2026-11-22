package com.taskflow.service.impl;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.project.AddProjectMemberRequest;
import com.taskflow.dto.project.CreateProjectRequest;
import com.taskflow.dto.project.ProjectDetailResponse;
import com.taskflow.dto.project.ProjectMemberResponse;
import com.taskflow.dto.project.ProjectSummaryResponse;
import com.taskflow.dto.project.UpdateProjectMemberRoleRequest;
import com.taskflow.dto.project.UpdateProjectRequest;
import com.taskflow.dto.project.UpdateProjectStatusRequest;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Team;
import com.taskflow.entity.TeamMember;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.SprintStatus;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.InvalidWorkflowTransitionException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.ProjectService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Set<TaskStatus> ACTIVE_ASSIGNMENT_TASK_STATUSES =
            EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW);

    private static final Set<BugStatus> ACTIVE_ASSIGNMENT_BUG_STATUSES =
            EnumSet.of(BugStatus.ASSIGNED, BugStatus.IN_PROGRESS);

    private static final Set<BugStatus> OPEN_BUG_STATUSES =
            EnumSet.of(BugStatus.OPEN, BugStatus.ASSIGNED, BugStatus.IN_PROGRESS, BugStatus.REOPENED);

    private static final Map<ProjectStatus, Set<ProjectStatus>> ALLOWED_STATUS_TRANSITIONS = buildStatusTransitions();

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final BugRepository bugRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;
    private final AuditLogWriter auditLogWriter;

    public ProjectServiceImpl(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            SprintRepository sprintRepository,
            TaskRepository taskRepository,
            BugRepository bugRepository,
            ProjectSecurityEvaluator projectSecurityEvaluator,
            AuditLogWriter auditLogWriter
    ) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.bugRepository = bugRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    @Transactional
    public ProjectDetailResponse createProject(CreateProjectRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        if (!actorDetails.isAdmin() && actorDetails.getPrimaryRole() != RoleName.PROJECT_MANAGER) {
            throw new UnauthorizedResourceAccessException("Only admins and project managers can create projects.");
        }

        Long orgId = actorDetails.getOrganizationId();
        String normalizedKey = request.projectKey().trim().toUpperCase(Locale.ROOT);
        String normalizedName = request.name().trim();

        if (projectRepository.existsByOrganizationIdAndProjectKeyIgnoreCase(orgId, normalizedKey)) {
            throw new DuplicateResourceException(
                    "A project with key '" + normalizedKey + "' already exists in this organization."
            );
        }

        validateDateRange(request.startDate(), request.endDate());

        ProjectStatus initialStatus = request.initialStatus() != null
                ? request.initialStatus()
                : ProjectStatus.ACTIVE;
        if (initialStatus != ProjectStatus.PLANNING && initialStatus != ProjectStatus.ACTIVE) {
            throw new BusinessRuleException("New projects must start in PLANNING or ACTIVE status.");
        }

        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found."));

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        User manager = resolveProjectManagerForCreate(request.managerId(), actorDetails, actor, orgId);
        List<User> teamMembers = resolveInitialTeamMembers(request.teamId(), orgId);
        List<User> explicitMembers = resolveExplicitInitialMembers(request.memberIds(), orgId);

        Project project = new Project(
                organization,
                normalizedKey,
                normalizedName,
                normalizeOptionalText(request.description()),
                manager,
                request.startDate(),
                request.endDate()
        );
        project.setStatus(initialStatus);
        Project saved = projectRepository.save(project);

        Set<Long> addedUserIds = new LinkedHashSet<>();
        projectMemberRepository.save(new ProjectMember(saved, manager, RoleName.PROJECT_MANAGER, actor));
        addedUserIds.add(manager.getId());

        for (User member : teamMembers) {
            if (addedUserIds.add(member.getId())) {
                projectMemberRepository.save(new ProjectMember(saved, member, member.getPrimaryRoleName(), actor));
            }
        }
        for (User member : explicitMembers) {
            if (addedUserIds.add(member.getId())) {
                projectMemberRepository.save(new ProjectMember(saved, member, member.getPrimaryRoleName(), actor));
            }
        }

        auditLogWriter.record(
                organization,
                actor,
                saved,
                AuditAction.PROJECT_CREATED,
                "PROJECT",
                saved.getId(),
                null,
                buildProjectSnapshot(saved, addedUserIds.size())
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProjectSummaryResponse> listProjects(ProjectStatus status, String search, Pageable pageable) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        String normalizedSearch = normalizeSearch(search);

        Page<Project> projectPage = viewer.isAdmin()
                ? projectRepository.searchAllProjects(viewer.getOrganizationId(), status, normalizedSearch, pageable)
                : projectRepository.searchMemberProjects(
                        viewer.getOrganizationId(),
                        viewer.getId(),
                        status,
                        normalizedSearch,
                        pageable
                );

        Map<Long, Long> memberCounts = loadMemberCounts(projectPage.getContent());
        Page<ProjectSummaryResponse> dtoPage = projectPage.map(
                project -> ProjectSummaryResponse.from(project, memberCounts.getOrDefault(project.getId(), 0L))
        );
        return PagedResponse.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResponse getProjectById(Long projectId) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(projectId, viewer);

        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        return toDetailResponse(project);
    }

    @Override
    @Transactional
    public ProjectDetailResponse updateProject(Long projectId, UpdateProjectRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("Archived projects cannot be modified. Reactivate the project first.");
        }

        validateDateRange(request.startDate(), request.endDate());

        long currentMemberCount = projectMemberRepository.countByProjectId(projectId);
        Map<String, Object> previous = buildProjectSnapshot(project, currentMemberCount);

        User actor = userRepository.getReferenceById(actorDetails.getId());

        if (request.managerId() != null && !request.managerId().equals(project.getManager().getId())) {
            if (!actorDetails.isAdmin()) {
                throw new UnauthorizedResourceAccessException("Only an organization admin can reassign a project manager.");
            }
            User nextManager = resolveEligibleManager(request.managerId(), actorDetails.getOrganizationId());
            project.setManager(nextManager);
            ensureManagerMembership(project, nextManager, actor);
        }

        project.setName(request.name().trim());
        project.setDescription(normalizeOptionalText(request.description()));
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());

        Project saved = projectRepository.save(project);
        long updatedMemberCount = projectMemberRepository.countByProjectId(saved.getId());

        auditLogWriter.record(
                saved.getOrganization(),
                actor,
                saved,
                AuditAction.PROJECT_UPDATED,
                "PROJECT",
                saved.getId(),
                previous,
                buildProjectSnapshot(saved, updatedMemberCount)
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public ProjectDetailResponse updateProjectStatus(Long projectId, UpdateProjectStatusRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        ProjectStatus previousStatus = project.getStatus();
        ProjectStatus nextStatus = request.status();

        if (previousStatus == nextStatus) {
            return toDetailResponse(project);
        }

        validateStatusTransition(previousStatus, nextStatus, actorDetails);

        if (nextStatus == ProjectStatus.ARCHIVED
                && sprintRepository.existsByProjectIdAndStatus(projectId, SprintStatus.ACTIVE)) {
            throw new BusinessRuleException("Cannot archive a project while an active sprint is still running.");
        }

        project.setStatus(nextStatus);
        Project saved = projectRepository.save(project);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        AuditAction action = nextStatus == ProjectStatus.ARCHIVED
                ? AuditAction.PROJECT_ARCHIVED
                : AuditAction.PROJECT_STATUS_CHANGED;

        auditLogWriter.record(
                saved.getOrganization(),
                actor,
                saved,
                action,
                "PROJECT",
                saved.getId(),
                Map.of("status", previousStatus.name(), "projectKey", saved.getProjectKey()),
                Map.of("status", nextStatus.name(), "projectKey", saved.getProjectKey())
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public ProjectSummaryResponse archiveProject(Long projectId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        long memberCount = projectMemberRepository.countByProjectId(projectId);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            return ProjectSummaryResponse.from(project, memberCount);
        }

        if (sprintRepository.existsByProjectIdAndStatus(projectId, SprintStatus.ACTIVE)) {
            throw new BusinessRuleException("Cannot archive a project while an active sprint is still running.");
        }

        ProjectStatus previousStatus = project.getStatus();
        project.setStatus(ProjectStatus.ARCHIVED);
        Project saved = projectRepository.save(project);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                saved.getOrganization(),
                actor,
                saved,
                AuditAction.PROJECT_ARCHIVED,
                "PROJECT",
                saved.getId(),
                Map.of("status", previousStatus.name(), "projectKey", saved.getProjectKey()),
                Map.of("status", ProjectStatus.ARCHIVED.name(), "projectKey", saved.getProjectKey())
        );

        return ProjectSummaryResponse.from(saved, memberCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listProjectMembers(Long projectId) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(projectId, viewer);

        return projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(projectId)
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ProjectMemberResponse addProjectMember(Long projectId, AddProjectMemberRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot add members to an archived project.");
        }

        User member = resolveActiveOrgEmployee(request.userId(), actorDetails.getOrganizationId(), "Project member");
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, member.getId())) {
            throw new DuplicateResourceException(
                    "Employee " + member.getEmail() + " is already a member of project " + project.getProjectKey() + "."
            );
        }

        RoleName assignedRole = request.projectRole() != null
                ? request.projectRole()
                : member.getPrimaryRoleName();

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        ProjectMember saved = projectMemberRepository.save(new ProjectMember(project, member, assignedRole, actor));

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.PROJECT_MEMBER_ADDED,
                "PROJECT_MEMBER",
                saved.getId(),
                null,
                Map.of(
                        "projectKey", project.getProjectKey(),
                        "userId", member.getId(),
                        "email", member.getEmail(),
                        "projectRole", assignedRole.name()
                )
        );

        return ProjectMemberResponse.from(saved);
    }

    @Override
    @Transactional
    public void removeProjectMember(Long projectId, Long userId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot remove members from an archived project.");
        }

        if (project.getManager().getId().equals(userId)) {
            throw new BusinessRuleException("Cannot remove the assigned project manager. Reassign the project manager first.");
        }

        ProjectMember membership = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project member not found for user: " + userId));

        if (taskRepository.existsByProjectIdAndAssigneeIdAndStatusIn(projectId, userId, ACTIVE_ASSIGNMENT_TASK_STATUSES)) {
            throw new BusinessRuleException(
                    "Cannot remove member while they have tasks in progress or in review. Reassign active tasks first."
            );
        }

        if (bugRepository.existsByProjectIdAndAssigneeIdAndStatusIn(projectId, userId, ACTIVE_ASSIGNMENT_BUG_STATUSES)) {
            throw new BusinessRuleException(
                    "Cannot remove member while they have assigned or in-progress bugs. Reassign active bugs first."
            );
        }

        String removedEmail = membership.getUser().getEmail();
        String removedRole = membership.getProjectRole().name();
        Long membershipId = membership.getId();

        projectMemberRepository.delete(membership);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.PROJECT_MEMBER_REMOVED,
                "PROJECT_MEMBER",
                membershipId,
                Map.of(
                        "projectKey", project.getProjectKey(),
                        "userId", userId,
                        "email", removedEmail,
                        "projectRole", removedRole
                ),
                null
        );
    }

    @Override
    @Transactional
    public ProjectMemberResponse updateProjectMemberRole(
            Long projectId,
            Long userId,
            UpdateProjectMemberRoleRequest request
    ) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanManageProject(projectId, actorDetails);

        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot update member roles on an archived project.");
        }

        ProjectMember membership = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project member not found for user: " + userId));

        RoleName nextRole = request.projectRole();
        if (project.getManager().getId().equals(userId)
                && nextRole != RoleName.PROJECT_MANAGER
                && nextRole != RoleName.ADMIN) {
            throw new BusinessRuleException("Cannot change the assigned project manager to a non-manager project role.");
        }

        RoleName previousRole = membership.getProjectRole();
        if (previousRole == nextRole) {
            return ProjectMemberResponse.from(membership);
        }

        membership.setProjectRole(nextRole);
        ProjectMember saved = projectMemberRepository.save(membership);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.PROJECT_MEMBER_ROLE_UPDATED,
                "PROJECT_MEMBER",
                saved.getId(),
                Map.of(
                        "projectKey", project.getProjectKey(),
                        "userId", userId,
                        "projectRole", previousRole.name()
                ),
                Map.of(
                        "projectKey", project.getProjectKey(),
                        "userId", userId,
                        "projectRole", nextRole.name()
                )
        );

        return ProjectMemberResponse.from(saved);
    }

    private User resolveProjectManagerForCreate(
            Long requestedManagerId,
            CustomUserDetails actorDetails,
            User actor,
            Long orgId
    ) {
        if (actorDetails.isAdmin()) {
            if (requestedManagerId == null || requestedManagerId.equals(actor.getId())) {
                return actor;
            }
            return resolveEligibleManager(requestedManagerId, orgId);
        }
        if (requestedManagerId != null && !requestedManagerId.equals(actor.getId())) {
            throw new UnauthorizedResourceAccessException(
                    "Project managers can only create projects managed by themselves."
            );
        }
        return actor;
    }

    private User resolveEligibleManager(Long managerId, Long orgId) {
        User manager = resolveActiveOrgEmployee(managerId, orgId, "Project manager");
        RoleName role = manager.getPrimaryRoleName();
        if (role != RoleName.PROJECT_MANAGER && role != RoleName.ADMIN) {
            throw new BusinessRuleException("Project manager must hold the ADMIN or PROJECT_MANAGER role.");
        }
        return manager;
    }

    private User resolveActiveOrgEmployee(Long userId, Long orgId, String label) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(label + " not found: " + userId));
        if (!user.getOrganization().getId().equals(orgId)) {
            throw new UnauthorizedResourceAccessException(label + " does not belong to your organization.");
        }
        if (!user.isActive()) {
            throw new BusinessRuleException(label + " must be an active organization employee.");
        }
        return user;
    }

    private List<User> resolveInitialTeamMembers(Long teamId, Long orgId) {
        if (teamId == null) {
            return List.of();
        }
        Team team = teamRepository.findWithLeadById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        if (!team.getOrganization().getId().equals(orgId)) {
            throw new UnauthorizedResourceAccessException("Team does not belong to your organization.");
        }
        if (!team.isActive()) {
            throw new BusinessRuleException("Cannot initialize a project with a deactivated team.");
        }

        List<User> resolved = new ArrayList<>();
        if (team.getLeadUser() != null && team.getLeadUser().isActive()
                && team.getLeadUser().getOrganization().getId().equals(orgId)) {
            resolved.add(team.getLeadUser());
        }
        for (TeamMember tm : teamMemberRepository.findByTeamIdOrderByJoinedAtAsc(teamId)) {
            User member = tm.getUser();
            if (member.isActive() && member.getOrganization().getId().equals(orgId)) {
                resolved.add(member);
            }
        }
        return resolved;
    }

    private List<User> resolveExplicitInitialMembers(List<Long> memberIds, Long orgId) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<User> resolved = new ArrayList<>();
        for (Long memberId : memberIds) {
            if (memberId == null) {
                throw new BusinessRuleException("Project member user ID cannot be null.");
            }
            if (!seen.add(memberId)) {
                throw new DuplicateResourceException("Duplicate member user ID in request: " + memberId);
            }
            resolved.add(resolveActiveOrgEmployee(memberId, orgId, "Project member"));
        }
        return resolved;
    }

    private void ensureManagerMembership(Project project, User manager, User actor) {
        projectMemberRepository.findByProjectIdAndUserId(project.getId(), manager.getId())
                .ifPresentOrElse(
                        existing -> {
                            if (existing.getProjectRole() != RoleName.PROJECT_MANAGER
                                    && existing.getProjectRole() != RoleName.ADMIN) {
                                existing.setProjectRole(RoleName.PROJECT_MANAGER);
                                projectMemberRepository.save(existing);
                            }
                        },
                        () -> projectMemberRepository.save(
                                new ProjectMember(project, manager, RoleName.PROJECT_MANAGER, actor)
                        )
                );
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessRuleException("Project end date cannot be earlier than start date.");
        }
    }

    private void validateStatusTransition(
            ProjectStatus current,
            ProjectStatus target,
            CustomUserDetails actorDetails
    ) {
        if (current == ProjectStatus.ARCHIVED) {
            if (target != ProjectStatus.ACTIVE) {
                throw new InvalidWorkflowTransitionException(
                        "Archived projects can only transition back to ACTIVE status."
                );
            }
            if (!actorDetails.isAdmin()) {
                throw new UnauthorizedResourceAccessException("Only an organization admin can reactivate an archived project.");
            }
            return;
        }
        Set<ProjectStatus> allowed = ALLOWED_STATUS_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidWorkflowTransitionException(
                    "Invalid project status transition from " + current + " to " + target + "."
            );
        }
    }

    private Map<Long, Long> loadMemberCounts(List<Project> projects) {
        if (projects.isEmpty()) {
            return Map.of();
        }
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : projectMemberRepository.countMembersByProjectIds(projectIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private ProjectDetailResponse toDetailResponse(Project project) {
        Long projectId = project.getId();
        List<ProjectMemberResponse> members = projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(projectId)
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();

        long totalTasks = taskRepository.countByProjectId(projectId);
        long completedTasks = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.DONE);
        long overdueTasks = taskRepository.countOverdueByProjectId(projectId, LocalDate.now());
        long openBugs = bugRepository.countByProjectIdAndStatusIn(projectId, OPEN_BUG_STATUSES);

        return ProjectDetailResponse.from(project, members, totalTasks, completedTasks, overdueTasks, openBugs);
    }

    private Map<String, Object> buildProjectSnapshot(Project project, long memberCount) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("projectKey", project.getProjectKey());
        snapshot.put("name", project.getName());
        snapshot.put("status", project.getStatus().name());
        snapshot.put("managerId", project.getManager().getId());
        snapshot.put("startDate", project.getStartDate().toString());
        snapshot.put("endDate", project.getEndDate() != null ? project.getEndDate().toString() : null);
        snapshot.put("memberCount", memberCount);
        return snapshot;
    }

    private String normalizeSearch(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }

    private String normalizeOptionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Map<ProjectStatus, Set<ProjectStatus>> buildStatusTransitions() {
        Map<ProjectStatus, Set<ProjectStatus>> map = new EnumMap<>(ProjectStatus.class);
        map.put(ProjectStatus.PLANNING, EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.ON_HOLD, ProjectStatus.ARCHIVED));
        map.put(ProjectStatus.ACTIVE, EnumSet.of(ProjectStatus.ON_HOLD, ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED));
        map.put(ProjectStatus.ON_HOLD, EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.ARCHIVED));
        map.put(ProjectStatus.COMPLETED, EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.ARCHIVED));
        map.put(ProjectStatus.ARCHIVED, EnumSet.of(ProjectStatus.ACTIVE));
        return map;
    }
}
