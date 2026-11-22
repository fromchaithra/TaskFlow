package com.taskflow.service.impl;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.organization.AddTeamMemberRequest;
import com.taskflow.dto.organization.CreateTeamRequest;
import com.taskflow.dto.organization.TeamDetailResponse;
import com.taskflow.dto.organization.TeamResponse;
import com.taskflow.dto.organization.UpdateTeamLeadRequest;
import com.taskflow.dto.organization.UpdateTeamRequest;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Team;
import com.taskflow.entity.TeamMember;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.TeamService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogWriter auditLogWriter;

    public TeamServiceImpl(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            AuditLogWriter auditLogWriter
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TeamResponse> listTeams(String search, Boolean active, Pageable pageable) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        String normalizedSearch = normalizeSearch(search);
        Boolean effectiveActive = viewer.isAdmin() ? active : Boolean.TRUE;

        Page<Team> teamPage = teamRepository.searchTeams(
                viewer.getOrganizationId(),
                normalizedSearch,
                effectiveActive,
                pageable
        );

        Map<Long, Long> memberCounts = loadMemberCounts(teamPage.getContent());
        Page<TeamResponse> dtoPage = teamPage.map(
                team -> TeamResponse.from(team, memberCounts.getOrDefault(team.getId(), 0L))
        );
        return PagedResponse.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public TeamDetailResponse getTeamById(Long teamId) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        Team team = findAuthorizedTeam(teamId, viewer.getOrganizationId());
        return toDetailResponse(team);
    }

    @Override
    @Transactional
    public TeamDetailResponse createTeam(CreateTeamRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Long orgId = actorDetails.getOrganizationId();
        String teamName = request.name().trim();

        if (teamRepository.existsByOrganizationIdAndNameIgnoreCase(orgId, teamName)) {
            throw new DuplicateResourceException("A team named '" + teamName + "' already exists in this organization.");
        }

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        User lead = resolveOptionalActiveOrgEmployee(request.leadUserId(), orgId, "Team lead");
        List<User> initialMembers = resolveActiveOrgMembers(request.memberIds(), orgId);

        Team team = new Team(
                org,
                teamName,
                normalizeOptionalText(request.description()),
                lead
        );
        Team saved = teamRepository.save(team);

        for (User member : initialMembers) {
            teamMemberRepository.save(new TeamMember(saved, member));
        }

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                org,
                actor,
                null,
                AuditAction.TEAM_CREATED,
                "TEAM",
                saved.getId(),
                null,
                buildTeamSnapshot(saved, initialMembers.size())
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public TeamDetailResponse updateTeam(Long teamId, UpdateTeamRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Long orgId = actorDetails.getOrganizationId();
        Team team = findAuthorizedTeam(teamId, orgId);

        String nextName = request.name().trim();
        if (teamRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(orgId, nextName, teamId)) {
            throw new DuplicateResourceException("A team named '" + nextName + "' already exists in this organization.");
        }

        Map<String, Object> previous = buildTeamSnapshot(team, teamMemberRepository.countByTeamId(teamId));

        User nextLead = resolveOptionalActiveOrgEmployee(request.leadUserId(), orgId, "Team lead");
        team.setName(nextName);
        team.setDescription(normalizeOptionalText(request.description()));
        team.setLeadUser(nextLead);
        if (request.active() != null) {
            team.setActive(request.active());
        }

        Team saved = teamRepository.save(team);

        if (request.memberIds() != null) {
            syncTeamMembers(saved, request.memberIds(), orgId);
        }

        long updatedMemberCount = teamMemberRepository.countByTeamId(saved.getId());
        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                saved.getOrganization(),
                actor,
                null,
                AuditAction.TEAM_UPDATED,
                "TEAM",
                saved.getId(),
                previous,
                buildTeamSnapshot(saved, updatedMemberCount)
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public TeamResponse deactivateTeam(Long teamId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Team team = findAuthorizedTeam(teamId, actorDetails.getOrganizationId());
        long memberCount = teamMemberRepository.countByTeamId(teamId);

        if (!team.isActive()) {
            return TeamResponse.from(team, memberCount);
        }

        team.setActive(false);
        Team saved = teamRepository.save(team);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                saved.getOrganization(),
                actor,
                null,
                AuditAction.TEAM_DEACTIVATED,
                "TEAM",
                saved.getId(),
                Map.of("active", true, "name", saved.getName()),
                Map.of("active", false, "name", saved.getName())
        );

        return TeamResponse.from(saved, memberCount);
    }

    @Override
    @Transactional
    public TeamDetailResponse addMember(Long teamId, AddTeamMemberRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Long orgId = actorDetails.getOrganizationId();
        Team team = findAuthorizedTeam(teamId, orgId);

        if (!team.isActive()) {
            throw new BusinessRuleException("Cannot add members to a deactivated team.");
        }

        User member = resolveRequiredActiveOrgEmployee(request.userId(), orgId, "Team member");
        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, member.getId())) {
            throw new DuplicateResourceException("Employee " + member.getEmail() + " is already a member of this team.");
        }

        teamMemberRepository.save(new TeamMember(team, member));

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                team.getOrganization(),
                actor,
                null,
                AuditAction.TEAM_MEMBER_ADDED,
                "TEAM",
                team.getId(),
                null,
                Map.of("teamName", team.getName(), "userId", member.getId(), "email", member.getEmail())
        );

        return toDetailResponse(team);
    }

    @Override
    @Transactional
    public void removeMember(Long teamId, Long userId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Team team = findAuthorizedTeam(teamId, actorDetails.getOrganizationId());

        TeamMember membership = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found for user: " + userId));

        String removedEmail = membership.getUser().getEmail();
        teamMemberRepository.delete(membership);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                team.getOrganization(),
                actor,
                null,
                AuditAction.TEAM_MEMBER_REMOVED,
                "TEAM",
                team.getId(),
                Map.of("teamName", team.getName(), "userId", userId, "email", removedEmail),
                null
        );
    }

    @Override
    @Transactional
    public TeamDetailResponse updateTeamLead(Long teamId, UpdateTeamLeadRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Long orgId = actorDetails.getOrganizationId();
        Team team = findAuthorizedTeam(teamId, orgId);

        if (!team.isActive()) {
            throw new BusinessRuleException("Cannot change the team lead of a deactivated team.");
        }

        User nextLead = resolveRequiredActiveOrgEmployee(request.leadUserId(), orgId, "Team lead");
        Long previousLeadId = team.getLeadUser() != null ? team.getLeadUser().getId() : null;

        if (nextLead.getId().equals(previousLeadId)) {
            return toDetailResponse(team);
        }

        team.setLeadUser(nextLead);
        Team saved = teamRepository.save(team);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        Map<String, Object> previous = new HashMap<>();
        previous.put("leadUserId", previousLeadId);
        auditLogWriter.record(
                saved.getOrganization(),
                actor,
                null,
                AuditAction.TEAM_LEAD_CHANGED,
                "TEAM",
                saved.getId(),
                previous,
                Map.of("leadUserId", nextLead.getId(), "leadEmail", nextLead.getEmail())
        );

        return toDetailResponse(saved);
    }

    private Team findAuthorizedTeam(Long teamId, Long orgId) {
        Team team = teamRepository.findWithLeadById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        if (!team.getOrganization().getId().equals(orgId)) {
            throw new UnauthorizedResourceAccessException("You do not have access to this team.");
        }
        return team;
    }

    private User resolveOptionalActiveOrgEmployee(Long userId, Long orgId, String roleLabel) {
        if (userId == null) {
            return null;
        }
        return resolveRequiredActiveOrgEmployee(userId, orgId, roleLabel);
    }

    private User resolveRequiredActiveOrgEmployee(Long userId, Long orgId, String roleLabel) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(roleLabel + " not found: " + userId));
        if (!user.getOrganization().getId().equals(orgId)) {
            throw new UnauthorizedResourceAccessException(roleLabel + " does not belong to your organization.");
        }
        if (!user.isActive()) {
            throw new BusinessRuleException(roleLabel + " must be an active organization employee.");
        }
        return user;
    }

    private List<User> resolveActiveOrgMembers(List<Long> memberIds, Long orgId) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        List<User> resolved = new ArrayList<>();
        for (Long memberId : memberIds) {
            if (memberId == null) {
                throw new BusinessRuleException("Member user ID cannot be null.");
            }
            if (!uniqueIds.add(memberId)) {
                throw new DuplicateResourceException("Duplicate member user ID in request: " + memberId);
            }
            resolved.add(resolveRequiredActiveOrgEmployee(memberId, orgId, "Team member"));
        }
        return resolved;
    }

    private void syncTeamMembers(Team team, List<Long> requestedMemberIds, Long orgId) {
        List<User> targetUsers = resolveActiveOrgMembers(requestedMemberIds, orgId);
        Set<Long> targetUserIds = new LinkedHashSet<>();
        for (User u : targetUsers) {
            targetUserIds.add(u.getId());
        }

        List<TeamMember> existingMembers = teamMemberRepository.findByTeamIdOrderByJoinedAtAsc(team.getId());
        Set<Long> existingUserIds = new LinkedHashSet<>();
        for (TeamMember existing : existingMembers) {
            Long existingUserId = existing.getUser().getId();
            if (!targetUserIds.contains(existingUserId)) {
                teamMemberRepository.delete(existing);
            } else {
                existingUserIds.add(existingUserId);
            }
        }

        for (User target : targetUsers) {
            if (!existingUserIds.contains(target.getId())) {
                teamMemberRepository.save(new TeamMember(team, target));
            }
        }
    }

    private Map<Long, Long> loadMemberCounts(List<Team> teams) {
        if (teams.isEmpty()) {
            return Map.of();
        }
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : teamMemberRepository.countMembersByTeamIds(teamIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private TeamDetailResponse toDetailResponse(Team team) {
        List<UserSummaryDto> members = teamMemberRepository.findByTeamIdOrderByJoinedAtAsc(team.getId())
                .stream()
                .map(tm -> UserSummaryDto.from(tm.getUser()))
                .toList();
        return TeamDetailResponse.from(team, members);
    }

    private Map<String, Object> buildTeamSnapshot(Team team, long memberCount) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("name", team.getName());
        snapshot.put("active", team.isActive());
        snapshot.put("leadUserId", team.getLeadUser() != null ? team.getLeadUser().getId() : null);
        snapshot.put("memberCount", memberCount);
        return snapshot;
    }

    private String normalizeSearch(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }

    private String normalizeOptionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
