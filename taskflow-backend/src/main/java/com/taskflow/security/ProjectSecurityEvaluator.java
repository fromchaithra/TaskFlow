package com.taskflow.security;

import com.taskflow.entity.Project;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("projectGuard")
public class ProjectSecurityEvaluator {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectSecurityEvaluator(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Transactional(readOnly = true)
    public boolean canViewProject(Long projectId, Authentication authentication) {
        CustomUserDetails principal = extractPrincipal(authentication);
        if (principal == null) {
            return false;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || !project.getOrganization().getId().equals(principal.getOrganizationId())) {
            return false;
        }
        if (principal.getPrimaryRole() == RoleName.ADMIN) {
            return true;
        }
        return project.getManager().getId().equals(principal.getId())
                || projectMemberRepository.existsByProjectIdAndUserId(projectId, principal.getId());
    }

    @Transactional(readOnly = true)
    public boolean canManageProject(Long projectId, Authentication authentication) {
        CustomUserDetails principal = extractPrincipal(authentication);
        if (principal == null) {
            return false;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || !project.getOrganization().getId().equals(principal.getOrganizationId())) {
            return false;
        }
        if (principal.getPrimaryRole() == RoleName.ADMIN) {
            return true;
        }
        return principal.getPrimaryRole() == RoleName.PROJECT_MANAGER
                && project.getManager().getId().equals(principal.getId());
    }

    @Transactional(readOnly = true)
    public boolean isProjectManager(Long projectId, Authentication authentication) {
        CustomUserDetails principal = extractPrincipal(authentication);
        if (principal == null || principal.getPrimaryRole() != RoleName.PROJECT_MANAGER) {
            return false;
        }
        return projectRepository.findById(projectId)
                .map(p -> p.getOrganization().getId().equals(principal.getOrganizationId())
                        && p.getManager().getId().equals(principal.getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public void assertCanViewProject(Long projectId, CustomUserDetails user) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (!project.getOrganization().getId().equals(user.getOrganizationId())) {
            throw new UnauthorizedResourceAccessException("You do not have access to this project.");
        }
        if (user.isAdmin()) {
            return;
        }
        boolean allowed = project.getManager().getId().equals(user.getId())
                || projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId());
        if (!allowed) {
            throw new UnauthorizedResourceAccessException("Project membership is required.");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanManageProject(Long projectId, CustomUserDetails user) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (!project.getOrganization().getId().equals(user.getOrganizationId())) {
            throw new UnauthorizedResourceAccessException("You do not have access to this project.");
        }
        if (user.isAdmin()) {
            return;
        }
        if (user.getPrimaryRole() == RoleName.PROJECT_MANAGER && project.getManager().getId().equals(user.getId())) {
            return;
        }
        throw new UnauthorizedResourceAccessException("Only an organization admin or the assigned project manager can manage this project.");
    }

    @Transactional(readOnly = true)
    public void assertProjectMember(Long projectId, CustomUserDetails user) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (!project.getOrganization().getId().equals(user.getOrganizationId())) {
            throw new UnauthorizedResourceAccessException("You do not have access to this project.");
        }
        boolean member = project.getManager().getId().equals(user.getId())
                || projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId());
        if (!member) {
            throw new UnauthorizedResourceAccessException("Project membership is required.");
        }
    }

    @Transactional(readOnly = true)
    public void assertProjectManager(Long projectId, CustomUserDetails user) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (!project.getOrganization().getId().equals(user.getOrganizationId())
                || user.getPrimaryRole() != RoleName.PROJECT_MANAGER
                || !project.getManager().getId().equals(user.getId())) {
            throw new UnauthorizedResourceAccessException("Only the assigned project manager can modify this project.");
        }
    }

    private CustomUserDetails extractPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            return null;
        }
        return principal;
    }
}
