package com.taskflow.service.impl;

import com.taskflow.dto.activity.ActivityResponse;
import com.taskflow.dto.common.PagedResponse;
import com.taskflow.entity.AuditLog;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.service.ActivityService;
import com.taskflow.service.AnalyticsAuthorizationService;
import com.taskflow.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ActivityServiceImpl implements ActivityService {

    private static final Set<Long> NO_PROJECTS = Set.of(-1L);

    private final AuditLogRepository auditLogRepository;
    private final AnalyticsAuthorizationService authorizationService;

    public ActivityServiceImpl(AuditLogRepository auditLogRepository,
                                AnalyticsAuthorizationService authorizationService) {
        this.auditLogRepository = auditLogRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ActivityResponse> recentActivity(Pageable pageable) {
        var viewer = SecurityUtils.currentUser();
        Set<Long> visible = authorizationService.resolveVisibleProjectIds();
        // Project-level visibility is enforced here, not only in the UI.
        // Organization-level records with no project remain admin-only.
        Page<AuditLog> page = auditLogRepository.searchAuthorizedAuditLogs(
                viewer.getOrganizationId(),
                viewer.isAdmin(),
                visible.isEmpty() ? NO_PROJECTS : visible,
                pageable);
        return PagedResponse.from(page.map(a -> new ActivityResponse(
                a.getId(),
                a.getActor() != null ? a.getActor().getFullName() : "System",
                a.getAction().name(),
                a.getEntityType(),
                a.getEntityId(),
                a.getProject() != null ? a.getProject().getName() : null,
                a.getCreatedAt()
        )));
    }
}
