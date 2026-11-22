package com.taskflow.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fails fast when the prod profile is used with missing or insecure settings.
 *
 * <p>Production datasource placeholders already have no localhost defaults,
 * but this guard turns silent misconfiguration into an explicit startup error
 * and prevents DB_SSL_MODE from falling back to PREFERRED/DISABLED.</p>
 */
@Component
@Profile("prod")
public class ProdConfigurationGuard {

    private static final Set<String> SECURE_SSL_MODES = Set.of("REQUIRED", "VERIFY_CA");
    private static final List<String> REQUIRED_SETTINGS = List.of(
            "DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD",
            "JWT_SECRET", "CORS_ALLOWED_ORIGINS");

    public ProdConfigurationGuard(Environment environment) {
        List<String> missing = REQUIRED_SETTINGS.stream()
                .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required production environment variables: " + String.join(", ", missing));
        }

        String sslMode = environment.getProperty("DB_SSL_MODE", "REQUIRED").toUpperCase(Locale.ROOT);
        if (!SECURE_SSL_MODES.contains(sslMode)) {
            throw new IllegalStateException(
                    "DB_SSL_MODE must be REQUIRED or VERIFY_CA for production.");
        }
    }
}
