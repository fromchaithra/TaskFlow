package com.taskflow.service;

import com.taskflow.dto.organization.OrganizationResponse;
import com.taskflow.dto.organization.UpdateOrganizationRequest;

public interface OrganizationService {

    OrganizationResponse getCurrentOrganization();

    OrganizationResponse updateCurrentOrganization(UpdateOrganizationRequest request);
}
