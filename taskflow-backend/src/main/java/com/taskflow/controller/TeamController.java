package com.taskflow.controller;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.organization.AddTeamMemberRequest;
import com.taskflow.dto.organization.CreateTeamRequest;
import com.taskflow.dto.organization.TeamDetailResponse;
import com.taskflow.dto.organization.TeamResponse;
import com.taskflow.dto.organization.UpdateTeamLeadRequest;
import com.taskflow.dto.organization.UpdateTeamRequest;
import com.taskflow.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "name", "updatedAt");

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TeamResponse>> listTeams(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));

        return ResponseEntity.ok(teamService.listTeams(search, active, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamDetailResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        TeamDetailResponse created = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamDetailResponse> getTeamById(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamDetailResponse> updateTeam(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTeamRequest request
    ) {
        return ResponseEntity.ok(teamService.updateTeam(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamResponse> deactivateTeam(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.deactivateTeam(id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamDetailResponse> addMember(
            @PathVariable Long id,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        TeamDetailResponse updated = teamService.addMember(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId
    ) {
        teamService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/lead")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamDetailResponse> updateTeamLead(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTeamLeadRequest request
    ) {
        return ResponseEntity.ok(teamService.updateTeamLead(id, request));
    }
}
