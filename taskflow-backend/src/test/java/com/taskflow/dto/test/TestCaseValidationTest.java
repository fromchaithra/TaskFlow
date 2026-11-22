package com.taskflow.dto.test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Boundary tests for test-case step list and nested field validation. */
class TestCaseValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static CreateTestCaseRequest.TestStepRequest step(String action, String expected) {
        return new CreateTestCaseRequest.TestStepRequest(action, expected);
    }

    private static CreateTestCaseRequest create(List<CreateTestCaseRequest.TestStepRequest> steps) {
        return new CreateTestCaseRequest(1L, "Valid title", null, null, null, steps);
    }

    @Test
    void emptyStepsAreAllowed() {
        assertThat(validator.validate(create(null))).isEmpty();
        assertThat(validator.validate(create(List.of()))).isEmpty();
    }

    @Test
    void normalAndMaximumStepCountsAreAccepted() {
        assertThat(validator.validate(create(List.of(step("action", "expected"))))).isEmpty();
        List<CreateTestCaseRequest.TestStepRequest> max = new ArrayList<>();
        for (int i = 0; i < 100; i++) max.add(step("a" + i, "e" + i));
        assertThat(validator.validate(create(max))).isEmpty();
    }

    @Test
    void stepCountBeyondMaximumIsRejected() {
        List<CreateTestCaseRequest.TestStepRequest> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) tooMany.add(step("a" + i, "e" + i));
        assertThat(validator.validate(create(tooMany)))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("steps"));
    }

    @Test
    void nestedActionAndExpectedResultLimitsAreEnforced() {
        String atLimit = "x".repeat(4000);
        assertThat(validator.validate(create(List.of(step(atLimit, atLimit))))).isEmpty();
        assertThat(validator.validate(create(List.of(step("x".repeat(4001), atLimit))))).isNotEmpty();
        assertThat(validator.validate(create(List.of(step(atLimit, "x".repeat(4001)))))).isNotEmpty();
        assertThat(validator.validate(create(List.of(step("", "expected"))))).isNotEmpty();
    }

    @Test
    void updateStepsAreValidatedToo() {
        UpdateTestCaseRequest atLimit = new UpdateTestCaseRequest(null, null, null, null, null,
                List.of(step("x".repeat(4000), "x".repeat(4000))));
        assertThat(validator.validate(atLimit)).isEmpty();

        UpdateTestCaseRequest oversized = new UpdateTestCaseRequest(null, null, null, null, null,
                List.of(step("x".repeat(4001), "ok")));
        assertThat(validator.validate(oversized)).isNotEmpty();

        List<CreateTestCaseRequest.TestStepRequest> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) tooMany.add(step("a", "e"));
        assertThat(validator.validate(new UpdateTestCaseRequest(null, null, null, null, null, tooMany)))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("steps"));
    }
}
