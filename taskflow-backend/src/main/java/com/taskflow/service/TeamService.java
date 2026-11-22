package com.taskflow.service;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.organization.AddTeamMemberRequest;
import com.taskflow.dto.organization.CreateTeamRequest;
import com.taskflow.dto.organization.TeamDetailResponse;
import com.taskflow.dto.organization.TeamResponse;
import com.taskflow.dto.organization.UpdateTeamLeadRequest;
import com.taskflow.dto.organization.UpdateTeamRequest;
import org.springframework.data.domain.Pageable;

public interface TeamService {

    PagedResponse<TeamResponse> listTeams(String search, Boolean active, Pageable pageable);

    TeamDetailResponse getTeamById(Long teamId);

    TeamDetailResponse createTeam(CreateTeamRequest request);

    TeamDetailResponse updateTeam(Long teamId, UpdateTeamRequest request);

    TeamResponse deactivateTeam(Long teamId);

    TeamDetailResponse addMember(Long teamId, AddTeamMemberRequest request);

    void removeMember(Long teamId, Long userId);

    TeamDetailResponse updateTeamLead(Long teamId, UpdateTeamLeadRequest request);
}
