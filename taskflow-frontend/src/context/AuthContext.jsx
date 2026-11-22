import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { registerAuthCallbacks } from '../services/apiClient';
import { authService } from '../services/authService';
import { tokenManager } from '../utils/tokenManager';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isBootstrapping, setIsBootstrapping] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);

  const userRef = useRef(null);
  userRef.current = user;

  useEffect(() => {
    registerAuthCallbacks({
      onTokenRefreshed: (payload) => {
        if (payload?.user) {
          setUser(payload.user);
        }
      },
      onSessionExpired: () => {
        const hadActiveSession = Boolean(userRef.current);
        tokenManager.clear();
        setUser(null);
        if (hadActiveSession) {
          setSessionExpired(true);
        }
      },
    });

    return () => {
      registerAuthCallbacks({
        onTokenRefreshed: null,
        onSessionExpired: null,
      });
    };
  }, []);

  // Silently restore session on initial load using the HttpOnly tf_refresh cookie.
  // Single-flight promise in apiClient ensures StrictMode remounts do not trigger duplicate refreshes.
  useEffect(() => {
    let active = true;

    async function bootstrapSession() {
      try {
        const data = await authService.refresh();
        if (active && data?.user) {
          setUser(data.user);
          setSessionExpired(false);
        }
      } catch {
        if (active) {
          tokenManager.clear();
          setUser(null);
        }
      } finally {
        if (active) {
          setIsBootstrapping(false);
        }
      }
    }

    bootstrapSession();

    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async ({ email, password }) => {
    const data = await authService.login({ email, password });
    setUser(data.user);
    setSessionExpired(false);
    return data;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } catch {
      // Clear client auth state even if backend logout returns 401 or network error.
    } finally {
      tokenManager.clear();
      setUser(null);
      setSessionExpired(false);
    }
  }, []);

  const refreshCurrentUser = useCallback(async () => {
    const profile = await authService.getCurrentUser();
    setUser(profile);
    return profile;
  }, []);

  const clearSessionExpired = useCallback(() => {
    setSessionExpired(false);
  }, []);

  const hasRole = useCallback(
    (allowedRoles) => {
      if (!user?.role) return false;
      if (!allowedRoles || allowedRoles.length === 0) return true;
      if (Array.isArray(allowedRoles)) {
        return allowedRoles.includes(user.role);
      }
      return user.role === allowedRoles;
    },
    [user]
  );

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: Boolean(user && tokenManager.hasToken()),
      isBootstrapping,
      sessionExpired,
      login,
      logout,
      refreshCurrentUser,
      clearSessionExpired,
      hasRole,
    }),
    [
      user,
      isBootstrapping,
      sessionExpired,
      login,
      logout,
      refreshCurrentUser,
      clearSessionExpired,
      hasRole,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
