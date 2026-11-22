package com.taskflow.service;

import com.taskflow.dto.analytics.AnalyticsQuery;
import com.taskflow.dto.analytics.AnalyticsScope;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.Team;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.impl.AnalyticsAuthorizationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsAuthorizationServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectSecurityEvaluator projectSecurityEvaluator;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private UserRepository userRepository;

    private AnalyticsAuthorizationServiceImpl service;
    private Organization orgA;
    private Organization orgB;
    private User admin;
    private User projectManager;

    @BeforeEach
    void setUp() {
        service = new AnalyticsAuthorizationServiceImpl(
                projectRepository, projectSecurityEvaluator, teamRepository, userRepository);

        orgA = new Organization("Org Alpha", "org-alpha", "Software", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgA, "id", 1L);
        orgB = new Organization("Org Beta", "org-beta", "Finance", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgB, "id", 2L);

        admin = buildUser(10L, orgA, "admin@alpha.test", new Role(RoleName.ADMIN, "Admin"));
        projectManager = buildUser(11L, orgA, "pm@alpha.test", new Role(RoleName.PROJECT_MANAGER, "PM"));

        authenticateAs(admin);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminWithoutFiltersResolvesAllOrganizationProjects() {
        Project first = project(21L);
        Project second = project(22L);
        when(projectRepository.searchAllProjects(eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        AnalyticsScope scope = service.resolveScope(query(List.of(), List.of(), List.of()));

        assertThat(scope.organizationId()).isEqualTo(1L);
        assertThat(scope.viewerId()).isEqualTo(10L);
        assertThat(scope.admin()).isTrue();
        assertThat(scope.allowedProjectIds()).containsExactlyInAnyOrder(21L, 22L);
    }

    @Test
    void nonAdminWithoutFiltersResolvesOnlyMemberProjects() {
        authenticateAs(projectManager);
        Project memberProject = project(23L);
        when(projectRepository.searchMemberProjects(eq(1L), eq(11L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(memberProject)));

        AnalyticsScope scope = service.resolveScope(query(List.of(), List.of(), List.of()));

        assertThat(scope.admin()).isFalse();
        assertThat(scope.allowedProjectIds()).containsExactly(23L);
    }

    @Test
    void explicitProjectFiltersAreCheckedByExistingProjectGuard() {
        authenticateAs(projectManager);

        service.resolveScope(query(List.of(7L), List.of(), List.of()));

        verify(projectSecurityEvaluator).assertCanViewProject(eq(7L), any(CustomUserDetails.class));
        verifyNoInteractions(projectRepository);
    }

    @Test
    void teamFilterFromAnotherOrganizationIsRejected() {
        when(projectRepository.searchAllProjects(eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        Team foreignTeam = new Team(orgB, "Beta Team", "Foreign", null);
        ReflectionTestUtils.setField(foreignTeam, "id", 99L);
        when(teamRepository.findById(99L)).thenReturn(Optional.of(foreignTeam));

        assertThatThrownBy(() -> service.resolveScope(query(List.of(), List.of(99L), List.of())))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void unknownEmployeeFilterIsRejected() {
        when(projectRepository.searchAllProjects(eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveScope(query(List.of(), List.of(), List.of(404L))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static AnalyticsQuery query(List<Long> projectIds, List<Long> teamIds, List<Long> employeeIds) {
        return new AnalyticsQuery(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                null,
                null,
                null,
                projectIds,
                teamIds,
                employeeIds
        );
    }

    private static User buildUser(long id, Organization organization, String email, Role role) {
        User user = new User(organization, "EMP-" + id, email, "hash", "User " + id, "Tester", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Project project(long id) {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(id);
        return project;
    }

    private static void authenticateAs(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
