package com.taskflow.service.impl;

import com.taskflow.dto.dashboard.DashboardStatsResponse;
import com.taskflow.entity.Task;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.TestCaseRepository;
import com.taskflow.repository.TestExecutionRepository;
import com.taskflow.repository.TestRunRepository;
import com.taskflow.repository.TestSuiteRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.service.DashboardService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TaskRepository taskRepository;
    private final BugRepository bugRepository;
    private final NotificationRepository notificationRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final TestRunRepository testRunRepository;
    private final TestExecutionRepository testExecutionRepository;

    public DashboardServiceImpl(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            TaskRepository taskRepository,
            BugRepository bugRepository,
            NotificationRepository notificationRepository,
            TestCaseRepository testCaseRepository,
            TestSuiteRepository testSuiteRepository,
            TestRunRepository testRunRepository,
            TestExecutionRepository testExecutionRepository
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.taskRepository = taskRepository;
        this.bugRepository = bugRepository;
        this.notificationRepository = notificationRepository;
        this.testCaseRepository = testCaseRepository;
        this.testSuiteRepository = testSuiteRepository;
        this.testRunRepository = testRunRepository;
        this.testExecutionRepository = testExecutionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        var viewer = SecurityUtils.currentUser();
        Long orgId = viewer.getOrganizationId();
        Long viewerId = viewer.getId();
        boolean isAdmin = viewer.isAdmin();

        long totalEmployees = userRepository.searchUsers(orgId, null, null, null, PageRequest.of(0, 1)).getTotalElements();
        long activeEmployees = userRepository.searchUsers(orgId, null, null, Boolean.TRUE, PageRequest.of(0, 1)).getTotalElements();
        long inactiveEmployees = totalEmployees - activeEmployees;

        long totalProjects = projectRepository.countByOrganizationId(orgId);
        long activeProjects = projectRepository.countByOrganizationIdAndStatus(orgId, ProjectStatus.ACTIVE);
        long completedProjects = projectRepository.countByOrganizationIdAndStatus(orgId, ProjectStatus.COMPLETED);

        long managedProjects = projectRepository.countByOrganizationIdAndManagerId(orgId, viewerId);

        long totalTeams = teamRepository.searchTeams(orgId, null, null, PageRequest.of(0, 1)).getTotalElements();

        var taskPage = taskRepository.searchAuthorizedTasks(orgId, viewerId, isAdmin, null, null, null, null, null, false, LocalDate.now(), null, PageRequest.of(0, 1));
        long totalTasks = taskPage.getTotalElements();

        Map<String, Long> byStatus = new java.util.HashMap<>();
        for (TaskStatus s : TaskStatus.values()) {
            long c = taskRepository.searchAuthorizedTasks(orgId, viewerId, isAdmin, null, null, s, null, null, false, LocalDate.now(), null, PageRequest.of(0, 1)).getTotalElements();
            byStatus.put(s.name(), c);
        }

        long overdue = taskRepository.searchAuthorizedTasks(orgId, viewerId, isAdmin, null, null, null, null, null, true, LocalDate.now(), null, PageRequest.of(0, 1)).getTotalElements();

        long dueToday = taskRepository.countDueToday(orgId, viewerId, isAdmin, viewerId, LocalDate.now());

        long tasksReadyForTesting = byStatus.getOrDefault("IN_REVIEW", 0L);

        // bugs
        long openBugs = bugRepository.searchAuthorizedBugs(orgId, viewerId, isAdmin, null, null, null, null, null, PageRequest.of(0, 1)).getTotalElements();
        Map<String, Long> bugsBySeverity = new java.util.HashMap<>();
        for (BugSeverity s : BugSeverity.values()) {
            long c = bugRepository.searchAuthorizedBugs(orgId, viewerId, isAdmin, null, null, s, null, null, PageRequest.of(0, 1)).getTotalElements();
            bugsBySeverity.put(s.name(), c);
        }
        long criticalBugs = bugsBySeverity.getOrDefault("CRITICAL", 0L) + bugsBySeverity.getOrDefault("HIGH", 0L);

        long unread = notificationRepository.countByRecipientIdAndReadFalse(viewerId);

        long totalTestCases = testCaseRepository.searchTestCases(orgId, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        long totalTestSuites = testSuiteRepository.searchTestSuites(orgId, null, null, null, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        long activeTestRuns = testRunRepository.searchTestRuns(orgId, null, com.taskflow.enums.TestRunStatus.PLANNED, null, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements()
                + testRunRepository.searchTestRuns(orgId, null, com.taskflow.enums.TestRunStatus.IN_PROGRESS, null, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();

        long passedExecutions = 0, failedExecutions = 0, blockedExecutions = 0;
        // Organization-scoped aggregate query instead of loading every execution of every tenant.
        for (var row : testExecutionRepository.countDashboardExecutionsByResult(orgId)) {
            switch (row.key()) {
                case "PASS" -> passedExecutions += row.count();
                case "FAIL" -> failedExecutions += row.count();
                case "BLOCKED" -> blockedExecutions += row.count();
                default -> {}
            }
        }

        return new DashboardStatsResponse(
                viewer.getPrimaryRole().name(),
                totalEmployees, activeEmployees, inactiveEmployees,
                totalProjects, activeProjects, completedProjects,
                managedProjects,
                totalTeams, totalTasks, overdue, dueToday, tasksReadyForTesting, byStatus,
                openBugs, criticalBugs, bugsBySeverity, unread,
                totalTestCases, totalTestSuites, activeTestRuns, passedExecutions, failedExecutions, blockedExecutions
        );
    }
}
