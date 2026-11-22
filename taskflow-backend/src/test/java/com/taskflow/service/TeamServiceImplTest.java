package com.taskflow.service;

import com.taskflow.dto.organization.AddTeamMemberRequest;
import com.taskflow.dto.organization.CreateTeamRequest;
import com.taskflow.dto.organization.UpdateTeamLeadRequest;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.Team;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.service.impl.TeamServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AuditLogWriter auditLogWriter;

    private TeamServiceImpl teamService;
    private Organization orgA;
    private Organization orgB;
    private Role adminRole;
    private Role devRole;
    private User adminA;

    @BeforeEach
    void setUp() {
        teamService = new TeamServiceImpl(
                teamRepository,
                teamMemberRepository,
                userRepository,
                organizationRepository,
                auditLogWriter
        );

        orgA = new Organization("Org Alpha", "org-alpha", "Software", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgA, "id", 1L);

        orgB = new Organization("Org Beta", "org-beta", "Finance", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgB, "id", 2L);

        adminRole = new Role(RoleName.ADMIN, "Admin");
        devRole = new Role(RoleName.DEVELOPER, "Developer");

        adminA = buildUser(10L, orgA, "admin@alpha.test", adminRole, true);
        authenticateAs(adminA);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTeam_WhenDuplicateName_ShouldThrowDuplicateResourceException() {
        when(teamRepository.existsByOrganizationIdAndNameIgnoreCase(1L, "Platform Engineering")).thenReturn(true);

        CreateTeamRequest request = new CreateTeamRequest("Platform Engineering", "Core APIs", null, List.of());

        assertThatThrownBy(() -> teamService.createTeam(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createTeam_WhenLeadNotFound_ShouldThrowResourceNotFoundException() {
        when(teamRepository.existsByOrganizationIdAndNameIgnoreCase(1L, "QA Guild")).thenReturn(false);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(orgA));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        CreateTeamRequest request = new CreateTeamRequest("QA Guild", "Testing", 999L, List.of());

        assertThatThrownBy(() -> teamService.createTeam(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Team lead not found");
    }

    @Test
    void createTeam_WhenLeadInactive_ShouldThrowBusinessRuleException() {
        User inactiveLead = buildUser(11L, orgA, "inactive@alpha.test", devRole, false);
        when(teamRepository.existsByOrganizationIdAndNameIgnoreCase(1L, "QA Guild")).thenReturn(false);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(orgA));
        when(userRepository.findById(11L)).thenReturn(Optional.of(inactiveLead));

        CreateTeamRequest request = new CreateTeamRequest("QA Guild", "Testing", 11L, List.of());

        assertThatThrownBy(() -> teamService.createTeam(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("active organization employee");
    }

    @Test
    void addMember_WhenMemberFromAnotherOrganization_ShouldThrowUnauthorized() {
        Team teamA = buildTeam(100L, orgA, "Core Backend", true);
        User orgBUser = buildUser(20L, orgB, "dev@beta.test", devRole, true);

        when(teamRepository.findWithLeadById(100L)).thenReturn(Optional.of(teamA));
        when(userRepository.findById(20L)).thenReturn(Optional.of(orgBUser));

        assertThatThrownBy(() -> teamService.addMember(100L, new AddTeamMemberRequest(20L)))
                .isInstanceOf(UnauthorizedResourceAccessException.class)
                .hasMessageContaining("does not belong to your organization");
    }

    @Test
    void addMember_WhenAlreadyMember_ShouldThrowDuplicateResourceException() {
        Team teamA = buildTeam(100L, orgA, "Core Backend", true);
        User orgAUser = buildUser(12L, orgA, "dev@alpha.test", devRole, true);

        when(teamRepository.findWithLeadById(100L)).thenReturn(Optional.of(teamA));
        when(userRepository.findById(12L)).thenReturn(Optional.of(orgAUser));
        when(teamMemberRepository.existsByTeamIdAndUserId(100L, 12L)).thenReturn(true);

        assertThatThrownBy(() -> teamService.addMember(100L, new AddTeamMemberRequest(12L)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void updateTeamLead_WhenTeamDeactivated_ShouldThrowBusinessRuleException() {
        Team inactiveTeam = buildTeam(101L, orgA, "Legacy Ops", false);
        when(teamRepository.findWithLeadById(101L)).thenReturn(Optional.of(inactiveTeam));

        assertThatThrownBy(() -> teamService.updateTeamLead(101L, new UpdateTeamLeadRequest(10L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("deactivated team");
    }

    @Test
    void getTeamById_WhenCrossOrganization_ShouldThrowUnauthorized() {
        Team teamB = buildTeam(200L, orgB, "Beta Platform", true);
        when(teamRepository.findWithLeadById(200L)).thenReturn(Optional.of(teamB));

        assertThatThrownBy(() -> teamService.getTeamById(200L))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    private User buildUser(Long id, Organization org, String email, Role role, boolean active) {
        User user = new User(org, "EMP-" + id, email, "encoded-hash", "User " + id, "Engineer", role);
        user.setActive(active);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Team buildTeam(Long id, Organization org, String name, boolean active) {
        Team team = new Team(org, name, "Description", null);
        team.setActive(active);
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private void authenticateAs(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
