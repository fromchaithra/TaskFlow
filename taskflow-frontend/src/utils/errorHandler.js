/**
 * Parses backend ApiErrorResponse payloads:
 * {
 *   timestamp: string,
 *   status: number,
 *   error: string,
 *   message: string,
 *   path: string,
 *   fieldErrors: Array<{ field: string, message: string }>
 * }
 */
export function parseApiError(err) {
  if (!err) {
    return {
      status: 0,
      code: 'UNKNOWN_ERROR',
      message: 'An unexpected error occurred. Please try again.',
      fieldErrors: {},
      isNetworkError: false,
    };
  }

  // Axios network or CORS failure (no HTTP response received)
  if (!err.response) {
    const isTimeout = err.code === 'ECONNABORTED';
    return {
      status: 0,
      code: isTimeout ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR',
      message: isTimeout
        ? 'The request timed out while contacting the TaskFlow server.'
        : 'Unable to reach the TaskFlow server. Verify the backend is running and accessible.',
      fieldErrors: {},
      isNetworkError: true,
    };
  }

  const { status, data } = err.response;
  const fieldErrors = {};

  if (Array.isArray(data?.fieldErrors)) {
    for (const violation of data.fieldErrors) {
      if (violation?.field && violation?.message && !fieldErrors[violation.field]) {
        fieldErrors[violation.field] = violation.message;
      }
    }
  }

  const code = data?.error || `HTTP_${status}`;
  let message = data?.message;

  if (!message) {
    switch (status) {
      case 400:
        message = 'Please review the submitted fields and try again.';
        break;
      case 401:
        message = 'Authentication is required or your session has expired.';
        break;
      case 403:
        message = 'You do not have permission to perform this action.';
        break;
      case 404:
        message = 'The requested resource could not be found.';
        break;
      case 409:
        message = 'This action conflicts with the current state of the resource.';
        break;
      case 422:
        message = 'The request violated an application workflow rule.';
        break;
      default:
        message = status >= 500
          ? 'The server encountered an internal error. Please try again shortly.'
          : 'Request failed. Please try again.';
    }
  }

  return {
    status,
    code,
    message,
    fieldErrors,
    isNetworkError: false,
  };
}
