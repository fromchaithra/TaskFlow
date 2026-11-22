import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { requestTokenRefresh } from '../services/apiClient';
import {
  fetchSnapshot,
  millisecondsUntilTokenRefresh,
  openLiveStream,
} from '../services/liveAnalyticsService';
import { tokenManager } from '../utils/tokenManager';
import { parseApiError } from '../utils/errorHandler';

export const LIVE_STATUS = Object.freeze({
  CONNECTING: 'CONNECTING',
  LIVE: 'LIVE',
  RECONNECTING: 'RECONNECTING',
  POLLING: 'POLLING',
  ERROR: 'ERROR',
});

const MAX_FAILURES_BEFORE_POLLING = 3;
const MAX_RETRY_DELAY_MS = 30_000;
const POLL_INTERVAL_MS = 30_000;
const SSE_RECOVERY_INTERVAL_MS = 60_000;

/**
 * Reusable live-analytics connection manager.
 *
 * Guarantees at most one active SSE stream, backs off retries, falls back to
 * REST polling after repeated failures, and keeps attempting SSE recovery.
 */
export function useLiveAnalytics(filters = {}, isAuthenticated = true, identityKey = '') {
  const [snapshot, setSnapshot] = useState(null);
  const [status, setStatus] = useState(LIVE_STATUS.CONNECTING);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [error, setError] = useState(null);
  const [refreshing, setRefreshing] = useState(false);

  const filtersKey = useMemo(() => JSON.stringify(filters ?? {}), [filters]);
  const filtersRef = useRef(filters);
  filtersRef.current = filters;

  const aliveRef = useRef(true);
  const generationRef = useRef(0);
  const cleanupRef = useRef(null);
  const retryRef = useRef(null);
  const pollRef = useRef(null);
  const recoveryRef = useRef(null);
  const expiryRef = useRef(null);
  const failuresRef = useRef(0);
  const pollingRef = useRef(false);

  const clearTimer = (ref) => {
    if (ref.current) {
      clearTimeout(ref.current);
      clearInterval(ref.current);
      ref.current = null;
    }
  };

  const stopStream = useCallback(() => {
    if (cleanupRef.current) {
      const cleanup = cleanupRef.current;
      cleanupRef.current = null;
      cleanup();
    }
    clearTimer(expiryRef);
  }, []);

  const stopPolling = useCallback(() => {
    clearTimer(pollRef);
    pollingRef.current = false;
  }, []);

  const stopAll = useCallback(() => {
    stopStream();
    stopPolling();
    clearTimer(retryRef);
    clearTimer(recoveryRef);
  }, [stopStream, stopPolling]);

  const applySnapshot = useCallback((payload, fromPolling = false) => {
    if (!aliveRef.current || !payload) return;
    setSnapshot(payload);
    setLastUpdated(Date.now());
    setError(null);
    failuresRef.current = 0;
    if (fromPolling) {
      // A successful poll must not cancel the fallback loop or SSE recovery timer;
      // otherwise the badge claims "Live" while the snapshot is frozen.
      setStatus(LIVE_STATUS.POLLING);
      return;
    }
    stopPolling();
    clearTimer(recoveryRef);
    setStatus(LIVE_STATUS.LIVE);
  }, [stopPolling]);

  const pollOnce = useCallback(async () => {
    try {
      const payload = await fetchSnapshot(filtersRef.current);
      applySnapshot(payload, true);
      if (pollingRef.current) setStatus(LIVE_STATUS.POLLING);
    } catch (err) {
      if (!aliveRef.current) return;
      setError(parseApiError(err).message);
      // Keep the POLLING badge while fallback polling is active; the error is
      // shown separately so users know refreshing is still being attempted.
      if (!pollingRef.current) setStatus(LIVE_STATUS.ERROR);
    }
  }, [applySnapshot]);

  const startPolling = useCallback(() => {
    if (pollingRef.current) return;
    pollingRef.current = true;
    setStatus(LIVE_STATUS.POLLING);
    pollOnce();
    clearTimer(pollRef);
    pollRef.current = setInterval(pollOnce, POLL_INTERVAL_MS);
  }, [pollOnce]);

  const connect = useCallback(async () => {
    if (!aliveRef.current || !isAuthenticated || !tokenManager.hasToken()) return;
    stopStream();
    clearTimer(retryRef);
    clearTimer(recoveryRef);
    // Captured before any await so a generation change during refresh cannot
    // let a stale connect() open an untracked SSE stream.
    const generation = generationRef.current;

    // Rotate the access token before opening a long-lived stream so it cannot
    // expire mid-connection. Uses the shared single-flight refresh.
    const remaining = tokenManager.getTokenExpiresAt()
      ? tokenManager.getTokenExpiresAt() - Date.now()
      : Infinity;
    if (remaining < 45_000) {
      try {
        await requestTokenRefresh();
      } catch {
        if (aliveRef.current) {
          setError('Your session has expired. Please sign in again.');
          setStatus(LIVE_STATUS.ERROR);
        }
        return;
      }
    }
    if (!aliveRef.current || generation !== generationRef.current || !tokenManager.hasToken()) return;

    setStatus(failuresRef.current > 0 ? LIVE_STATUS.RECONNECTING : LIVE_STATUS.CONNECTING);

    cleanupRef.current = openLiveStream({
      filters: filtersRef.current,
      onSnapshot: applySnapshot,
      onEnd: (reason) => {
        if (!aliveRef.current || generation !== generationRef.current) return;
        if (cleanupRef.current) {
          cleanupRef.current();
          cleanupRef.current = null;
        }

        if (reason === 'unauthorized') {
          // A failed direct refresh does not go through apiClient's session-expired
          // callback, so drop potentially stale data from the previous account.
          setSnapshot(null);
          setLastUpdated(null);
          setError('Session expired. Sign in again to resume live updates.');
          setStatus(LIVE_STATUS.ERROR);
          return;
        }

        failuresRef.current += 1;
        if (failuresRef.current >= MAX_FAILURES_BEFORE_POLLING) {
          startPolling();
          if (!recoveryRef.current) {
            recoveryRef.current = setTimeout(() => connect(), SSE_RECOVERY_INTERVAL_MS);
          }
          return;
        }

        const delay = Math.min(1_000 * 2 ** (failuresRef.current - 1), MAX_RETRY_DELAY_MS);
        setStatus(LIVE_STATUS.RECONNECTING);
        clearTimer(retryRef);
        retryRef.current = setTimeout(() => connect(), delay);
      },
    });

    // Backend emitter times out at 14 min; rotate before the 15 min JWT expiry.
    const rotateIn = millisecondsUntilTokenRefresh();
    if (rotateIn > 0) {
      clearTimer(expiryRef);
      expiryRef.current = setTimeout(() => {
        if (!aliveRef.current || generation !== generationRef.current) return;
        stopStream();
        failuresRef.current = 0;
        connect();
      }, rotateIn);
    }
  }, [applySnapshot, isAuthenticated, startPolling, stopStream]);

  const manualRefresh = useCallback(async () => {
    if (!isAuthenticated || !tokenManager.hasToken()) return;
    setRefreshing(true);
    try {
      const payload = await fetchSnapshot(filtersRef.current);
      applySnapshot(payload, pollingRef.current);
    } catch (err) {
      if (aliveRef.current) {
        setError(parseApiError(err).message);
        setStatus(LIVE_STATUS.ERROR);
      }
    } finally {
      if (aliveRef.current) setRefreshing(false);
    }
  }, [applySnapshot, isAuthenticated]);

  useEffect(() => {
    aliveRef.current = true;
    if (!isAuthenticated || !tokenManager.hasToken()) {
      generationRef.current += 1;
      stopAll();
      setSnapshot(null);
      setLastUpdated(null);
      setError(null);
      setStatus(LIVE_STATUS.CONNECTING);
      return undefined;
    }

    generationRef.current += 1;
    failuresRef.current = 0;
    // Never show data for a different filter set, account or organization.
    setSnapshot(null);
    setLastUpdated(null);
    setError(null);
    stopAll();
    connect();

    return () => {
      aliveRef.current = false;
      generationRef.current += 1;
      stopAll();
    };
    // identityKey changes when the account or organization changes.
  }, [filtersKey, identityKey, isAuthenticated, connect, stopAll]);

  return {
    snapshot,
    status,
    lastUpdated,
    error,
    refreshing,
    manualRefresh,
  };
}
