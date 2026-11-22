import { apiClient, requestTokenRefresh } from './apiClient';
import { tokenManager } from '../utils/tokenManager';

/**
 * Auth service mapped strictly to com.taskflow.controller.AuthController:
 * - POST /api/v1/auth/login
 * - POST /api/v1/auth/refresh
 * - POST /api/v1/auth/logout
 * - GET  /api/v1/auth/me
 * - GET  /api/v1/auth/activate/validate
 * - POST /api/v1/auth/activate
 * - GET  /api/v1/setup/status
 * - POST /api/v1/setup/admin
 */
export const authService = {
  async login({ email, password }) {
    const response = await apiClient.post(
      '/auth/login',
      { email: email.trim(), password },
      { _skipAuthHeader: true }
    );
    const data = response.data;
    if (data?.accessToken) {
      tokenManager.setAccessToken(data.accessToken, data.expiresIn);
    }
    return data;
  },

  async refresh() {
    return requestTokenRefresh();
  },

  async logout() {
    try {
      await apiClient.post('/auth/logout');
    } finally {
      tokenManager.clear();
    }
  },

  async getCurrentUser() {
    const response = await apiClient.get('/auth/me');
    return response.data;
  },

  async validateActivationToken(token) {
    const response = await apiClient.get('/auth/activate/validate', {
      params: { token },
      _skipAuthHeader: true,
    });
    return response.data;
  },

  async activateAccount({ token, password }) {
    const response = await apiClient.post(
      '/auth/activate',
      { token, password },
      { _skipAuthHeader: true }
    );
    const data = response.data;
    if (data?.accessToken) {
      tokenManager.setAccessToken(data.accessToken, data.expiresIn);
    }
    return data;
  },

  async getSetupStatus() {
    const response = await apiClient.get('/setup/status', { _skipAuthHeader: true });
    return response.data;
  },

  async registerFirstAdmin({ fullName, email, password, confirmPassword, organizationName }) {
    const response = await apiClient.post(
      '/setup/admin',
      {
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        confirmPassword,
        organizationName: organizationName?.trim() || undefined,
      },
      { _skipAuthHeader: true }
    );
    return response.data;
  },
};
