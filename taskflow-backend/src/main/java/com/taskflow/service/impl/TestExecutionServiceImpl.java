package com.taskflow.service.impl;

import com.taskflow.dto.test.TestExecutionResponse;
import com.taskflow.dto.test.UpdateTestExecutionRequest;
import com.taskflow.entity.Bug;
import com.taskflow.entity.Project;
import com.taskflow.entity.TestExecution;
import com.taskflow.entity.TestRun;
import com.taskflow.entity.User;
import com.taskflow.enums.TestExecutionResult;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.TestExecutionRepository;
import com.taskflow.repository.TestCaseRepository;
import com.taskflow.repository.TestRunRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.BugService;
import com.taskflow.service.TestExecutionService;
import com.taskflow.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TestExecutionServiceImpl implements TestExecutionService {

    private final TestExecutionRepository executionRepository;
    private final TestRunRepository runRepository;
    private final TestCaseRepository testCaseRepository;
    private final BugRepository bugRepository;
    private final UserRepository userRepository;
    private final BugService bugService;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;

    public TestExecutionServiceImpl(TestExecutionRepository executionRepository, TestRunRepository runRepository,
                                   TestCaseRepository testCaseRepository, BugRepository bugRepository, UserRepository userRepository, BugService bugService,
                                   ProjectSecurityEvaluator projectSecurityEvaluator) {
        this.executionRepository = executionRepository;
        this.runRepository = runRepository;
        this.testCaseRepository = testCaseRepository;
        this.bugRepository = bugRepository;
        this.userRepository = userRepository;
        this.bugService = bugService;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
    }

    @Override
    @Transactional
    public TestExecutionResponse recordResult(Long runId, Long executionId, UpdateTestExecutionRequest request) {
        var actor = SecurityUtils.currentUser();
        TestExecution te = executionRepository.findById(executionId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Execution not found: " + executionId));
        if (!te.getRun().getId().equals(runId)) {
            throw new com.taskflow.exception.ResourceNotFoundException("Execution not found: " + executionId);
        }
        if (!te.getRun().getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Execution not found: " + executionId);
        }
        projectSecurityEvaluator.assertCanViewProject(te.getRun().getProject().getId(), actor);

        if (request.result() != null) te.setResult(request.result());
        te.setActualResult(request.actualResult());
        te.setNotes(request.notes());
        te.setExecutedBy(userRepository.getReferenceById(actor.getId()));
        te.setExecutedAt(Instant.now());
        if (request.bugId() != null) {
            te.setBug(bugRepository.findById(request.bugId()).orElse(null));
        }
        executionRepository.save(te);
        return TestExecutionResponse.from(te);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestExecutionResponse> listByRun(Long runId) {
        var actor = SecurityUtils.currentUser();
        TestRun run = runRepository.findById(runId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test run not found: " + runId));
        if (!run.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test run not found: " + runId);
        }
        projectSecurityEvaluator.assertCanViewProject(run.getProject().getId(), actor);
        return executionRepository.findByRunIdOrderByTestCaseIdAsc(runId).stream()
                .map(TestExecutionResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<TestExecutionResponse> listByTestCase(Long testCaseId) {
        var actor = SecurityUtils.currentUser();
        com.taskflow.entity.TestCase tc = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + testCaseId));
        if (!tc.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + testCaseId);
        }
        projectSecurityEvaluator.assertCanViewProject(tc.getProject().getId(), actor);
        return executionRepository.findByTestCaseIdOrderByExecutedAtDesc(testCaseId).stream()
                .map(TestExecutionResponse::from)
                .toList();
    }
    @Override
    @Transactional
    public TestExecutionResponse createBugFromFailure(Long runId, Long executionId) {
        var actor = SecurityUtils.currentUser();
        TestExecution te = executionRepository.findById(executionId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Execution not found: " + executionId));
        if (!te.getRun().getId().equals(runId)) {
            throw new com.taskflow.exception.ResourceNotFoundException("Execution not found: " + executionId);
        }
        if (!te.getRun().getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Execution not found: " + executionId);
        }
        projectSecurityEvaluator.assertCanViewProject(te.getRun().getProject().getId(), actor);

        if (te.getResult() != TestExecutionResult.FAIL) {
            throw new com.taskflow.exception.BusinessRuleException("Bug can only be created from a failed execution.");
        }
        if (te.getBug() != null) {
            return TestExecutionResponse.from(te);
        }

        com.taskflow.dto.bug.CreateBugRequest bugRequest = new com.taskflow.dto.bug.CreateBugRequest(
                te.getRun().getProject().getId(),
                null,
                "Bug from test " + te.getTestCase().getTestKey() + ": " + te.getTestCase().getTitle(),
                te.getActualResult() != null ? te.getActualResult() : "Failed test execution.",
                null,
                com.taskflow.enums.BugSeverity.MEDIUM,
                com.taskflow.enums.PriorityLevel.MEDIUM,
                null
        );
        com.taskflow.dto.bug.BugDetailResponse created = bugService.createBug(bugRequest);
        // link bug to execution
        com.taskflow.entity.Bug bug = bugRepository.findById(created.id()).orElse(null);
        te.setBug(bug);
        executionRepository.save(te);
        return TestExecutionResponse.from(te);
    }
}
