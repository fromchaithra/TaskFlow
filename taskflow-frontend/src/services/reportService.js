import { apiClient } from './apiClient';
import { parseApiError } from '../utils/errorHandler';

/**
 * Reporting API integration.
 *
 * The backend catalog is the source of truth for available report types,
 * supported formats, and the Sprint Burndown sprint requirement. The PDF/XLSX
 * methods return the full Axios response so headers such as Content-Type and
 * Content-Disposition remain available to the H.3.4 download flow.
 */
function numericIds(ids) {
  if (!Array.isArray(ids)) return [];
  const unique = new Set();
  for (const id of ids) {
    const numericId = Number(id);
    if (Number.isInteger(numericId) && numericId > 0) unique.add(numericId);
  }
  return Array.from(unique);
}

/**
 * Builds the exact AnalyticsReportRequest contract. Optional comparison and
 * sprint fields are omitted entirely when they do not apply, so no unsupported
 * or misleading values are sent to the backend.
 */
export function buildReportRequest({ type, format, filters = {} }) {
  const {
    from,
    to,
    granularity = 'WEEK',
    comparisonEnabled = false,
    compareFrom,
    compareTo,
    projectIds = [],
    teamIds = [],
    employeeIds = [],
    sprintId,
  } = filters;

  const query = {
    from,
    to,
    granularity,
    projectIds: numericIds(projectIds),
    teamIds: numericIds(teamIds),
    employeeIds: numericIds(employeeIds),
  };

  if (comparisonEnabled && compareFrom && compareTo) {
    query.compareFrom = compareFrom;
    query.compareTo = compareTo;
  }

  const request = { type, format, query };

  if (type === 'SPRINT_BURNOWN') {
    const numericSprintId = Number(sprintId);
    if (Number.isInteger(numericSprintId) && numericSprintId > 0) {
      request.sprintId = numericSprintId;
    }
  }

  return request;
}

/**
 * Axios keeps responseType: 'arraybuffer' on error responses too, so the
 * backend ApiErrorResponse arrives as binary data. Decode it safely before
 * using the shared error parser; never surface raw payloads or stack traces.
 */
export async function parseReportError(error) {
  const data = error?.response?.data;
  let decoded = data;

  if (typeof ArrayBuffer !== 'undefined' && data instanceof ArrayBuffer) {
    try {
      decoded = JSON.parse(new TextDecoder().decode(data));
    } catch (decodeError) {
      decoded = null;
    }
  } else if (typeof Blob !== 'undefined' && data instanceof Blob) {
    try {
      decoded = JSON.parse(await data.text());
    } catch (decodeError) {
      decoded = null;
    }
  }

  if (decoded && typeof decoded === 'object' && decoded !== data) {
    return parseApiError({ ...error, response: { ...error.response, data: decoded } });
  }

  return parseApiError(error);
}

export const reportService = {
  buildReportRequest,
  parseReportError,

  async getCatalog() {
    const response = await apiClient.get('/reports/catalog');
    return response.data;
  },

  async generatePdf(request) {
    return apiClient.post('/reports/pdf', request, { responseType: 'arraybuffer' });
  },

  async generateXlsx(request) {
    return apiClient.post('/reports/xlsx', request, { responseType: 'arraybuffer' });
  },
};
