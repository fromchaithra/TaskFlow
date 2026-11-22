package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the development-only sample employee seeder.
 *
 * <p>Only bound when the {@code dev} profile is active; production profiles never
 * see this block because it lives in {@code application-dev.yml}.</p>
 */
@ConfigurationProperties(prefix = "taskflow.dev-seed")
public record DevEmployeeSeedProperties(
        boolean enabled,
        String password
) {
}
