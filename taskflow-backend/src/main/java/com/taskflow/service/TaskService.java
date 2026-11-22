package com.taskflow.service;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.task.AssignTaskRequest;
import com.taskflow.dto.task.CreateTaskRequest;
import com.taskflow.dto.task.KanbanBoardResponse;
import com.taskflow.dto.task.TaskDetailResponse;
import com.taskflow.dto.task.TaskSummaryResponse;
import com.taskflow.dto.task.UpdateTaskRequest;
import com.taskflow.dto.task.UpdateTaskStatusRequest;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.TaskStatus;
import org.springframework.data.domain.Pageable;

public interface TaskService {

    TaskDetailResponse createTask(CreateTaskRequest request);

    PagedResponse<TaskSummaryResponse> listTasks(
            Long projectId,
            Long assigneeId,
            TaskStatus status,
            PriorityLevel priority,
            Long sprintId,
            boolean overdueOnly,
            String search,
            Pageable pageable
    );

    PagedResponse<TaskSummaryResponse> listMyTasks(
            Long projectId,
            TaskStatus status,
            PriorityLevel priority,
            boolean overdueOnly,
            String search,
            Pageable pageable
    );

    TaskDetailResponse getTaskById(Long taskId);

    TaskDetailResponse updateTask(Long taskId, UpdateTaskRequest request);

    TaskDetailResponse updateTaskStatus(Long taskId, UpdateTaskStatusRequest request);

    TaskDetailResponse assignTask(Long taskId, AssignTaskRequest request);

    void deleteTask(Long taskId);

    KanbanBoardResponse getProjectKanbanBoard(
            Long projectId,
            Long sprintId,
            Long assigneeId,
            PriorityLevel priority,
            String search,
            int page,
            int limitPerColumn
    );
}
