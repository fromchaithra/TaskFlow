package com.taskflow.service.impl;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.test.CreateTestSuiteRequest;
import com.taskflow.dto.test.UpdateTestSuiteRequest;
import com.taskflow.dto.test.TestSuiteResponse;
import com.taskflow.entity.Project;
import com.taskflow.entity.TestCase;
import com.taskflow.entity.TestSuite;
import com.taskflow.entity.TestSuiteCase;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TestCaseRepository;
import com.taskflow.repository.TestSuiteCaseRepository;
import com.taskflow.repository.TestSuiteRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.TestSuiteService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TestSuiteServiceImpl implements TestSuiteService {

    private final TestSuiteRepository suiteRepository;
    private final TestSuiteCaseRepository suiteCaseRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;

    public TestSuiteServiceImpl(TestSuiteRepository suiteRepository, TestSuiteCaseRepository suiteCaseRepository,
                               TestCaseRepository testCaseRepository, ProjectRepository projectRepository,
                               UserRepository userRepository, ProjectSecurityEvaluator projectSecurityEvaluator) {
        this.suiteRepository = suiteRepository;
        this.suiteCaseRepository = suiteCaseRepository;
        this.testCaseRepository = testCaseRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
    }

    @Override
    @Transactional
    public TestSuiteResponse create(CreateTestSuiteRequest request) {
        var actor = SecurityUtils.currentUser();
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Project not found: " + request.projectId()));
        if (!project.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Project not found: " + request.projectId());
        }
        projectSecurityEvaluator.assertCanViewProject(project.getId(), actor);
        TestSuite suite = new TestSuite(project.getOrganization(), project, request.name().trim(), userRepository.getReferenceById(actor.getId()));
        suite.setDescription(request.description());
        suiteRepository.save(suite);
        return TestSuiteResponse.from(suite);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TestSuiteResponse> list(Long projectId, Boolean archived, String search, Pageable pageable) {
        var actor = SecurityUtils.currentUser();
        Page<TestSuite> page = suiteRepository.searchTestSuites(actor.getOrganizationId(), projectId, archived, search, pageable);
        return PagedResponse.from(page.map(TestSuiteResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public TestSuiteResponse getById(Long id) {
        var actor = SecurityUtils.currentUser();
        TestSuite suite = suiteRepository.findById(id)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test suite not found: " + id));
        if (!suite.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test suite not found: " + id);
        }
        projectSecurityEvaluator.assertCanViewProject(suite.getProject().getId(), actor);
        return TestSuiteResponse.from(suite);
    }

    @Override
    @Transactional
    public TestSuiteResponse update(Long id, UpdateTestSuiteRequest request) {
        var actor = SecurityUtils.currentUser();
        TestSuite suite = suiteRepository.findById(id)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test suite not found: " + id));
        if (!suite.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test suite not found: " + id);
        }
        projectSecurityEvaluator.assertCanViewProject(suite.getProject().getId(), actor);
        if (request.name() != null) suite.setName(request.name().trim());
        if (request.description() != null) suite.setDescription(request.description());
        if (request.archived() != null) suite.setArchived(request.archived());
        suiteRepository.save(suite);
        return TestSuiteResponse.from(suite);
    }

    @Override
    @Transactional
    public void archive(Long id) {
        var actor = SecurityUtils.currentUser();
        TestSuite suite = suiteRepository.findById(id)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test suite not found: " + id));
        if (!suite.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test suite not found: " + id);
        }
        projectSecurityEvaluator.assertCanViewProject(suite.getProject().getId(), actor);
        suite.setArchived(true);
        suiteRepository.save(suite);
    }

    @Override
    @Transactional
    public void addCase(Long suiteId, Long testCaseId) {
        var actor = SecurityUtils.currentUser();
        TestSuite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test suite not found: " + suiteId));
        TestCase tc = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test case not found: " + testCaseId));
        if (!suite.getOrganization().getId().equals(actor.getOrganizationId()) || !tc.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Not found");
        }
        if (!suite.getProject().getId().equals(tc.getProject().getId())) {
            throw new com.taskflow.exception.BusinessRuleException("Test case and suite must belong to the same project.");
        }
        projectSecurityEvaluator.assertCanViewProject(suite.getProject().getId(), actor);
        if (suiteCaseRepository.existsById(new TestSuiteCase.PK(suiteId, testCaseId))) {
            return; // already present
        }
        suiteCaseRepository.save(new TestSuiteCase(suite, tc));
    }

    @Override
    @Transactional
    public void removeCase(Long suiteId, Long testCaseId) {
        var actor = SecurityUtils.currentUser();
        TestSuite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Test suite not found: " + suiteId));
        if (!suite.getOrganization().getId().equals(actor.getOrganizationId())) {
            throw new com.taskflow.exception.ResourceNotFoundException("Test suite not found: " + suiteId);
        }
        projectSecurityEvaluator.assertCanViewProject(suite.getProject().getId(), actor);
        suiteCaseRepository.deleteById(new TestSuiteCase.PK(suiteId, testCaseId));
    }
}
