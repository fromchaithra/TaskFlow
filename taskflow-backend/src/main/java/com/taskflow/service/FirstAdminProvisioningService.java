package com.taskflow.service;

import com.taskflow.dto.setup.FirstAdminRegistrationRequest;
import com.taskflow.dto.setup.SetupStatusResponse;
import com.taskflow.dto.user.UserResponse;

/** Public organization registration shared by the setup API and bootstrap runner. */
public interface FirstAdminProvisioningService {

    SetupStatusResponse getSetupStatus();

    UserResponse registerFirstAdmin(FirstAdminRegistrationRequest request);
}
