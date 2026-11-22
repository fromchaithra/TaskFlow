package com.taskflow.config;

import com.taskflow.dto.setup.FirstAdminRegistrationRequest;
import com.taskflow.service.FirstAdminProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Optional environment-based bootstrap for the first ADMIN.
 *
 * <p>Reconciled with public registration: both paths delegate to
 * {@link FirstAdminProvisioningService}. Bootstrap only runs when no ADMIN
 * exists at all, so it never provisions a duplicate installation-wide. Public
 * registration remains available regardless of existing organizations.</p>
 */
@Component
@Order(0)
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final BootstrapAdminProperties properties;
    private final FirstAdminProvisioningService provisioningService;

    public BootstrapAdminRunner(
            BootstrapAdminProperties properties,
            FirstAdminProvisioningService provisioningService
    ) {
        this.properties = properties;
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.adminEmail() == null || properties.adminEmail().isBlank()
                || properties.adminPassword() == null || properties.adminPassword().isBlank()) {
            return; // No bootstrap credentials configured; public setup remains available.
        }
        if (provisioningService.getSetupStatus().adminExists()) {
            return; // An administrator already exists; never bootstrap another.
        }

        String fullName = (properties.adminName() != null && !properties.adminName().isBlank())
                ? properties.adminName()
                : "System Administrator";
        String password = properties.adminPassword();
        provisioningService.registerFirstAdmin(new FirstAdminRegistrationRequest(
                fullName,
                properties.adminEmail(),
                password,
                password
        ));
        log.info("Bootstrapped initial ADMIN account from configured environment settings.");
    }
}
