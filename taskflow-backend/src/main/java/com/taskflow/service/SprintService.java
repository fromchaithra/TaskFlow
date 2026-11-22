package com.taskflow.service;

import com.taskflow.dto.sprint.AddSprintTasksRequest;
import com.taskflow.dto.sprint.CreateSprintRequest;
import com.taskflow.dto.sprint.SprintDetailResponse;
import com.taskflow.dto.sprint.SprintReportResponse;
import com.taskflow.dto.sprint.SprintSummaryResponse;
import com.taskflow.dto.sprint.UpdateSprintRequest;
import com.taskflow.enums.SprintStatus;

import java.util.List;

public interface SprintService {

    SprintDetailResponse createSprint(Long projectId, CreateSprintRequest request);

    List<SprintSummaryResponse> listProjectSprints(Long projectId, SprintStatus status);

    SprintDetailResponse getSprintById(Long projectId, Long sprintId);

    SprintDetailResponse updateSprint(Long projectId, Long sprintId, UpdateSprintRequest request);

    SprintDetailResponse startSprint(Long projectId, Long sprintId);

    SprintDetailResponse completeSprint(Long projectId, Long sprintId);

    SprintDetailResponse cancelSprint(Long projectId, Long sprintId);

    SprintDetailResponse addTasksToSprint(Long projectId, Long sprintId, AddSprintTasksRequest request);

    void removeTaskFromSprint(Long projectId, Long sprintId, Long taskId);

    SprintReportResponse getSprintReport(Long projectId, Long sprintId);
}
