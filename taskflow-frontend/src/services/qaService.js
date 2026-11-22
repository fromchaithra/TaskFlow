import { apiClient } from './apiClient';

function cleanParams(params) {
  const result = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') result[key] = value;
  }
  return result;
}

export const qaService = {
  async listTestCases({ projectId, archived, priority, search, page = 0, size = 20, sortBy = 'createdAt', sortDir = 'desc' } = {}) {
    const params = cleanParams({ projectId, archived, priority, search, page, size, sortBy, sortDir });
    const response = await apiClient.get('/test-cases', { params });
    return response.data;
  },
  async getTestCase(id) {
    const response = await apiClient.get(`/test-cases/${id}`);
    return response.data;
  },
  async listTestCaseExecutions(id) {
    const response = await apiClient.get(`/test-cases/${id}/executions`);
    return response.data;
  },
  async createTestCase(payload) {
    const response = await apiClient.post('/test-cases', payload);
    return response.data;
  },
  async updateTestCase(id, payload) {
    const response = await apiClient.put(`/test-cases/${id}`, payload);
    return response.data;
  },
  async archiveTestCase(id) {
    await apiClient.delete(`/test-cases/${id}`);
  },

  async listSuites({ projectId, archived, search, page = 0, size = 20, sortBy = 'createdAt', sortDir = 'desc' } = {}) {
    const params = cleanParams({ projectId, archived, search, page, size, sortBy, sortDir });
    const response = await apiClient.get('/test-suites', { params });
    return response.data;
  },
  async getSuite(id) {
    const response = await apiClient.get(`/test-suites/${id}`);
    return response.data;
  },
  async createSuite(payload) {
    const response = await apiClient.post('/test-suites', payload);
    return response.data;
  },
  async updateSuite(id, payload) {
    const response = await apiClient.put(`/test-suites/${id}`, payload);
    return response.data;
  },
  async archiveSuite(id) {
    await apiClient.delete(`/test-suites/${id}`);
  },
  async addCaseToSuite(suiteId, caseId) {
    await apiClient.post(`/test-suites/${suiteId}/cases/${caseId}`);
  },
  async removeCaseFromSuite(suiteId, caseId) {
    await apiClient.delete(`/test-suites/${suiteId}/cases/${caseId}`);
  },

  async listRuns({ projectId, status, assignedToId, page = 0, size = 20, sortBy = 'createdAt', sortDir = 'desc' } = {}) {
    const params = cleanParams({ projectId, status, assignedToId, page, size, sortBy, sortDir });
    const response = await apiClient.get('/test-runs', { params });
    return response.data;
  },
  async getRun(id) {
    const response = await apiClient.get(`/test-runs/${id}`);
    return response.data;
  },
  async createRun(payload) {
    const response = await apiClient.post('/test-runs', payload);
    return response.data;
  },
  async updateRunStatus(id, status) {
    const response = await apiClient.patch(`/test-runs/${id}/status`, null, { params: { status } });
    return response.data;
  },

  async listExecutions(runId) {
    const response = await apiClient.get(`/test-runs/${runId}/executions`);
    return response.data;
  },
  async recordExecution(runId, executionId, payload) {
    const response = await apiClient.patch(`/test-runs/${runId}/executions/${executionId}`, payload);
    return response.data;
  },
  async createBugFromExecution(runId, executionId) {
    const response = await apiClient.post(`/test-runs/${runId}/executions/${executionId}/create-bug`);
    return response.data;
  },
};
