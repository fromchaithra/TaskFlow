package com.taskflow.config;

import com.taskflow.dto.setup.FirstAdminRegistrationRequest;
import com.taskflow.dto.setup.SetupStatusResponse;
import com.taskflow.service.FirstAdminProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapAdminRunnerTest {

    private FirstAdminProvisioningService provisioningService;
    private BootstrapAdminRunner runner;

    @BeforeEach
    void setUp() {
        provisioningService = mock(FirstAdminProvisioningService.class);
        BootstrapAdminProperties properties =
                new BootstrapAdminProperties("admin@taskflow.test", "StrongPass123!", "Configured Admin");
        runner = new BootstrapAdminRunner(properties, provisioningService);
    }

    @Test
    void bootstrapsThroughSharedServiceWhenNoAdminExists() {
        when(provisioningService.getSetupStatus()).thenReturn(new SetupStatusResponse(false, true));

        runner.run(new DefaultApplicationArguments());

        verify(provisioningService).registerFirstAdmin(any(FirstAdminRegistrationRequest.class));
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        // Public registration stays open, but bootstrap must not create a duplicate admin.
        when(provisioningService.getSetupStatus()).thenReturn(new SetupStatusResponse(true, true));

        runner.run(new DefaultApplicationArguments());

        verify(provisioningService, never()).registerFirstAdmin(any());
    }

    @Test
    void doesNothingWhenBootstrapCredentialsAreAbsent() {
        BootstrapAdminRunner noCredentials = new BootstrapAdminRunner(
                new BootstrapAdminProperties("", "", ""), provisioningService);

        noCredentials.run(new DefaultApplicationArguments());

        verify(provisioningService, never()).getSetupStatus();
        verify(provisioningService, never()).registerFirstAdmin(any());
    }
}
