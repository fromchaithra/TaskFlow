/**
 * In-memory access token store.
 *
 * Security design:
 * - The short-lived JWT access token is intentionally kept only in JS memory
 *   (never persisted to localStorage or sessionStorage) to mitigate XSS exfiltration.
 * - Session persistence across reloads relies on the backend's HttpOnly `tf_refresh`
 *   cookie exchanged via POST /api/v1/auth/refresh.
 */
let accessToken = null;
let tokenExpiresAt = null;

export const tokenManager = {
  getAccessToken() {
    return accessToken;
  },

  setAccessToken(token, expiresInSeconds = null) {
    accessToken = token || null;
    if (token && typeof expiresInSeconds === 'number' && expiresInSeconds > 0) {
      tokenExpiresAt = Date.now() + expiresInSeconds * 1000;
    } else {
      tokenExpiresAt = null;
    }
  },

  getTokenExpiresAt() {
    return tokenExpiresAt;
  },

  hasToken() {
    return Boolean(accessToken);
  },

  clear() {
    accessToken = null;
    tokenExpiresAt = null;
  },
};
