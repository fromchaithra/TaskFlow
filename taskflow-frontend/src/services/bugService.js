import { apiClient } from './apiClient';

function cleanParams(params) {
  const result = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') result[key] = value;
  }
  return result;
}

export const bugService = {
  async listBugs({ projectId, assigneeId, severity, status, search, page = 0, size = 20, sortBy = 'createdAt', sortDir = 'desc' } = {}) {
    const params = cleanParams({ projectId, assigneeId, severity, status, search, page, size, sortBy, sortDir });
    const response = await apiClient.get('/bugs', { params });
    return response.data;
  },
  async getBugById(id) {
    const response = await apiClient.get(`/bugs/${id}`);
    return response.data;
  },
  async createBug(payload) {
    const response = await apiClient.post('/bugs', payload);
    return response.data;
  },
  async updateBug(id, payload) {
    const response = await apiClient.put(`/bugs/${id}`, payload);
    return response.data;
  },
  async updateStatus(id, status, resolutionNotes) {
    const response = await apiClient.patch(`/bugs/${id}/status`, { status, resolutionNotes });
    return response.data;
  },
  async assignBug(id, assigneeId) {
    const response = await apiClient.patch(`/bugs/${id}/assignee`, { assigneeId });
    return response.data;
  },
  async listComments(id) {
    const response = await apiClient.get(`/bugs/${id}/comments`);
    return response.data;
  },
  async addComment(id, content) {
    const response = await apiClient.post(`/bugs/${id}/comments`, { content });
    return response.data;
  },
  async listAttachments(id) {
    const response = await apiClient.get(`/bugs/${id}/attachments`);
    return response.data;
  },
  async uploadAttachment(id, file) {
    const formData = new FormData();
    formData.append('file', file);
    const response = await apiClient.post(`/bugs/${id}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
  async deleteAttachment(bugId, attachmentId) {
    await apiClient.delete(`/bugs/${bugId}/attachments/${attachmentId}`);
  },
};
