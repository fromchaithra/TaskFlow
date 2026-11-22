package com.taskflow.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounded request limits for public authentication/registration endpoints.
 *
 * <p>The implementation is process-local. A multi-instance production deployment
 * must replace the in-memory store with a shared one (see RateLimitFilter).</p>
 */
@Validated
@ConfigurationProperties(prefix = "taskflow.rate-limit")
public record RateLimitProperties(

        @Min(value = 1, message = "login-per-ip-limit must be at least 1")
        @Max(value = 10000, message = "login-per-ip-limit must not exceed 10000")
        int loginPerIpLimit,

        @Min(value = 1, message = "login-failures-per-account-limit must be at least 1")
        @Max(value = 100, message = "login-failures-per-account-limit must not exceed 100")
        int loginFailuresPerAccountLimit,

        @Min(value = 1, message = "refresh-per-ip-limit must be at least 1")
        @Max(value = 10000, message = "refresh-per-ip-limit must not exceed 10000")
        int refreshPerIpLimit,

        @Min(value = 1, message = "activation-per-ip-limit must be at least 1")
        @Max(value = 10000, message = "activation-per-ip-limit must not exceed 10000")
        int activationPerIpLimit,

        @Min(value = 1, message = "registration-per-ip-limit must be at least 1")
        @Max(value = 10000, message = "registration-per-ip-limit must not exceed 10000")
        int registrationPerIpLimit,

        @Min(value = 1, message = "window-seconds must be at least 1")
        @Max(value = 86400, message = "window-seconds must not exceed 86400")
        int windowSeconds
) {
}
