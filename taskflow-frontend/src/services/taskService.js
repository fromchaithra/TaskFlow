import { apiClient } from './apiClient';

function cleanParams(params) {
  const result = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value;
    }
  }
  return result;
}

/**
 * Task & Kanban API client mapped strictly to:
 * - TaskController (/api/v1/tasks)
 * - KanbanController (/api/v1/projects/{projectId}/kanban)
 * - SprintController read-only list (/api/v1/projects/{projectId}/sprints) for task sprint selectors
 */
export const taskService = {
  async createTask({
    projectId,
    title,
    description,
    priority,
    assigneeId,
    sprintId,
    parentTaskId,
    estimatedHours,
    dueDate,
  }) {
    const payload = {
      projectId: Number(projectId),
      title: title.trim(),
      description: description && description.trim() ? description.trim() : null,
      priority: priority || 'MEDIUM',
      assigneeId: assigneeId ? Number(assigneeId) : null,
      sprintId: sprintId ? Number(sprintId) : null,
      parentTaskId: parentTaskId ? Number(parentTaskId) : null,
      estimatedHours:
        estimatedHours !== undefined && estimatedHours !== null && estimatedHours !== ''
          ? Number(estimatedHours)
          : null,
      dueDate: dueDate || null,
    };
    const response = await apiClient.post('/tasks', payload);
    return response.data;
  },

  async listTasks({
    projectId,
    assigneeId,
    status,
    priority,
    sprintId,
    overdueOnly = false,
    search,
    page = 0,
    size = 20,
    sortBy = 'createdAt',
    sortDir = 'desc',
  } = {}) {
    const params = cleanParams({
      projectId,
      assigneeId,
      status,
      priority,
      sprintId,
      overdueOnly: overdueOnly ? true : undefined,
      search: typeof search === 'string' ? search.trim() : undefined,
      page,
      size,
      sortBy,
      sortDir,
    });
    const response = await apiClient.get('/tasks', { params });
    return response.data;
  },

  async listMyTasks({
    projectId,
    status,
    priority,
    overdueOnly = false,
    search,
    page = 0,
    size = 20,
    sortBy = 'dueDate',
    sortDir = 'asc',
  } = {}) {
    const params = cleanParams({
      projectId,
      status,
      priority,
      overdueOnly: overdueOnly ? true : undefined,
      search: typeof search === 'string' ? search.trim() : undefined,
      page,
      size,
      sortBy,
      sortDir,
    });
    const response = await apiClient.get('/tasks/my-tasks', { params });
    return response.data;
  },

  async getTaskById(id) {
    const response = await apiClient.get(`/tasks/${id}`);
    return response.data;
  },

  async updateTask(
    id,
    {
      title,
      description,
      priority,
      assigneeId,
      sprintId,
      parentTaskId,
      estimatedHours,
      dueDate,
      boardOrder,
      expectedVersion,
      remarks,
    }
  ) {
    const payload = {};
    if (title !== undefined) payload.title = title.trim();
    if (description !== undefined) payload.description = description;
    if (priority !== undefined && priority !== '') payload.priority = priority;
    if (assigneeId !== undefined && assigneeId !== null && assigneeId !== '') {
      payload.assigneeId = Number(assigneeId);
    }
    if (sprintId !== undefined && sprintId !== null && sprintId !== '') {
      payload.sprintId = Number(sprintId);
    }
    if (parentTaskId !== undefined && parentTaskId !== null && parentTaskId !== '') {
      payload.parentTaskId = Number(parentTaskId);
    }
    if (estimatedHours !== undefined && estimatedHours !== null && estimatedHours !== '') {
      payload.estimatedHours = Number(estimatedHours);
    }
    if (dueDate !== undefined && dueDate !== null && dueDate !== '') {
      payload.dueDate = dueDate;
    }
    if (boardOrder !== undefined && boardOrder !== null) {
      payload.boardOrder = Number(boardOrder);
    }
    if (expectedVersion !== undefined && expectedVersion !== null) {
      payload.expectedVersion = Number(expectedVersion);
    }
    if (remarks && remarks.trim()) {
      payload.remarks = remarks.trim();
    }

    const response = await apiClient.put(`/tasks/${id}`, payload);
    return response.data;
  },

  async updateTaskStatus(id, { status, boardOrder = null, remarks = null }) {
    const payload = {
      status,
      boardOrder: boardOrder !== undefined && boardOrder !== null ? Number(boardOrder) : null,
      remarks: remarks && remarks.trim() ? remarks.trim() : null,
    };
    const response = await apiClient.patch(`/tasks/${id}/status`, payload);
    return response.data;
  },

  async assignTask(id, { assigneeId = null, remarks = null }) {
    const payload = {
      assigneeId: assigneeId ? Number(assigneeId) : null,
      remarks: remarks && remarks.trim() ? remarks.trim() : null,
    };
    const response = await apiClient.patch(`/tasks/${id}/assignee`, payload);
    return response.data;
  },

  async deleteTask(id) {
    await apiClient.delete(`/tasks/${id}`);
  },

  async getProjectKanbanBoard(
    projectId,
    { sprintId, assigneeId, priority, search, page = 0, limitPerColumn = 25 } = {}
  ) {
    const params = cleanParams({
      sprintId,
      assigneeId,
      priority,
      search: typeof search === 'string' ? search.trim() : undefined,
      page,
      limitPerColumn,
    });
    const response = await apiClient.get(`/projects/${projectId}/kanban`, { params });
    return response.data;
  },

  /**
   * Read-only project sprints list (GET /api/v1/projects/{projectId}/sprints)
   * used solely to populate sprint filter/selectors on Task List, Kanban Board, and Task Form.
   */
  async listProjectSprints(projectId, status) {
    const params = cleanParams({ status });
    const response = await apiClient.get(`/projects/${projectId}/sprints`, { params });
    return response.data;
  },
};
