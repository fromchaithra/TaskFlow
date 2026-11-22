package com.taskflow.service;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.project.AddProjectMemberRequest;
import com.taskflow.dto.project.CreateProjectRequest;
import com.taskflow.dto.project.ProjectDetailResponse;
import com.taskflow.dto.project.ProjectMemberResponse;
import com.taskflow.dto.project.ProjectSummaryResponse;
import com.taskflow.dto.project.UpdateProjectMemberRoleRequest;
import com.taskflow.dto.project.UpdateProjectRequest;
import com.taskflow.dto.project.UpdateProjectStatusRequest;
import com.taskflow.enums.ProjectStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProjectService {

    ProjectDetailResponse createProject(CreateProjectRequest request);

    PagedResponse<ProjectSummaryResponse> listProjects(ProjectStatus status, String search, Pageable pageable);

    ProjectDetailResponse getProjectById(Long projectId);

    ProjectDetailResponse updateProject(Long projectId, UpdateProjectRequest request);

    ProjectDetailResponse updateProjectStatus(Long projectId, UpdateProjectStatusRequest request);

    ProjectSummaryResponse archiveProject(Long projectId);

    List<ProjectMemberResponse> listProjectMembers(Long projectId);

    ProjectMemberResponse addProjectMember(Long projectId, AddProjectMemberRequest request);

    void removeProjectMember(Long projectId, Long userId);

    ProjectMemberResponse updateProjectMemberRole(
            Long projectId,
            Long userId,
            UpdateProjectMemberRoleRequest request
    );
}
