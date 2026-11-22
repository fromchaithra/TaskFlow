package com.taskflow.service.impl;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.test.TestExecutionResponse;
import com.taskflow.dto.test.UpdateTestExecutionRequest;
import com.taskflow.dto.test.CreateTestRunRequest;
import com.taskflow.dto.test.TestRunResponse;
import com.taskflow.entity.Project;
import com.taskflow.entity.TestCase;
import com.taskflow.entity.TestExecution;
import com.taskflow.entity.TestRun;
import com.taskflow.entity.TestSuite;
import com.taskflow.entity.User;
import com.taskflow.entity.TestSuiteCase;
import com.taskflow.enums.TestRunStatus;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TestCaseRepository;
import com.taskflow.repository.TestExecutionRepository;
import com.taskflow.repository.TestRunRepository;
import com.taskflow.repository.TestSuiteCaseRepository;
import com.taskflow.repository.TestSuiteRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.TestRunService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TestRunServiceImpl implements TestRunService {

    private final TestRunRepository runRepository;
    private final TestExecutionRepository executionRepository;
    private final TestSuiteRepository suiteRepository;
    private final TestSuiteCaseRepository suiteCaseRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;

    public TestRunServiceImpl(TestRunRepository runRepository, TestExecutionRepository executionRepository,
                             TestSuiteRepository suiteRepository, TestSuiteCaseRepository suiteCaseRepository,
                             TestCaseRepository testCaseRepository, ProjectRepository projectRepository,
                             UserRepository userRepository, ProjectSecurityEvaluator projectSecurityEvaluator) {
        this.runRepository = runRepository;
        this.executionRepository = executionRepository;
        this.suiteRepository = suiteRepository;
        this.suiteCaseRepository = suiteCaseRepository;
        this.testCaseRepository = testCaseRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
    }

    @Override
    @Transactional
    public TestRunResponse create(CreateTestRunRequest request) {
        var actor = SecurityUtils.currentUser();
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Project not found: " + request.projectId()));
        if (!project.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Project not found: " + request.projectId());
        }
        projectSecurityEvaluator.assertCanViewProject(project.getId(), actor);

        TestSuite suite = null;
        if (request.suiteId() != null) {
            suite = suiteRepository.findById(request.suiteId())
                    .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Suite not found: " + request.suiteId()));
            if (!suite.getProject().getId().equals(project.getId())) {
                throw new com.taskflow.exception.BusinessRuleException("Suite does not belong to project.");
            }
        }

        User creator = userRepository.getReferenceById(actor.getId());
        User assignee = request.assignedToId() != null ? userRepository.getReferenceById(request.assignedToId()) : null;

        TestRun run = new TestRun(project.getOrganization(), project, request.name().trim(), creator);
        run.setSuite(suite);
        run.setAssignedTo(assignee);
        runRepository.save(run);

        List<Long> caseIds = new ArrayList<>();
        if (suite != null) {
            caseIds = new ArrayList<>(suiteCaseRepository.findBySuiteId(suite.getId()).stream()
                    .map(sc -> sc.getTestCase().getId())
                    .toList());
        }
        if (request.testCaseIds() != null) {
            caseIds = new ArrayList<>(caseIds);
            for (Long cid : request.testCaseIds()) {
                if (!caseIds.contains(cid)) caseIds.add(cid);
            }
        }

        Set<Long> unique = new java.util.LinkedHashSet<>(caseIds);
        for (Long cid : unique) {
            TestCase tc = testCaseRepository.findById(cid)
                    .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + cid));
            if (!tc.getProject().getId().equals(project.getId())) {
                throw new com.taskflow.exception.BusinessRuleException("Test case does not belong to project.");
            }
            executionRepository.save(new TestExecution(run, tc));
        }

        return TestRunResponse.from(run);
    }

    private TestRunStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return TestRunStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new com.taskflow.exception.BusinessRuleException("Invalid test run status.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TestRunResponse> list(Long projectId, String status, Long assignedToId, Pageable pageable) {
        var actor = SecurityUtils.currentUser();
        TestRunStatus s = parseStatus(status);
        Page<TestRun> page = runRepository.searchTestRuns(actor.getOrganizationId(), projectId, s, assignedToId, pageable);
        return PagedResponse.from(page.map(TestRunResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public TestRunResponse getById(Long id) {
        var actor = SecurityUtils.currentUser();
        TestRun run = runRepository.findById(id)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test run not found: " + id));
        if (!run.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test run not found: " + id);
        }
        return TestRunResponse.from(run);
    }

    @Override
    @Transactional
    public TestRunResponse updateStatus(Long id, String status) {
        var actor = SecurityUtils.currentUser();
        TestRun run = runRepository.findById(id)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test run not found: " + id));
        if (!run.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test run not found: " + id);
        }
        // Organization membership alone is not enough for a mutation: QA/PM users
        // must also be able to view the run's project (admin passes this check).
        projectSecurityEvaluator.assertCanViewProject(run.getProject().getId(), actor);
        TestRunStatus newStatus = parseStatus(status);
        run.setStatus(newStatus);
        if (newStatus == TestRunStatus.IN_PROGRESS && run.getStartedAt() == null) {
            run.setStartedAt(Instant.now());
        }
        if (newStatus == TestRunStatus.COMPLETED) {
            run.setCompletedAt(Instant.now());
        }
        runRepository.save(run);
        return TestRunResponse.from(run);
    }
}
