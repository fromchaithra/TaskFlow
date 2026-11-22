package com.taskflow.live;

import com.taskflow.dto.activity.ActivityResponse;
import com.taskflow.entity.AuditLog;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.service.AnalyticsAuthorizationService;
import com.taskflow.service.impl.ActivityServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityServiceImplTest {

    @Mock private AuditLogRepository repository;
    @Mock private AnalyticsAuthorizationService authorizationService;

    private final Organization organization = new Organization("TaskFlow Enterprise", "activity", "IT", "UTC", "MON-FRI");
    private final Role role = new Role(RoleName.DEVELOPER, "Developer");
    private final User actor = new User(organization, "EMP-1", "dev@taskflow.test", "hash", "Dev User", "Engineer", role);
    private final Project allowed = new Project(organization, "ALW", "Allowed", "desc", actor,
            LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
    private final Project foreign = new Project(organization, "FRB", "Forbidden", "desc", actor,
            LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(boolean admin) {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getId()).thenReturn(1L);
        when(principal.getOrganizationId()).thenReturn(organization.getId());
        when(principal.isAdmin()).thenReturn(admin);
        doReturn(Set.of(new SimpleGrantedAuthority("ROLE_DEVELOPER"))).when(principal).getAuthorities();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private AuditLog log(Long id, Project project) {
        AuditLog entry = new AuditLog(organization, actor, project, AuditAction.TASK_UPDATED,
                "TASK", id, null, null, null);
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }

    @Test
    void nonAdminSeesOnlyAuthorizedProjects() {
        authenticate(false);
        when(authorizationService.resolveVisibleProjectIds()).thenReturn(Set.of(1L));
        when(repository.searchAuthorizedAuditLogs(eq(organization.getId()), eq(false), eq(Set.of(1L)), any()))
                .thenReturn(new PageImpl<>(List.of(log(10L, allowed))));

        var result = new ActivityServiceImpl(repository, authorizationService)
                .recentActivity(PageRequest.of(0, 10));

        assertThat(result.content()).extracting(ActivityResponse::id).containsExactly(10L);
        verify(repository).searchAuthorizedAuditLogs(eq(organization.getId()), eq(false), eq(Set.of(1L)), any());
    }

    @Test
    void adminSeesOrganizationLevelActivity() {
        authenticate(true);
        when(authorizationService.resolveVisibleProjectIds()).thenReturn(Set.of(1L, 2L));
        when(repository.searchAuthorizedAuditLogs(eq(organization.getId()), eq(true), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log(20L, null), log(21L, foreign))));

        var result = new ActivityServiceImpl(repository, authorizationService)
                .recentActivity(PageRequest.of(0, 10));

        assertThat(result.content()).extracting(ActivityResponse::id).containsExactly(20L, 21L);
    }

    @Test
    void activityResponseNeverExposesRawAuditSnapshots() {
        AuditLog entry = new AuditLog(organization, actor, allowed, AuditAction.TASK_UPDATED,
                "TASK", 30L, Map.of("password", "secret"), Map.of("token", "secret"), "127.0.0.1");
        ActivityResponse response = new ActivityResponse(entry.getId(),
                entry.getActor().getFullName(), entry.getAction().name(), entry.getEntityType(),
                entry.getEntityId(), entry.getProject().getName(), Instant.now());

        String text = response.toString();
        assertThat(text).doesNotContain("password", "token", "127.0.0.1", "secret");
    }
}
