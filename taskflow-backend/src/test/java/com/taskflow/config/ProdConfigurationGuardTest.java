package com.taskflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdConfigurationGuardTest {

    private static MockEnvironment completeEnvironment(String sslMode) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DB_HOST", "aiven-host.invalid");
        environment.setProperty("DB_PORT", "3306");
        environment.setProperty("DB_NAME", "taskflow");
        environment.setProperty("DB_USERNAME", "prod_user");
        environment.setProperty("DB_PASSWORD", "not-a-real-secret");
        environment.setProperty("JWT_SECRET", "not-a-real-jwt-secret");
        environment.setProperty("CORS_ALLOWED_ORIGINS", "https://app.example.com");
        if (sslMode != null) {
            environment.setProperty("DB_SSL_MODE", sslMode);
        }
        return environment;
    }

    @Test
    void acceptsRequiredSslMode() {
        assertThatCode(() -> new ProdConfigurationGuard(completeEnvironment("REQUIRED")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsVerifyCaSslModeCaseInsensitively() {
        assertThatCode(() -> new ProdConfigurationGuard(completeEnvironment("verify_ca")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsSecureDefaultWhenSslModeIsAbsent() {
        assertThatCode(() -> new ProdConfigurationGuard(completeEnvironment(null)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPreferredSslMode() {
        assertThatThrownBy(() -> new ProdConfigurationGuard(completeEnvironment("PREFERRED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_SSL_MODE must be REQUIRED or VERIFY_CA");
    }

    @Test
    void rejectsDisabledSslMode() {
        assertThatThrownBy(() -> new ProdConfigurationGuard(completeEnvironment("DISABLED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_SSL_MODE must be REQUIRED or VERIFY_CA");
    }

    @Test
    void missingProductionVariablesFailClearly() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DB_HOST", "aiven-host.invalid");

        assertThatThrownBy(() -> new ProdConfigurationGuard(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required production environment variables")
                .hasMessageContaining("DB_PORT")
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void errorMessagesNeverIncludeSecretValues() {
        assertThatThrownBy(() -> new ProdConfigurationGuard(completeEnvironment("PREFERRED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not-a-real-secret")
                .hasMessageNotContaining("not-a-real-jwt-secret");
    }
}
