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
 * Project service mapped strictly to com.taskflow.controller.ProjectController:
 * - GET    /api/v1/projects                         (all authenticated roles)
 * - POST   /api/v1/projects                         (ADMIN, PROJECT_MANAGER)
 * - GET    /api/v1/projects/{id}                    (canViewProject)
 * - PUT    /api/v1/projects/{id}                    (canManageProject)
 * - PATCH  /api/v1/projects/{id}/status             (canManageProject)
 * - DELETE /api/v1/projects/{id}                    (canManageProject -> archive)
 * - GET    /api/v1/projects/{id}/members            (canViewProject)
 * - POST   /api/v1/projects/{id}/members            (canManageProject)
 * - DELETE /api/v1/projects/{id}/members/{userId}   (canManageProject)
 * - PATCH  /api/v1/projects/{id}/members/{uid}/role (canManageProject)
 */
export const projectService = {
  async listProjects({
    status,
    search,
    page = 0,
    size = 20,
    sortBy = 'createdAt',
    sortDir = 'desc',
  } = {}) {
    const params = cleanParams({
      status,
      search: typeof search === 'string' ? search.trim() : undefined,
      page,
      size,
      sortBy,
      sortDir,
    });
    const response = await apiClient.get('/projects', { params });
    return response.data;
  },

  async createProject({
    name,
    projectKey,
    description,
    startDate,
    endDate,
    initialStatus,
    managerId,
    teamId,
    memberIds,
  }) {
    const payload = {
      name: name.trim(),
      projectKey: projectKey.trim().toUpperCase(),
      description: description && description.trim() ? description.trim() : null,
      startDate,
      endDate: endDate || null,
      initialStatus: initialStatus || 'ACTIVE',
      managerId: managerId ? Number(managerId) : null,
      teamId: teamId ? Number(teamId) : null,
      memberIds: Array.isArray(memberIds) && memberIds.length > 0 ? memberIds.map(Number) : [],
    };
    const response = await apiClient.post('/projects', payload);
    return response.data;
  },

  async getProjectById(id) {
    const response = await apiClient.get(`/projects/${id}`);
    return response.data;
  },

  async updateProject(id, { name, description, startDate, endDate, managerId }) {
    const payload = {
      name: name.trim(),
      description: description && description.trim() ? description.trim() : null,
      startDate,
      endDate: endDate || null,
      managerId: managerId ? Number(managerId) : null,
    };
    const response = await apiClient.put(`/projects/${id}`, payload);
    return response.data;
  },

  async updateProjectStatus(id, status) {
    const response = await apiClient.patch(`/projects/${id}/status`, { status });
    return response.data;
  },

  async archiveProject(id) {
    const response = await apiClient.delete(`/projects/${id}`);
    return response.data;
  },

  async listProjectMembers(id) {
    const response = await apiClient.get(`/projects/${id}/members`);
    return response.data;
  },

  async addProjectMember(id, { userId, projectRole }) {
    const payload = {
      userId: Number(userId),
      projectRole: projectRole || null,
    };
    const response = await apiClient.post(`/projects/${id}/members`, payload);
    return response.data;
  },

  async removeProjectMember(id, userId) {
    await apiClient.delete(`/projects/${id}/members/${userId}`);
  },

  async updateProjectMemberRole(id, userId, projectRole) {
    const response = await apiClient.patch(`/projects/${id}/members/${userId}/role`, {
      projectRole,
    });
    return response.data;
  },
};
