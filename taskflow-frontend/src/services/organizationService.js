import { apiClient } from './apiClient';

/**
 * Organization service mapped to com.taskflow.controller.OrganizationController:
 * - GET /api/v1/organization (all authenticated roles)
 * - PUT /api/v1/organization (ADMIN only)
 */
export const organizationService = {
  async getCurrentOrganization() {
    const response = await apiClient.get('/organization');
    return response.data;
  },

  async updateCurrentOrganization({ name, industry, timezone, workingDays }) {
    const payload = {
      name: name.trim(),
      industry: industry && industry.trim() ? industry.trim() : null,
      timezone: timezone.trim(),
      workingDays: workingDays.trim().toUpperCase(),
    };
    const response = await apiClient.put('/organization', payload);
    return response.data;
  },
};
