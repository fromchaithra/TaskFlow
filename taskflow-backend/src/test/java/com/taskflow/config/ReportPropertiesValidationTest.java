package com.taskflow.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPropertiesValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void configuredDefaultsAreValid() {
        ReportProperties properties = new ReportProperties(120, 20000, 50, 50);

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void nonPositiveLimitsAreRejected() {
        ReportProperties properties = new ReportProperties(0, 20000, 50, 50);

        assertThat(validator.validate(properties))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("maxBucketsPerReport");
    }

    @Test
    void limitsAboveMaximumAreRejected() {
        ReportProperties properties = new ReportProperties(501, 20000, 50, 200);

        assertThat(validator.validate(properties))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("maxBucketsPerReport");
    }

    @Test
    void pdfPageLimitIsValidated() {
        ReportProperties properties = new ReportProperties(120, 20000, 50, 0);

        assertThat(validator.validate(properties))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("maxPdfPages");
    }
}
