import { apiClient, API_BASE_URL, requestTokenRefresh } from './apiClient';
import { CSRF_HEADER_NAME, CSRF_HEADER_VALUE } from '../utils/constants';
import { tokenManager } from '../utils/tokenManager';

/**
 * Live Analytics transport.
 *
 * Uses fetch for SSE because native EventSource cannot send an Authorization
 * header. Authentication reuses the existing in-memory token store and the
 * single-flight refresh in apiClient - there is no second refresh mechanism.
 */

const STREAM_PATH = '/live/analytics';
const SNAPSHOT_PATH = '/live/analytics/snapshot';
const STALL_TIMEOUT_MS = 75_000;

/** Only the filter parameters documented by LiveAnalyticsController. */
export function buildLiveQuery(filters = {}) {
  const { from, to, granularity, projectIds = [], teamIds = [], employeeIds = [] } = filters;
  const params = new URLSearchParams();

  if (from) params.append('from', from);
  if (to) params.append('to', to);
  if (granularity) params.append('granularity', granularity);

  for (const [key, values] of [
    ['projectIds', projectIds],
    ['teamIds', teamIds],
    ['employeeIds', employeeIds],
  ]) {
    for (const id of Array.isArray(values) ? values : []) {
      const numeric = Number(id);
      if (Number.isInteger(numeric) && numeric > 0) params.append(key, String(numeric));
    }
  }
  return params;
}

/** REST fallback/manual refresh. Goes through apiClient's 401 + refresh flow. */
export async function fetchSnapshot(filters = {}, signal = undefined) {
  const query = buildLiveQuery(filters);
  const response = await apiClient.get(`${SNAPSHOT_PATH}?${query.toString()}`, {
    ...(signal ? { signal } : {}),
    headers: { Accept: 'application/json' },
  });
  return response.data;
}

/**
 * Opens the SSE stream and parses the backend's actual wire format:
 *   retry:3000
 *   :connected
 *   event:snapshot
 *   data:{...}
 *
 * onSnapshot(payload) is called for every snapshot frame.
 * onEnd(reason) is called once when the stream closes ('closed', 'stalled',
 * 'unauthorized', 'aborted' or 'error').
 * Returns a cleanup function that aborts the request and clears timers.
 */
export function openLiveStream({
  filters = {},
  onSnapshot = () => {},
  onStatus = () => {},
  onEnd = () => {},
} = {}) {
  const controller = new AbortController();
  const query = buildLiveQuery(filters);
  const url = `${API_BASE_URL}${STREAM_PATH}?${query.toString()}`;
  let disposed = false;
  let ended = false;
  let stallTimer = null;
  let reader = null;

  const finish = (reason) => {
    if (ended || disposed) return;
    ended = true;
    if (stallTimer) clearTimeout(stallTimer);
    onEnd(reason);
  };

  const touch = () => {
    if (stallTimer) clearTimeout(stallTimer);
    stallTimer = setTimeout(() => {
      controller.abort();
      finish('stalled');
    }, STALL_TIMEOUT_MS);
  };

  const handleFrame = (rawFrame) => {
    const frame = rawFrame.replace(/\r/g, '');
    let eventName = 'message';
    const dataLines = [];
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
      else if (line.startsWith(':')) touch(); // heartbeat comment
      // 'retry:' is a browser hint; fetch does not need to apply it.
    }
    touch();
    if (eventName !== 'snapshot' || dataLines.length === 0) return;
    try {
      onSnapshot(JSON.parse(dataLines.join('\n')));
    } catch {
      // A malformed frame must not tear down the stream; the next snapshot replaces it.
    }
  };

  const open = async (retryAfterRefresh = false) => {
    const token = tokenManager.getAccessToken();
    const headers = {
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
      [CSRF_HEADER_NAME]: CSRF_HEADER_VALUE,
    };

    let response;
    try {
      response = await fetch(url, {
        headers,
        credentials: 'include',
        signal: controller.signal,
      });
    } catch (error) {
      if (disposed || controller.signal.aborted) return;
      finish('error');
      return;
    }

    if (response.status === 401 && !retryAfterRefresh) {
      // Reuse the shared single-flight refresh; never implement a second one.
      try {
        await requestTokenRefresh();
        if (!disposed && !controller.signal.aborted) await open(true);
      } catch {
        if (!disposed) finish('unauthorized');
      }
      return;
    }

    if (!response.ok || !response.body) {
      finish(response.status === 401 ? 'unauthorized' : 'error');
      return;
    }

    touch();
    onStatus('opened');
    reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      while (!disposed) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let boundary = buffer.indexOf('\n\n');
        while (boundary !== -1) {
          const frame = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          handleFrame(frame);
          boundary = buffer.indexOf('\n\n');
        }
      }
    } catch {
      if (!disposed && !controller.signal.aborted) finish('error');
      return;
    }
    if (!disposed) finish('closed');
  };

  open();

  return () => {
    if (disposed) return;
    disposed = true;
    if (stallTimer) clearTimeout(stallTimer);
    reader?.cancel?.().catch(() => {});
    controller.abort();
  };
}

/** milliseconds until the current access token should be rotated (or 0). */
export function millisecondsUntilTokenRefresh() {
  const expiresAt = tokenManager.getTokenExpiresAt();
  if (!expiresAt) return 0;
  return Math.max(0, expiresAt - Date.now() - 45_000);
}
