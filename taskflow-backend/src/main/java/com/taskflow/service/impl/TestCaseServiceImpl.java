package com.taskflow.service.impl;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.test.CreateTestCaseRequest;
import com.taskflow.dto.test.TestCaseResponse;
import com.taskflow.dto.test.UpdateTestCaseRequest;
import com.taskflow.entity.Project;
import com.taskflow.entity.TestCase;
import com.taskflow.entity.TestStep;
import com.taskflow.entity.User;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TestCaseRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.TestCaseService;
import com.taskflow.util.SecurityUtils;
import com.taskflow.util.TokenHasher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestCaseServiceImpl implements TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;

    public TestCaseServiceImpl(TestCaseRepository testCaseRepository, ProjectRepository projectRepository,
                              UserRepository userRepository, ProjectSecurityEvaluator projectSecurityEvaluator) {
        this.testCaseRepository = testCaseRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
    }

    @Override
    @Transactional
    public TestCaseResponse create(CreateTestCaseRequest request) {
        var actor = SecurityUtils.currentUser();
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Project not found: " + request.projectId()));
        if (!project.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Project not found: " + request.projectId());
        }
        projectSecurityEvaluator.assertCanViewProject(project.getId(), actor);

        User creator = userRepository.getReferenceById(actor.getId());
        String key = "TC-" + TokenHasher.generateRawToken(6).toUpperCase();
        while (testCaseRepository.existsByTestKey(key)) {
            key = "TC-" + TokenHasher.generateRawToken(6).toUpperCase();
        }
        TestCase tc = new TestCase(project.getOrganization(), project, key, request.title().trim(), creator);
        tc.setDescription(request.description());
        tc.setPreconditions(request.preconditions());
        if (request.priority() != null) tc.setPriority(request.priority());
        if (request.steps() != null) {
            int order = 1;
            for (CreateTestCaseRequest.TestStepRequest s : request.steps()) {
                tc.getSteps().add(new TestStep(tc, order++, s.action().trim(), s.expectedResult().trim()));
            }
        }
        testCaseRepository.save(tc);
        return TestCaseResponse.from(tc);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TestCaseResponse> list(Long projectId, Boolean archived, String priority, String search, Pageable pageable) {
        var actor = SecurityUtils.currentUser();
        PriorityLevel p = (priority == null || priority.isBlank()) ? null : PriorityLevel.valueOf(priority);
        Page<TestCase> page = testCaseRepository.searchTestCases(actor.getOrganizationId(), projectId, archived, p, search, pageable);
        return PagedResponse.from(page.map(TestCaseResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public TestCaseResponse getById(Long id) {
        var actor = SecurityUtils.currentUser();
        TestCase tc = testCaseRepository.findById(id)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + id));
        if (!tc.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + id);
        }
        projectSecurityEvaluator.assertCanViewProject(tc.getProject().getId(), actor);
        return TestCaseResponse.from(tc);
    }

    @Override
    @Transactional
    public TestCaseResponse update(Long id, UpdateTestCaseRequest request) {
        var actor = SecurityUtils.currentUser();
        TestCase tc = testCaseRepository.findById(id)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + id));
        if (!tc.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + id);
        }
        projectSecurityEvaluator.assertCanViewProject(tc.getProject().getId(), actor);
        if (request.title() != null) tc.setTitle(request.title().trim());
        if (request.description() != null) tc.setDescription(request.description());
        if (request.preconditions() != null) tc.setPreconditions(request.preconditions());
        if (request.priority() != null) tc.setPriority(request.priority());
        if (request.archived() != null) tc.setArchived(request.archived());
        if (request.steps() != null) {
            tc.getSteps().clear();
            int order = 1;
            for (CreateTestCaseRequest.TestStepRequest s : request.steps()) {
                tc.getSteps().add(new TestStep(tc, order++, s.action().trim(), s.expectedResult().trim()));
            }
        }
        testCaseRepository.save(tc);
        return TestCaseResponse.from(tc);
    }

    @Override
    @Transactional
    public void archive(Long id) {
        var actor = SecurityUtils.currentUser();
        TestCase tc = testCaseRepository.findById(id)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + id));
        if (!tc.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + id);
        }
        projectSecurityEvaluator.assertCanViewProject(tc.getProject().getId(), actor);
        tc.setArchived(true);
        testCaseRepository.save(tc);
    }
}
