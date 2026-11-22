import axios from 'axios';
import { CSRF_HEADER_NAME, CSRF_HEADER_VALUE } from '../utils/constants';
import { tokenManager } from '../utils/tokenManager';

export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
).replace(/\/+$/, '');

/**
 * Primary Axios instance for authenticated and public API requests.
 * Sends credentials so HttpOnly refresh cookies work seamlessly with Spring Boot CORS.
 */
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
    [CSRF_HEADER_NAME]: CSRF_HEADER_VALUE,
  },
});

/**
 * Dedicated Axios instance for /auth/refresh to prevent response-interceptor recursion.
 */
const refreshClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
    [CSRF_HEADER_NAME]: CSRF_HEADER_VALUE,
  },
});

let activeRefreshPromise = null;
let sessionExpiredListener = null;
let tokenRefreshedListener = null;

export function registerAuthCallbacks({ onSessionExpired, onTokenRefreshed }) {
  sessionExpiredListener = onSessionExpired || null;
  tokenRefreshedListener = onTokenRefreshed || null;
}

/**
 * Single-flight refresh call.
 * Critical: Backend rotates and revokes the previous refresh token on each call.
 * Coalescing concurrent calls prevents accidental token-reuse detection revoking all sessions.
 */
export function requestTokenRefresh() {
  if (!activeRefreshPromise) {
    activeRefreshPromise = refreshClient
      .post('/auth/refresh')
      .then((response) => {
        const payload = response.data;
        if (!payload?.accessToken) {
          throw new Error('Invalid refresh response: missing access token.');
        }
        tokenManager.setAccessToken(payload.accessToken, payload.expiresIn);
        if (typeof tokenRefreshedListener === 'function') {
          tokenRefreshedListener(payload);
        }
        return payload;
      })
      .finally(() => {
        activeRefreshPromise = null;
      });
  }

  return activeRefreshPromise;
}

const AUTH_BYPASS_PATHS = ['/auth/login', '/auth/refresh', '/auth/logout'];

function isAuthBypassRequest(url = '') {
  return AUTH_BYPASS_PATHS.some((path) => url.includes(path));
}

apiClient.interceptors.request.use(
  (config) => {
    const token = tokenManager.getAccessToken();
    if (token && !config._skipAuthHeader) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error?.config;
    const status = error?.response?.status;

    if (
      status !== 401 ||
      !originalRequest ||
      originalRequest._retry ||
      isAuthBypassRequest(originalRequest.url)
    ) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      const refreshed = await requestTokenRefresh();
      originalRequest.headers = originalRequest.headers || {};
      originalRequest.headers.Authorization = `Bearer ${refreshed.accessToken}`;
      return apiClient(originalRequest);
    } catch (refreshError) {
      tokenManager.clear();
      if (typeof sessionExpiredListener === 'function') {
        sessionExpiredListener(refreshError);
      }
      return Promise.reject(refreshError);
    }
  }
);
