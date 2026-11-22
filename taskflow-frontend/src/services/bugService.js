import { apiClient, API_BASE_URL } from './apiClient';
import { parseContentDisposition } from '../utils/download';

function cleanParams(params) {
  const result = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') result[key] = value;
  }
  return result;
}

/** Resolve a backend-provided path against the configured API origin only. */
function resolveAttachmentUrl(downloadUrl, bugId, attachmentId) {
  const fallbackPath = `/api/v1/bugs/${bugId}/attachments/${attachmentId}/download`;
  const supplied = typeof downloadUrl === 'string' && downloadUrl.trim() ? downloadUrl.trim() : fallbackPath;

  let apiOrigin;
  try {
    apiOrigin = new URL(API_BASE_URL).origin;
  } catch {
    throw new Error('The API base URL is not configured correctly.');
  }

  if (/^https?:\/\//i.test(supplied)) {
    let targetOrigin;
    try {
      targetOrigin = new URL(supplied).origin;
    } catch {
      throw new Error('Invalid attachment download URL.');
    }
    if (targetOrigin !== apiOrigin) {
      throw new Error('Invalid attachment download URL.');
    }
    return supplied;
  }

  return supplied.startsWith('/') ? `${apiOrigin}${supplied}` : `${API_BASE_URL}/${supplied}`;
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
  async updateStatus(id, status, remarks) {
    const payload = {
      status,
      remarks: remarks && String(remarks).trim() ? String(remarks).trim() : null,
    };
    const response = await apiClient.patch(`/bugs/${id}/status`, payload);
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

  /** Authenticated attachment download; returns an in-memory Blob and safe filename. */
  async downloadAttachment(bugId, attachmentId, downloadUrl, fallbackName) {
    const url = resolveAttachmentUrl(downloadUrl, bugId, attachmentId);
    const response = await apiClient.get(url, { responseType: 'blob' });
    const blob = response?.data;
    if (!blob || (typeof blob.size === 'number' && blob.size === 0)) {
      throw new Error('The attachment was empty.');
    }
    const headers = response.headers || {};
    const disposition = headers['content-disposition'] || headers['Content-Disposition'] || '';
    const filename = parseContentDisposition(disposition)
      || (typeof fallbackName === 'string' && fallbackName.trim())
      || `attachment-${attachmentId}`;
    return { blob, filename };
  },
};
