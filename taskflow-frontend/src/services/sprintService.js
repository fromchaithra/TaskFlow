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

function toSprintPayload({ name, goal, startDate, endDate }) {
  return {
    name: typeof name === 'string' ? name.trim() : name,
    goal: typeof goal === 'string' && goal.trim() ? goal.trim() : null,
    startDate,
    endDate,
  };
}

/**
 * Sprint API client mapped strictly to com.taskflow.controller.SprintController:
 * - GET    /api/v1/projects/{projectId}/sprints                         (canViewProject)
 * - POST   /api/v1/projects/{projectId}/sprints                         (canManageProject)
 * - GET    /api/v1/projects/{projectId}/sprints/{sprintId}              (canViewProject)
 * - PUT    /api/v1/projects/{projectId}/sprints/{sprintId}              (canManageProject)
 * - POST   /api/v1/projects/{projectId}/sprints/{sprintId}/start        (canManageProject)
 * - POST   /api/v1/projects/{projectId}/sprints/{sprintId}/complete     (canManageProject)
 * - POST   /api/v1/projects/{projectId}/sprints/{sprintId}/cancel       (canManageProject)
 * - POST   /api/v1/projects/{projectId}/sprints/{sprintId}/tasks        (canManageProject)
 * - DELETE /api/v1/projects/{projectId}/sprints/{sprintId}/tasks/{taskId} (canManageProject)
 * - GET    /api/v1/projects/{projectId}/sprints/{sprintId}/report       (canViewProject)
 */
export const sprintService = {
  async listProjectSprints(projectId, { status } = {}) {
    const params = cleanParams({ status });
    const response = await apiClient.get(`/projects/${projectId}/sprints`, { params });
    return response.data;
  },

  async createSprint(projectId, payload) {
    const response = await apiClient.post(
      `/projects/${projectId}/sprints`,
      toSprintPayload(payload)
    );
    return response.data;
  },

  async getSprintById(projectId, sprintId) {
    const response = await apiClient.get(`/projects/${projectId}/sprints/${sprintId}`);
    return response.data;
  },

  async updateSprint(projectId, sprintId, payload) {
    const response = await apiClient.put(
      `/projects/${projectId}/sprints/${sprintId}`,
      toSprintPayload(payload)
    );
    return response.data;
  },

  async startSprint(projectId, sprintId) {
    const response = await apiClient.post(
      `/projects/${projectId}/sprints/${sprintId}/start`
    );
    return response.data;
  },

  async completeSprint(projectId, sprintId) {
    const response = await apiClient.post(
      `/projects/${projectId}/sprints/${sprintId}/complete`
    );
    return response.data;
  },

  async cancelSprint(projectId, sprintId) {
    const response = await apiClient.post(
      `/projects/${projectId}/sprints/${sprintId}/cancel`
    );
    return response.data;
  },

  async addTasksToSprint(projectId, sprintId, taskIds) {
    const response = await apiClient.post(
      `/projects/${projectId}/sprints/${sprintId}/tasks`,
      { taskIds: taskIds.map((taskId) => Number(taskId)) }
    );
    return response.data;
  },

  async removeTaskFromSprint(projectId, sprintId, taskId) {
    await apiClient.delete(
      `/projects/${projectId}/sprints/${sprintId}/tasks/${taskId}`
    );
  },

  async getSprintReport(projectId, sprintId) {
    const response = await apiClient.get(
      `/projects/${projectId}/sprints/${sprintId}/report`
    );
    return response.data;
  },
};
