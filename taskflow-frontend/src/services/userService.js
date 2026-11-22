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
 * User service mapped to com.taskflow.controller.UserController:
 * - GET   /api/v1/users           (ADMIN, PROJECT_MANAGER)
 * - POST  /api/v1/users           (ADMIN)
 * - GET   /api/v1/users/{id}      (ADMIN or self)
 * - PUT   /api/v1/users/{id}      (ADMIN or self)
 * - PATCH /api/v1/users/{id}/status (ADMIN)
 * - PATCH /api/v1/users/{id}/role   (ADMIN)
 */
export const userService = {
  async listUsers({
    search,
    role,
    active,
    page = 0,
    size = 20,
    sortBy = 'createdAt',
    sortDir = 'desc',
  } = {}) {
    const params = cleanParams({
      search: typeof search === 'string' ? search.trim() : undefined,
      role,
      active,
      page,
      size,
      sortBy,
      sortDir,
    });
    const response = await apiClient.get('/users', { params });
    return response.data;
  },

  async getUserById(id) {
    const response = await apiClient.get(`/users/${id}`);
    return response.data;
  },

  async inviteUser({ fullName, email, jobTitle, roleName, avatarColor }) {
    const payload = {
      fullName: fullName.trim(),
      email: email.trim(),
      jobTitle: jobTitle && jobTitle.trim() ? jobTitle.trim() : null,
      roleName,
      avatarColor: avatarColor && avatarColor.trim() ? avatarColor.trim() : null,
    };
    const response = await apiClient.post('/users/invite', payload);
    return response.data;
  },

  async createUser({ fullName, email, password, jobTitle, roleName, avatarColor }) {
    const payload = {
      fullName: fullName.trim(),
      email: email.trim(),
      password,
      jobTitle: jobTitle && jobTitle.trim() ? jobTitle.trim() : null,
      roleName,
      avatarColor: avatarColor && avatarColor.trim() ? avatarColor.trim() : null,
    };
    const response = await apiClient.post('/users', payload);
    return response.data;
  },

  async updateUser(id, { fullName, jobTitle, avatarColor }) {
    const payload = {
      fullName: fullName.trim(),
      jobTitle: jobTitle && jobTitle.trim() ? jobTitle.trim() : null,
      avatarColor: avatarColor && avatarColor.trim() ? avatarColor.trim() : null,
    };
    const response = await apiClient.put(`/users/${id}`, payload);
    return response.data;
  },

  async updateUserStatus(id, active) {
    const response = await apiClient.patch(`/users/${id}/status`, { active: Boolean(active) });
    return response.data;
  },

  async updateUserRole(id, roleName) {
    const response = await apiClient.patch(`/users/${id}/role`, { roleName });
    return response.data;
  },
};
