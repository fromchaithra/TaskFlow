package com.taskflow.service.impl;

import com.taskflow.dto.bug.AssignBugRequest;
import com.taskflow.dto.bug.BugDetailResponse;
import com.taskflow.dto.bug.BugHistoryResponse;
import com.taskflow.dto.bug.BugSummaryResponse;
import com.taskflow.dto.bug.CreateBugRequest;
import com.taskflow.dto.bug.ReopenBugRequest;
import com.taskflow.dto.bug.UpdateBugRequest;
import com.taskflow.dto.bug.UpdateBugSeverityRequest;
import com.taskflow.dto.bug.UpdateBugStatusRequest;
import com.taskflow.dto.bug.VerifyBugRequest;
import com.taskflow.dto.common.PagedResponse;
import com.taskflow.entity.Bug;
import com.taskflow.entity.BugComment;
import com.taskflow.entity.BugHistory;
import com.taskflow.entity.Project;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.NotificationType;
import com.taskflow.enums.ProjectStatus;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.InvalidWorkflowTransitionException;
import com.taskflow.exception.ResourceNotFoundException;
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
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.BugService;
import com.taskflow.service.NotificationDispatcher;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class BugServiceImpl implements BugService {

    private static final Map<BugStatus, Set<BugStatus>> ALLOWED_STATUS_TRANSITIONS = buildStatusTransitions();

    private final BugRepository bugRepository;
    private final BugHistoryRepository bugHistoryRepository;
    private final BugCommentRepository bugCommentRepository;
    private final BugAttachmentRepository bugAttachmentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;
    private final AuditLogWriter auditLogWriter;
    private final NotificationDispatcher notificationDispatcher;

    public BugServiceImpl(
            BugRepository bugRepository,
            BugHistoryRepository bugHistoryRepository,
            BugCommentRepository bugCommentRepository,
            BugAttachmentRepository bugAttachmentRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            ProjectSecurityEvaluator projectSecurityEvaluator,
            AuditLogWriter auditLogWriter,
            NotificationDispatcher notificationDispatcher
    ) {
        this.bugRepository = bugRepository;
        this.bugHistoryRepository = bugHistoryRepository;
        this.bugCommentRepository = bugCommentRepository;
        this.bugAttachmentRepository = bugAttachmentRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
        this.auditLogWriter = auditLogWriter;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Override
    @Transactional
    public BugDetailResponse createBug(CreateBugRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(request.projectId(), actorDetails);

        Project project = projectRepository.findByIdForUpdate(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + request.projectId()));

        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot report bugs in an archived project.");
        }

        User reporter = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        Task relatedTask = resolveProjectTask(request.relatedTaskId(), project);
        User assignee = resolveProjectAssignee(request.assigneeId(), project);

        String bugKey = project.nextBugKey();
        Bug bug = new Bug(
                project,
                bugKey,
                relatedTask,
                request.title().trim(),
                request.description().trim(),
                normalizeOptionalText(request.stepsToReproduce()),
                request.severity(),
                request.priority(),
                reporter,
                assignee
        );

        projectRepository.save(project);
        Bug saved = bugRepository.save(bug);

        bugHistoryRepository.save(new BugHistory(
                saved,
                reporter,
                "REPORTED",
                null,
                saved.getStatus().name(),
                "Bug " + saved.getBugKey() + " reported"
        ));

        auditLogWriter.record(
                project.getOrganization(),
                reporter,
                project,
                AuditAction.BUG_REPORTED,
                "BUG",
                saved.getId(),
                null,
                buildBugSnapshot(saved)
        );

        if (assignee != null && !assignee.getId().equals(reporter.getId())) {
            notificationDispatcher.notifyUser(
                    assignee,
                    NotificationType.BUG_ASSIGNED,
                    "Bug assigned: " + saved.getBugKey(),
                    reporter.getFullName() + " assigned you bug '" + saved.getTitle() + "' (" + saved.getSeverity() + ").",
                    "BUG",
                    saved.getId()
            );
        }

        return toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<BugSummaryResponse> listBugs(
            Long projectId,
            Long assigneeId,
            BugSeverity severity,
            BugStatus status,
            String search,
            Pageable pageable
    ) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        if (projectId != null) {
            projectSecurityEvaluator.assertCanViewProject(projectId, viewer);
        }

        Page<Bug> bugPage = bugRepository.searchAuthorizedBugs(
                viewer.getOrganizationId(),
                viewer.getId(),
                viewer.isAdmin(),
                projectId,
                assigneeId,
                severity,
                status,
                normalizeSearch(search),
                pageable
        );
        return PagedResponse.from(bugPage.map(BugSummaryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public BugDetailResponse getBugById(Long bugId) {
        CustomUserDetails viewer = SecurityUtils.currentUser();
        Bug bug = findBugOrThrow(bugId);
        projectSecurityEvaluator.assertCanViewProject(bug.getProject().getId(), viewer);
        return toDetailResponse(bug);
    }

    @Override
    @Transactional
    public BugDetailResponse updateBug(Long bugId, UpdateBugRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();

        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        if (request.expectedVersion() != null && !request.expectedVersion().equals(bug.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Bug.class, bugId);
        }

        boolean isManagerOrAdmin = canManageBugProject(project, actorDetails);
        boolean isQa = actorDetails.getPrimaryRole() == RoleName.QA_TESTER;
        boolean isAssignee = isBugAssignee(bug, actorDetails);

        if (!isManagerOrAdmin) {
            if (isQa) {
                Long currentAssigneeId = bug.getAssignee() != null ? bug.getAssignee().getId() : null;
                if (request.assigneeId() != null && !Objects.equals(request.assigneeId(), currentAssigneeId)) {
                    throw new UnauthorizedResourceAccessException(
                            "Only an admin or the assigned project manager can reassign bugs."
                    );
                }
            } else if (actorDetails.getPrimaryRole() == RoleName.DEVELOPER) {
                if (!isAssignee) {
                    throw new UnauthorizedResourceAccessException(
                            "Developers can only update bugs assigned to them."
                    );
                }
                enforceDeveloperFieldRestrictions(bug, request);
            } else {
                throw new UnauthorizedResourceAccessException("You are not authorized to update this bug.");
            }
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        Map<String, Object> previousSnapshot = buildBugSnapshot(bug);
        List<BugHistory> historyEntries = new ArrayList<>();
        String remarks = normalizeOptionalText(request.remarks());

        BugSeverity previousSeverity = bug.getSeverity();
        boolean severityChanged = false;
        User previousAssignee = bug.getAssignee();
        User nextAssignee = previousAssignee;

        if (isManagerOrAdmin || isQa) {
            if (request.title() != null) {
                String nextTitle = request.title().trim();
                if (nextTitle.length() < 3) {
                    throw new BusinessRuleException("Bug title must be at least 3 characters.");
                }
                if (!nextTitle.equals(bug.getTitle())) {
                    historyEntries.add(new BugHistory(bug, actor, "title", bug.getTitle(), nextTitle, remarks));
                    bug.setTitle(nextTitle);
                }
            }

            if (request.severity() != null && request.severity() != bug.getSeverity()) {
                historyEntries.add(new BugHistory(
                        bug, actor, "severity", bug.getSeverity().name(), request.severity().name(), remarks
                ));
                bug.setSeverity(request.severity());
                severityChanged = true;
            }

            if (request.priority() != null && request.priority() != bug.getPriority()) {
                historyEntries.add(new BugHistory(
                        bug, actor, "priority", bug.getPriority().name(), request.priority().name(), remarks
                ));
                bug.setPriority(request.priority());
            }

            if (request.relatedTaskId() != null) {
                Long currentTaskId = bug.getRelatedTask() != null ? bug.getRelatedTask().getId() : null;
                if (!request.relatedTaskId().equals(currentTaskId)) {
                    Task nextTask = resolveProjectTask(request.relatedTaskId(), project);
                    historyEntries.add(new BugHistory(
                            bug,
                            actor,
                            "related_task",
                            bug.getRelatedTask() != null ? bug.getRelatedTask().getTaskKey() : "None",
                            nextTask.getTaskKey(),
                            remarks
                    ));
                    bug.setRelatedTask(nextTask);
                }
            }
        }

        if (isManagerOrAdmin && request.assigneeId() != null) {
            Long currentAssigneeId = previousAssignee != null ? previousAssignee.getId() : null;
            if (!request.assigneeId().equals(currentAssigneeId)) {
                nextAssignee = resolveProjectAssignee(request.assigneeId(), project);
                historyEntries.add(new BugHistory(
                        bug, actor, "assignee", formatUser(previousAssignee), formatUser(nextAssignee), remarks
                ));
                bug.setAssignee(nextAssignee);
                if (bug.getStatus() == BugStatus.OPEN) {
                    historyEntries.add(new BugHistory(
                            bug, actor, "status", BugStatus.OPEN.name(), BugStatus.ASSIGNED.name(), "Auto-assigned"
                    ));
                    bug.setStatus(BugStatus.ASSIGNED);
                }
            }
        }

        if (request.description() != null) {
            String nextDesc = request.description().trim();
            if (nextDesc.length() < 5) {
                throw new BusinessRuleException("Bug description must be at least 5 characters.");
            }
            if (!nextDesc.equals(bug.getDescription())) {
                historyEntries.add(new BugHistory(
                        bug,
                        actor,
                        "description",
                        truncateForHistory(bug.getDescription()),
                        truncateForHistory(nextDesc),
                        remarks
                ));
                bug.setDescription(nextDesc);
            }
        }

        if (request.stepsToReproduce() != null) {
            String nextSteps = normalizeOptionalText(request.stepsToReproduce());
            if (!Objects.equals(nextSteps, bug.getStepsToReproduce())) {
                historyEntries.add(new BugHistory(
                        bug,
                        actor,
                        "steps_to_reproduce",
                        truncateForHistory(bug.getStepsToReproduce()),
                        truncateForHistory(nextSteps),
                        remarks
                ));
                bug.setStepsToReproduce(nextSteps);
            }
        }

        Bug saved = bugRepository.save(bug);
        if (!historyEntries.isEmpty()) {
            bugHistoryRepository.saveAll(historyEntries);
            auditLogWriter.record(
                    project.getOrganization(),
                    actor,
                    project,
                    AuditAction.BUG_UPDATED,
                    "BUG",
                    saved.getId(),
                    previousSnapshot,
                    buildBugSnapshot(saved)
            );
        }

        if (severityChanged) {
            auditLogWriter.record(
                    project.getOrganization(),
                    actor,
                    project,
                    AuditAction.BUG_SEVERITY_CHANGED,
                    "BUG",
                    saved.getId(),
                    Map.of("bugKey", saved.getBugKey(), "severity", previousSeverity.name()),
                    Map.of("bugKey", saved.getBugKey(), "severity", saved.getSeverity().name())
            );
        }

        if (nextAssignee != null
                && !Objects.equals(nextAssignee.getId(), previousAssignee != null ? previousAssignee.getId() : null)
                && !nextAssignee.getId().equals(actor.getId())) {
            notificationDispatcher.notifyUser(
                    nextAssignee,
                    NotificationType.BUG_ASSIGNED,
                    "Bug assigned: " + saved.getBugKey(),
                    actor.getFullName() + " assigned you bug '" + saved.getTitle() + "'.",
                    "BUG",
                    saved.getId()
            );
        }

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public BugDetailResponse updateBugStatus(Long bugId, UpdateBugStatusRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();

        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        BugStatus currentStatus = bug.getStatus();
        BugStatus targetStatus = request.status();

        if (currentStatus == targetStatus) {
            return toDetailResponse(bug);
        }

        enforceStatusTransitionPermissions(bug, currentStatus, targetStatus, actorDetails);
        validateStatusTransition(currentStatus, targetStatus);

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        return applyStatusTransition(
                bug,
                currentStatus,
                targetStatus,
                actor,
                normalizeOptionalText(request.remarks()),
                resolveStatusAuditAction(targetStatus)
        );
    }

    @Override
    @Transactional
    public BugDetailResponse assignBug(Long bugId, AssignBugRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();

        projectSecurityEvaluator.assertCanManageProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        User previousAssignee = bug.getAssignee();
        User nextAssignee = resolveProjectAssignee(request.assigneeId(), project);

        Long prevId = previousAssignee != null ? previousAssignee.getId() : null;
        Long nextId = nextAssignee != null ? nextAssignee.getId() : null;
        if (Objects.equals(prevId, nextId)) {
            return toDetailResponse(bug);
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        String remarks = normalizeOptionalText(request.remarks());
        List<BugHistory> historyEntries = new ArrayList<>();
        historyEntries.add(new BugHistory(
                bug, actor, "assignee", formatUser(previousAssignee), formatUser(nextAssignee), remarks
        ));

        bug.setAssignee(nextAssignee);
        if (nextAssignee != null && bug.getStatus() == BugStatus.OPEN) {
            historyEntries.add(new BugHistory(
                    bug, actor, "status", BugStatus.OPEN.name(), BugStatus.ASSIGNED.name(), "Assigned to developer"
            ));
            bug.setStatus(BugStatus.ASSIGNED);
        } else if (nextAssignee == null && bug.getStatus() == BugStatus.ASSIGNED) {
            historyEntries.add(new BugHistory(
                    bug, actor, "status", BugStatus.ASSIGNED.name(), BugStatus.OPEN.name(), "Bug unassigned"
            ));
            bug.setStatus(BugStatus.OPEN);
        }

        Bug saved = bugRepository.save(bug);
        bugHistoryRepository.saveAll(historyEntries);

        Map<String, Object> prevMap = new HashMap<>();
        prevMap.put("bugKey", saved.getBugKey());
        prevMap.put("assigneeId", prevId);

        Map<String, Object> nextMap = new HashMap<>();
        nextMap.put("bugKey", saved.getBugKey());
        nextMap.put("assigneeId", nextId);
        nextMap.put("status", saved.getStatus().name());

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.BUG_ASSIGNED,
                "BUG",
                saved.getId(),
                prevMap,
                nextMap
        );

        if (nextAssignee != null && !nextAssignee.getId().equals(actor.getId())) {
            notificationDispatcher.notifyUser(
                    nextAssignee,
                    NotificationType.BUG_ASSIGNED,
                    "Bug assigned: " + saved.getBugKey(),
                    actor.getFullName() + " assigned you bug '" + saved.getTitle() + "' (" + saved.getSeverity() + ").",
                    "BUG",
                    saved.getId()
            );
        }

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public BugDetailResponse updateBugSeverity(Long bugId, UpdateBugSeverityRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();

        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        if (!canManageBugProject(project, actorDetails) && actorDetails.getPrimaryRole() != RoleName.QA_TESTER) {
            throw new UnauthorizedResourceAccessException(
                    "Only an admin, project manager, or QA tester can change bug severity."
            );
        }

        BugSeverity previousSeverity = bug.getSeverity();
        BugSeverity nextSeverity = request.severity();
        if (previousSeverity == nextSeverity) {
            return toDetailResponse(bug);
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        bug.setSeverity(nextSeverity);
        Bug saved = bugRepository.save(bug);

        String remarks = normalizeOptionalText(request.remarks());
        bugHistoryRepository.save(new BugHistory(
                saved, actor, "severity", previousSeverity.name(), nextSeverity.name(), remarks
        ));

        Map<String, Object> prevMap = new HashMap<>();
        prevMap.put("bugKey", saved.getBugKey());
        prevMap.put("severity", previousSeverity.name());

        Map<String, Object> nextMap = new HashMap<>();
        nextMap.put("bugKey", saved.getBugKey());
        nextMap.put("severity", nextSeverity.name());
        if (remarks != null) {
            nextMap.put("remarks", remarks);
        }

        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.BUG_SEVERITY_CHANGED,
                "BUG",
                saved.getId(),
                prevMap,
                nextMap
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public BugDetailResponse verifyBug(Long bugId, VerifyBugRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();

        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        if (!canVerifyOrReopenBug(project, actorDetails)) {
            throw new UnauthorizedResourceAccessException(
                    "Only a QA tester, assigned project manager, or admin can verify bugs."
            );
        }

        if (bug.getStatus() != BugStatus.RESOLVED) {
            throw new InvalidWorkflowTransitionException(
                    "Only RESOLVED bugs can undergo QA verification. Current status: " + bug.getStatus() + "."
            );
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        boolean approved = Boolean.TRUE.equals(request.approved());
        BugStatus targetStatus = approved ? BugStatus.VERIFIED : BugStatus.REOPENED;
        AuditAction action = approved ? AuditAction.BUG_VERIFIED : AuditAction.BUG_REOPENED;
        String defaultRemark = approved ? "Fix verified by QA" : "Fix rejected during QA verification";
        String remarks = normalizeOptionalText(request.remarks()) != null
                ? request.remarks().trim()
                : defaultRemark;

        return applyStatusTransition(bug, BugStatus.RESOLVED, targetStatus, actor, remarks, action);
    }

    @Override
    @Transactional
    public BugDetailResponse reopenBug(Long bugId, ReopenBugRequest request) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();

        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        if (!canVerifyOrReopenBug(project, actorDetails)) {
            throw new UnauthorizedResourceAccessException(
                    "Only a QA tester, assigned project manager, or admin can reopen bugs."
            );
        }

        BugStatus currentStatus = bug.getStatus();
        if (currentStatus != BugStatus.RESOLVED && currentStatus != BugStatus.VERIFIED) {
            throw new InvalidWorkflowTransitionException(
                    "Only RESOLVED or VERIFIED bugs can be reopened. Current status: " + currentStatus + "."
            );
        }

        User actor = userRepository.findById(actorDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        return applyStatusTransition(
                bug,
                currentStatus,
                BugStatus.REOPENED,
                actor,
                request.reason().trim(),
                AuditAction.BUG_REOPENED
        );
    }

    @Override
    @Transactional
    public void deleteBug(Long bugId) {
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();

        projectSecurityEvaluator.assertCanManageProject(project.getId(), actorDetails);
        ensureProjectMutable(project);

        Map<String, Object> snapshot = buildBugSnapshot(bug);
        Long deletedId = bug.getId();

        bugHistoryRepository.deleteByBugId(deletedId);
        bugCommentRepository.deleteByBugId(deletedId);
        bugAttachmentRepository.deleteByBugId(deletedId);
        bugRepository.delete(bug);

        User actor = userRepository.getReferenceById(actorDetails.getId());
        auditLogWriter.record(
                project.getOrganization(),
                actor,
                project,
                AuditAction.BUG_DELETED,
                "BUG",
                deletedId,
                snapshot,
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.taskflow.dto.bug.BugCommentResponse> listComments(Long bugId) {
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);
        return bugCommentRepository.findByBugIdOrderByCreatedAtAsc(bugId)
                .stream()
                .map(com.taskflow.dto.bug.BugCommentResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public com.taskflow.dto.bug.BugCommentResponse addComment(Long bugId, String content) {
        if (content == null || content.isBlank()) {
            throw new com.taskflow.exception.BusinessRuleException("Comment content is required.");
        }
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);

        User author = userRepository.getReferenceById(actorDetails.getId());
        BugComment comment = new BugComment(bug, author, content.trim());
        bugCommentRepository.save(comment);
        return com.taskflow.dto.bug.BugCommentResponse.from(comment);
    }

    @Override
    @Transactional
    public com.taskflow.dto.bug.BugCommentResponse editComment(Long bugId, Long commentId, String content) {
        if (content == null || content.isBlank()) {
            throw new com.taskflow.exception.BusinessRuleException("Comment content is required.");
        }
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);

        BugComment comment = bugCommentRepository.findDetailById(commentId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Comment not found: " + commentId));
        if (!comment.getBug().getId().equals(bugId)) {
            throw new com.taskflow.exception.ResourceNotFoundException("Comment not found: " + commentId);
        }
        if (!comment.getAuthor().getId().equals(actorDetails.getId())) {
            throw new com.taskflow.exception.UnauthorizedResourceAccessException("You cannot edit this comment.");
        }
        comment.editContent(content.trim());
        bugCommentRepository.save(comment);
        return com.taskflow.dto.bug.BugCommentResponse.from(comment);
    }
    @Override
    @Transactional
    public void deleteComment(Long bugId, Long commentId) {
        Bug bug = findBugOrThrow(bugId);
        Project project = bug.getProject();
        CustomUserDetails actorDetails = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(project.getId(), actorDetails);

        BugComment comment = bugCommentRepository.findDetailById(commentId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Comment not found: " + commentId));
        if (!comment.getBug().getId().equals(bugId)) {
            throw new com.taskflow.exception.ResourceNotFoundException("Comment not found: " + commentId);
        }
        boolean isAuthor = comment.getAuthor().getId().equals(actorDetails.getId());
        boolean isManager = actorDetails.isAdmin() || actorDetails.getPrimaryRole() == com.taskflow.enums.RoleName.PROJECT_MANAGER;
        if (!isAuthor && !isManager) {
            throw new com.taskflow.exception.UnauthorizedResourceAccessException("You cannot delete this comment.");
        }
        bugCommentRepository.delete(comment);
    }

    private BugDetailResponse applyStatusTransition(
            Bug bug,
            BugStatus previousStatus,
            BugStatus targetStatus,
            User actor,
            String remarks,
            AuditAction auditAction
    ) {
        bug.setStatus(targetStatus);
        Bug saved = bugRepository.save(bug);

        bugHistoryRepository.save(new BugHistory(
                saved, actor, "status", previousStatus.name(), targetStatus.name(), remarks
        ));

        Map<String, Object> prevMap = new HashMap<>();
        prevMap.put("bugKey", saved.getBugKey());
        prevMap.put("status", previousStatus.name());

        Map<String, Object> nextMap = new HashMap<>();
        nextMap.put("bugKey", saved.getBugKey());
        nextMap.put("status", targetStatus.name());
        if (remarks != null) {
            nextMap.put("remarks", remarks);
        }

        auditLogWriter.record(
                saved.getProject().getOrganization(),
                actor,
                saved.getProject(),
                auditAction,
                "BUG",
                saved.getId(),
                prevMap,
                nextMap
        );

        dispatchBugStatusNotifications(saved, targetStatus, actor, remarks);
        return toDetailResponse(saved);
    }

    private void dispatchBugStatusNotifications(Bug bug, BugStatus targetStatus, User actor, String remarks) {
        if (targetStatus == BugStatus.RESOLVED) {
            User reporter = bug.getReporter();
            if (reporter != null && !reporter.getId().equals(actor.getId())) {
                notificationDispatcher.notifyUser(
                        reporter,
                        NotificationType.BUG_RESOLVED,
                        "Bug ready for QA verification: " + bug.getBugKey(),
                        actor.getFullName() + " marked '" + bug.getTitle() + "' as RESOLVED.",
                        "BUG",
                        bug.getId()
                );
            }
        } else if (targetStatus == BugStatus.VERIFIED) {
            User assignee = bug.getAssignee();
            if (assignee != null && !assignee.getId().equals(actor.getId())) {
                notificationDispatcher.notifyUser(
                        assignee,
                        NotificationType.BUG_VERIFIED,
                        "Bug fix verified: " + bug.getBugKey(),
                        actor.getFullName() + " verified the fix for '" + bug.getTitle() + "'.",
                        "BUG",
                        bug.getId()
                );
            }
        } else if (targetStatus == BugStatus.REOPENED) {
            User assignee = bug.getAssignee();
            if (assignee != null && !assignee.getId().equals(actor.getId())) {
                String suffix = remarks != null ? " Reason: " + remarks : "";
                notificationDispatcher.notifyUser(
                        assignee,
                        NotificationType.BUG_REOPENED,
                        "Bug reopened: " + bug.getBugKey(),
                        actor.getFullName() + " reopened '" + bug.getTitle() + "'." + suffix,
                        "BUG",
                        bug.getId()
                );
            }
        }
    }

    private Bug findBugOrThrow(Long bugId) {
        return bugRepository.findDetailById(bugId)
                .orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + bugId));
    }

    private boolean canManageBugProject(Project project, CustomUserDetails user) {
        if (!project.getOrganization().getId().equals(user.getOrganizationId())) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        return user.getPrimaryRole() == RoleName.PROJECT_MANAGER
                && project.getManager().getId().equals(user.getId());
    }

    private boolean canVerifyOrReopenBug(Project project, CustomUserDetails user) {
        return canManageBugProject(project, user) || user.getPrimaryRole() == RoleName.QA_TESTER;
    }

    private boolean isBugAssignee(Bug bug, CustomUserDetails user) {
        return bug.getAssignee() != null && bug.getAssignee().getId().equals(user.getId());
    }

    private void ensureProjectMutable(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot modify bugs in an archived project.");
        }
    }

    private void enforceDeveloperFieldRestrictions(Bug bug, UpdateBugRequest request) {
        boolean attemptedRestricted =
                (request.title() != null && !request.title().trim().equals(bug.getTitle()))
                        || (request.severity() != null && request.severity() != bug.getSeverity())
                        || (request.priority() != null && request.priority() != bug.getPriority())
                        || (request.relatedTaskId() != null && !Objects.equals(
                                request.relatedTaskId(),
                                bug.getRelatedTask() != null ? bug.getRelatedTask().getId() : null))
                        || (request.assigneeId() != null && !Objects.equals(
                                request.assigneeId(),
                                bug.getAssignee() != null ? bug.getAssignee().getId() : null));

        if (attemptedRestricted) {
            throw new UnauthorizedResourceAccessException(
                    "Developers can only update description and steps to reproduce on their assigned bugs."
            );
        }
    }

    private void enforceStatusTransitionPermissions(
            Bug bug,
            BugStatus current,
            BugStatus target,
            CustomUserDetails actor
    ) {
        if (canManageBugProject(bug.getProject(), actor)) {
            return;
        }

        RoleName role = actor.getPrimaryRole();
        boolean isAssignee = isBugAssignee(bug, actor);

        if (role == RoleName.DEVELOPER) {
            if (!isAssignee) {
                throw new UnauthorizedResourceAccessException(
                        "Developers can only update status on bugs assigned to them."
                );
            }
            if (target == BugStatus.VERIFIED || target == BugStatus.REOPENED || current == BugStatus.VERIFIED) {
                throw new UnauthorizedResourceAccessException(
                        "Developers cannot verify or reopen bugs. QA verification is required."
                );
            }
            return;
        }

        if (role == RoleName.QA_TESTER) {
            boolean isQaVerificationOrReopen =
                    (current == BugStatus.RESOLVED && (target == BugStatus.VERIFIED || target == BugStatus.REOPENED))
                            || (current == BugStatus.VERIFIED && target == BugStatus.REOPENED);
            if (isQaVerificationOrReopen || isAssignee) {
                return;
            }
            throw new UnauthorizedResourceAccessException(
                    "QA testers can only verify RESOLVED bugs, reopen RESOLVED/VERIFIED bugs, or transition bugs assigned to them."
            );
        }

        throw new UnauthorizedResourceAccessException("You are not authorized to change this bug's status.");
    }

    private void validateStatusTransition(BugStatus current, BugStatus target) {
        Set<BugStatus> allowed = ALLOWED_STATUS_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidWorkflowTransitionException(
                    "Invalid bug status transition from " + current + " to " + target + "."
            );
        }
    }

    private AuditAction resolveStatusAuditAction(BugStatus targetStatus) {
        return switch (targetStatus) {
            case VERIFIED -> AuditAction.BUG_VERIFIED;
            case REOPENED -> AuditAction.BUG_REOPENED;
            default -> AuditAction.BUG_STATUS_CHANGED;
        };
    }

    private User resolveProjectAssignee(Long assigneeId, Project project) {
        if (assigneeId == null) {
            return null;
        }
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignee not found: " + assigneeId));
        if (!assignee.getOrganization().getId().equals(project.getOrganization().getId())) {
            throw new UnauthorizedResourceAccessException("Assignee does not belong to your organization.");
        }
        if (!assignee.isActive()) {
            throw new BusinessRuleException("Cannot assign a bug to an inactive employee.");
        }
        boolean isMember = project.getManager().getId().equals(assigneeId)
                || projectMemberRepository.existsByProjectIdAndUserId(project.getId(), assigneeId);
        if (!isMember) {
            throw new BusinessRuleException(
                    "User " + assignee.getEmail() + " is not a member of project " + project.getProjectKey() + "."
            );
        }
        return assignee;
    }

    private Task resolveProjectTask(Long taskId, Project project) {
        if (taskId == null) {
            return null;
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Related task not found: " + taskId));
        if (!task.getProject().getId().equals(project.getId())) {
            throw new BusinessRuleException(
                    "Related task " + task.getTaskKey() + " does not belong to project " + project.getProjectKey() + "."
            );
        }
        return task;
    }

    private BugDetailResponse toDetailResponse(Bug bug) {
        Long bugId = bug.getId();
        long commentCount = bugCommentRepository.countByBugId(bugId);
        long attachmentCount = bugAttachmentRepository.countByBugId(bugId);
        List<BugHistoryResponse> history = bugHistoryRepository.findByBugIdOrderByCreatedAtDesc(bugId)
                .stream()
                .map(BugHistoryResponse::from)
                .toList();
        return BugDetailResponse.from(bug, commentCount, attachmentCount, history);
    }

    private Map<String, Object> buildBugSnapshot(Bug bug) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("bugKey", bug.getBugKey());
        snapshot.put("projectId", bug.getProject().getId());
        snapshot.put("title", bug.getTitle());
        snapshot.put("severity", bug.getSeverity().name());
        snapshot.put("priority", bug.getPriority().name());
        snapshot.put("status", bug.getStatus().name());
        snapshot.put("assigneeId", bug.getAssignee() != null ? bug.getAssignee().getId() : null);
        snapshot.put("relatedTaskId", bug.getRelatedTask() != null ? bug.getRelatedTask().getId() : null);
        return snapshot;
    }

    private String formatUser(User user) {
        return user != null ? user.getFullName() + " (" + user.getEmail() + ")" : "Unassigned";
    }

    private String truncateForHistory(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 250 ? text : text.substring(0, 247) + "...";
    }

    private String normalizeSearch(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }

    private String normalizeOptionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Map<BugStatus, Set<BugStatus>> buildStatusTransitions() {
        Map<BugStatus, Set<BugStatus>> map = new EnumMap<>(BugStatus.class);
        map.put(BugStatus.OPEN, EnumSet.of(BugStatus.ASSIGNED, BugStatus.IN_PROGRESS));
        map.put(BugStatus.ASSIGNED, EnumSet.of(BugStatus.IN_PROGRESS, BugStatus.OPEN));
        map.put(BugStatus.IN_PROGRESS, EnumSet.of(BugStatus.RESOLVED, BugStatus.ASSIGNED));
        map.put(BugStatus.RESOLVED, EnumSet.of(BugStatus.VERIFIED, BugStatus.REOPENED));
        map.put(BugStatus.VERIFIED, EnumSet.of(BugStatus.REOPENED));
        map.put(BugStatus.REOPENED, EnumSet.of(BugStatus.IN_PROGRESS, BugStatus.ASSIGNED));
        return map;
    }
}
