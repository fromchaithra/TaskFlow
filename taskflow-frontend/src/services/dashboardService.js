import { apiClient } from './apiClient';

export const dashboardService = {
  async getStats() {
    const response = await apiClient.get('/dashboard/stats');
    return response.data;
  },
};

export const activityService = {
  async list({ page = 0, size = 10 } = {}) {
    const response = await apiClient.get('/activity', { params: { page, size } });
    return response.data;
  },
};

export const notificationService = {
  async list({ unreadOnly = false, page = 0, size = 20 } = {}) {
    const response = await apiClient.get('/notifications', { params: { unreadOnly, page, size } });
    return response.data;
  },
  async unreadCount() {
    const response = await apiClient.get('/notifications/unread-count');
    return response.data;
  },
  async markRead(id) {
    const response = await apiClient.patch(`/notifications/${id}/read`);
    return response.data;
  },
  async markAllRead() {
    const response = await apiClient.post('/notifications/read-all');
    return response.data;
  },
};
