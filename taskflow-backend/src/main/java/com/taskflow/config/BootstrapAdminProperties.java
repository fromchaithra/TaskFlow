package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.bootstrap")
public record BootstrapAdminProperties(
        String adminEmail,
        String adminPassword,
        String adminName
) {
}
