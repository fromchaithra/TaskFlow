package com.taskflow.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileConfigurationTest {

    private static String resource(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    private static int exampleValue(String name) throws IOException {
        String example = resource(".env.example");
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(name) + "=(\\d+)\\s*$").matcher(example);
        assertThat(matcher.find()).as(name + " documented in .env.example").isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    @Test
    void globalConfigDoesNotImportLocalEnv() throws Exception {
        assertThat(resource("src/main/resources/application.yml"))
                .doesNotContain("optional:file:./.env")
                .doesNotContain("config:");
    }

    @Test
    void devProfileImportsLocalEnvForDevelopmentOnly() throws Exception {
        assertThat(resource("src/main/resources/application-dev.yml"))
                .contains("optional:file:./.env[.properties]")
                .contains("optional:file:./taskflow-backend/.env[.properties]");
    }

    @Test
    void prodProfileHasNoEnvImportAndRequiresExplicitSecureSettings() throws Exception {
        String prod = resource("src/main/resources/application-prod.yml");
        assertThat(prod)
                .doesNotContain(".env")
                .contains("jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}")
                .contains("sslMode=${DB_SSL_MODE:REQUIRED}")
                .contains("ddl-auto: validate");
    }

    @Test
    void localEnvIsGitignored() throws Exception {
        assertThat(resource(".gitignore")).contains(".env");
        assertThat(resource("../.gitignore")).contains(".env*");
    }

    @Test
    void reportEnvironmentVariablesAreDocumentedWithValidDefaults() throws Exception {
        assertThat(exampleValue("REPORT_MAX_BUCKETS")).isBetween(1, 500);
        assertThat(exampleValue("REPORT_MAX_ROWS")).isBetween(1, 100000);
        assertThat(exampleValue("REPORT_MAX_KPIS")).isBetween(1, 200);
        assertThat(exampleValue("REPORT_MAX_PDF_PAGES")).isBetween(1, 200);

        String application = resource("src/main/resources/application.yml");
        assertThat(application)
                .contains("${REPORT_MAX_BUCKETS:120}")
                .contains("${REPORT_MAX_ROWS:20000}")
                .contains("${REPORT_MAX_KPIS:50}")
                .contains("${REPORT_MAX_PDF_PAGES:50}");
    }

    @Test
    void exampleFileDoesNotContainRealSecretValues() throws Exception {
        String example = resource(".env.example");
        assertThat(example)
                .contains("CHANGE_ME", "REPLACE_WITH")
                .doesNotContain("Chaithra@123");
    }
}
