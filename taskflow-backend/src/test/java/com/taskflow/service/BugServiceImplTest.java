package com.taskflow.service;

import com.taskflow.dto.bug.AssignBugRequest;
import com.taskflow.dto.bug.BugDetailResponse;
import com.taskflow.dto.bug.CreateBugRequest;
import com.taskflow.dto.bug.ReopenBugRequest;
import com.taskflow.dto.bug.UpdateBugRequest;
import com.taskflow.dto.bug.UpdateBugSeverityRequest;
import com.taskflow.dto.bug.UpdateBugStatusRequest;
import com.taskflow.dto.bug.VerifyBugRequest;
import com.taskflow.entity.Bug;
import com.taskflow.entity.BugHistory;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.NotificationType;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.InvalidWorkflowTransitionException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.BugAttachmentRepository;
import com.taskflow.repository.BugCommentRepository;
import com.taskflow.repository.BugHistoryRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.impl.BugServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugServiceImplTest {

    @Mock
    private BugRepository bugRepository;
    @Mock
    private BugHistoryRepository bugHistoryRepository;
    @Mock
    private BugCommentRepository bugCommentRepository;
    @Mock
    private BugAttachmentRepository bugAttachmentRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private NotificationDispatcher notificationDispatcher;

    private BugServiceImpl bugService;

    private Organization orgA;
    private Organization orgB;
    private User adminA;
    private User pmA;
    private User devOneA;
    private User devTwoA;
    private User outsiderDevA;
    private User qaA;
    private User adminB;
    private Project projectA;

    @BeforeEach
    void setUp() {
        ProjectSecurityEvaluator projectSecurityEvaluator =
                new ProjectSecurityEvaluator(projectRepository, projectMemberRepository);
        bugService = new BugServiceImpl(
                bugRepository,
                bugHistoryRepository,
                bugCommentRepository,
                bugAttachmentRepository,
                projectRepository,
                projectMemberRepository,
                taskRepository,
                userRepository,
                projectSecurityEvaluator,
                auditLogWriter,
                notificationDispatcher
        );

        orgA = new Organization("Alpha Corp", "alpha-corp", "Software", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgA, "id", 1L);

        orgB = new Organization("Beta Corp", "beta-corp", "FinTech", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(orgB, "id", 2L);

        Role adminRole = new Role(RoleName.ADMIN, "Admin");
        Role pmRole = new Role(RoleName.PROJECT_MANAGER, "Project Manager");
        Role devRole = new Role(RoleName.DEVELOPER, "Developer");
        Role qaRole = new Role(RoleName.QA_TESTER, "QA Tester");

        adminA = buildUser(10L, orgA, "admin@alpha.test", adminRole);
        pmA = buildUser(11L, orgA, "pm@alpha.test", pmRole);
        devOneA = buildUser(12L, orgA, "dev1@alpha.test", devRole);
        devTwoA = buildUser(13L, orgA, "dev2@alpha.test", devRole);
        outsiderDevA = buildUser(14L, orgA, "outsider@alpha.test", devRole);
        qaA = buildUser(15L, orgA, "qa@alpha.test", qaRole);
        adminB = buildUser(20L, orgB, "admin@beta.test", adminRole);

        projectA = buildProject(100L, orgA, "ALPHA", pmA);

        authenticateAs(qaA);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBug_WhenAssigneeNotProjectMember_ShouldThrowBusinessRuleException() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 15L)).thenReturn(true);
        when(projectRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(projectA));
        when(userRepository.findById(15L)).thenReturn(Optional.of(qaA));
        when(userRepository.findById(14L)).thenReturn(Optional.of(outsiderDevA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 14L)).thenReturn(false);

        CreateBugRequest request = new CreateBugRequest(
                100L, null, "Login crash", "Null pointer on login", "1. Open login",
                BugSeverity.HIGH, PriorityLevel.HIGH, 14L
        );

        assertThatThrownBy(() -> bugService.createBug(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void assignBug_WhenNonProjectMember_ShouldThrowBusinessRuleException() {
        authenticateAs(pmA);
        Bug bug = buildBug(500L, projectA, "ALPHA-BUG-101", BugStatus.OPEN, qaA, null);

        when(bugRepository.findDetailById(500L)).thenReturn(Optional.of(bug));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(userRepository.findById(14L)).thenReturn(Optional.of(outsiderDevA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 14L)).thenReturn(false);

        assertThatThrownBy(() -> bugService.assignBug(500L, new AssignBugRequest(14L, "Assign")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void updateBugStatus_WhenInvalidTransitionOpenToVerified_ShouldThrowInvalidWorkflowTransition() {
        authenticateAs(adminA);
        Bug bug = buildBug(501L, projectA, "ALPHA-BUG-102", BugStatus.OPEN, qaA, devOneA);

        when(bugRepository.findDetailById(501L)).thenReturn(Optional.of(bug));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));

        assertThatThrownBy(() -> bugService.updateBugStatus(
                501L, new UpdateBugStatusRequest(BugStatus.VERIFIED, "Skipping resolution")
        )).isInstanceOf(InvalidWorkflowTransitionException.class);
    }

    @Test
    void verifyBug_WhenQaApprovesResolvedBug_ShouldTransitionToVerifiedAndNotifyAndAudit() {
        authenticateAs(qaA);
        Bug resolvedBug = buildBug(502L, projectA, "ALPHA-BUG-103", BugStatus.RESOLVED, qaA, devOneA);

        when(bugRepository.findDetailById(502L)).thenReturn(Optional.of(resolvedBug));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 15L)).thenReturn(true);
        when(userRepository.findById(15L)).thenReturn(Optional.of(qaA));
        when(bugRepository.save(any(Bug.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bugHistoryRepository.findByBugIdOrderByCreatedAtDesc(502L)).thenReturn(List.of());

        BugDetailResponse response = bugService.verifyBug(502L, new VerifyBugRequest(true, "Verified on staging"));

        assertThat(response.status()).isEqualTo(BugStatus.VERIFIED);
        verify(bugHistoryRepository).save(any(BugHistory.class));
        verify(auditLogWriter).record(
                eq(orgA), eq(qaA), eq(projectA), eq(AuditAction.BUG_VERIFIED), eq("BUG"), eq(502L), any(), any()
        );
        verify(notificationDispatcher).notifyUser(
                eq(devOneA), eq(NotificationType.BUG_VERIFIED), any(), any(), eq("BUG"), eq(502L)
        );
    }

    @Test
    void verifyBug_WhenQaRejectsResolvedBug_ShouldReturnBugToReopenedAndNotify() {
        authenticateAs(qaA);
        Bug resolvedBug = buildBug(503L, projectA, "ALPHA-BUG-104", BugStatus.RESOLVED, qaA, devOneA);

        when(bugRepository.findDetailById(503L)).thenReturn(Optional.of(resolvedBug));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 15L)).thenReturn(true);
        when(userRepository.findById(15L)).thenReturn(Optional.of(qaA));
        when(bugRepository.save(any(Bug.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bugHistoryRepository.findByBugIdOrderByCreatedAtDesc(503L)).thenReturn(List.of());

        BugDetailResponse response = bugService.verifyBug(503L, new VerifyBugRequest(false, "Still reproduces on Safari"));

        assertThat(response.status()).isEqualTo(BugStatus.REOPENED);
        verify(auditLogWriter).record(
                eq(orgA), eq(qaA), eq(projectA), eq(AuditAction.BUG_REOPENED), eq("BUG"), eq(503L), any(), any()
        );
        verify(notificationDispatcher).notifyUser(
                eq(devOneA), eq(NotificationType.BUG_REOPENED), any(), any(), eq("BUG"), eq(503L)
        );
    }

    @Test
    void reopenBug_WhenVerifiedBug_ShouldTransitionToReopened() {
        authenticateAs(qaA);
        Bug verifiedBug = buildBug(504L, projectA, "ALPHA-BUG-105", BugStatus.VERIFIED, qaA, devOneA);

        when(bugRepository.findDetailById(504L)).thenReturn(Optional.of(verifiedBug));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 15L)).thenReturn(true);
        when(userRepository.findById(15L)).thenReturn(Optional.of(qaA));
        when(bugRepository.save(any(Bug.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bugHistoryRepository.findByBugIdOrderByCreatedAtDesc(504L)).thenReturn(List.of());

        BugDetailResponse response = bugService.reopenBug(504L, new ReopenBugRequest("Regression after v2.4 deploy"));

        assertThat(response.status()).isEqualTo(BugStatus.REOPENED);
    }

    @Test
    void updateBug_WhenDeveloperEditsAnotherDevelopersBugOrChangesSeverity_ShouldThrowUnauthorized() {
        authenticateAs(devTwoA);
        Bug devOneBug = buildBug(505L, projectA, "ALPHA-BUG-106", BugStatus.IN_PROGRESS, qaA, devOneA);
        when(bugRepository.findDetailById(505L)).thenReturn(Optional.of(devOneBug));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 13L)).thenReturn(true);

        UpdateBugRequest request = new UpdateBugRequest(
                null, "Unauthorized dev edit", null, null, null, null, null, null, null
        );
        assertThatThrownBy(() -> bugService.updateBug(505L, request))
                .isInstanceOf(UnauthorizedResourceAccessException.class);

        authenticateAs(devOneA);
        when(projectMemberRepository.existsByProjectIdAndUserId(100L, 12L)).thenReturn(true);
        assertThatThrownBy(() -> bugService.updateBugSeverity(
                505L, new UpdateBugSeverityRequest(BugSeverity.LOW, "Downgrading")
        )).isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    @Test
    void getBugById_WhenCrossOrganizationAdmin_ShouldThrowUnauthorized() {
        authenticateAs(adminB);
        Bug bug = buildBug(506L, projectA, "ALPHA-BUG-107", BugStatus.OPEN, qaA, devOneA);
        when(bugRepository.findDetailById(506L)).thenReturn(Optional.of(bug));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(projectA));

        assertThatThrownBy(() -> bugService.getBugById(506L))
                .isInstanceOf(UnauthorizedResourceAccessException.class);
    }

    private User buildUser(Long id, Organization org, String email, Role role) {
        User user = new User(org, "EMP-" + id, email, "encoded-hash", "User " + id, "Staff", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Project buildProject(Long id, Organization org, String key, User manager) {
        Project project = new Project(
                org, key, "Project " + key, "Desc", manager,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        );
        project.setStatus(ProjectStatus.ACTIVE);
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private Bug buildBug(Long id, Project project, String key, BugStatus status, User reporter, User assignee) {
        Bug bug = new Bug(
                project, key, null, "Bug " + key, "Detailed bug description", "Steps",
                BugSeverity.HIGH, PriorityLevel.HIGH, reporter, assignee
        );
        bug.setStatus(status);
        ReflectionTestUtils.setField(bug, "id", id);
        return bug;
    }

    private void authenticateAs(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
