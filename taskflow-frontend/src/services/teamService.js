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
 * Team service mapped to com.taskflow.controller.TeamController:
 * - GET    /api/v1/teams                    (all authenticated roles)
 * - POST   /api/v1/teams                    (ADMIN)
 * - GET    /api/v1/teams/{id}               (all authenticated roles)
 * - PUT    /api/v1/teams/{id}               (ADMIN)
 * - DELETE /api/v1/teams/{id}               (ADMIN - soft deactivate)
 * - POST   /api/v1/teams/{id}/members       (ADMIN)
 * - DELETE /api/v1/teams/{id}/members/{uid} (ADMIN)
 * - PATCH  /api/v1/teams/{id}/lead          (ADMIN)
 */
export const teamService = {
  async listTeams({
    search,
    active,
    page = 0,
    size = 20,
    sortBy = 'createdAt',
    sortDir = 'desc',
  } = {}) {
    const params = cleanParams({
      search: typeof search === 'string' ? search.trim() : undefined,
      active,
      page,
      size,
      sortBy,
      sortDir,
    });
    const response = await apiClient.get('/teams', { params });
    return response.data;
  },

  async getTeamById(id) {
    const response = await apiClient.get(`/teams/${id}`);
    return response.data;
  },

  async createTeam({ name, description, leadUserId = null, memberIds = [] }) {
    const payload = {
      name: name.trim(),
      description: description && description.trim() ? description.trim() : null,
      leadUserId: leadUserId ? Number(leadUserId) : null,
      memberIds: Array.isArray(memberIds) ? memberIds.map(Number) : [],
    };
    const response = await apiClient.post('/teams', payload);
    return response.data;
  },

  async updateTeam(id, { name, description, leadUserId = null, active = undefined, memberIds = null }) {
    const payload = {
      name: name.trim(),
      description: description && description.trim() ? description.trim() : null,
      leadUserId: leadUserId ? Number(leadUserId) : null,
      active: typeof active === 'boolean' ? active : null,
      memberIds: Array.isArray(memberIds) ? memberIds.map(Number) : null,
    };
    const response = await apiClient.put(`/teams/${id}`, payload);
    return response.data;
  },

  async deactivateTeam(id) {
    const response = await apiClient.delete(`/teams/${id}`);
    return response.data;
  },

  async addMember(teamId, userId) {
    const response = await apiClient.post(`/teams/${teamId}/members`, {
      userId: Number(userId),
    });
    return response.data;
  },

  async removeMember(teamId, userId) {
    await apiClient.delete(`/teams/${teamId}/members/${userId}`);
  },

  async updateTeamLead(teamId, leadUserId) {
    const response = await apiClient.patch(`/teams/${teamId}/lead`, {
      leadUserId: Number(leadUserId),
    });
    return response.data;
  },
};
