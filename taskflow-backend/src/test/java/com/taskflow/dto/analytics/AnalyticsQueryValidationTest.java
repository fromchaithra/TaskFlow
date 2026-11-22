package com.taskflow.dto.analytics;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsQueryValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static AnalyticsQuery query(LocalDate from, LocalDate to,
                                         LocalDate compareFrom, LocalDate compareTo) {
        return new AnalyticsQuery(from, to, compareFrom, compareTo, null, null, null, null);
    }

    @Test
    void acceptsValidRangeAndDefaultsGranularity() {
        AnalyticsQuery query = query(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), null, null);

        assertThat(validator.validate(query)).isEmpty();
        assertThat(query.granularity()).isEqualTo(AnalyticsGranularity.WEEK);
        assertThat(query.projectIds()).isEmpty();
        assertThat(query.hasComparison()).isFalse();
    }

    @Test
    void acceptsValidComparisonPeriod() {
        AnalyticsQuery query = query(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));

        assertThat(validator.validate(query)).isEmpty();
        assertThat(query.hasComparison()).isTrue();
    }

    @Test
    void rejectsInvertedRange() {
        assertThat(validator.validate(query(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 1), null, null)))
                .isNotEmpty();
    }

    @Test
    void rejectsRangeLongerThanTwentyFourMonths() {
        assertThat(validator.validate(query(
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 2, 1), null, null)))
                .isNotEmpty();
    }

    @Test
    void rejectsPartialComparisonPeriod() {
        assertThat(validator.validate(query(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2025, 10, 1), null)))
                .isNotEmpty();
    }

    @Test
    void rejectsOverlappingComparisonPeriod() {
        assertThat(validator.validate(query(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 15))))
                .isNotEmpty();
    }

    @Test
    void rejectsMissingDates() {
        assertThat(validator.validate(query(null, LocalDate.of(2026, 3, 31), null, null)))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("from");
    }

    @Test
    void rejectsInvalidFilterValues() {
        AnalyticsQuery query = new AnalyticsQuery(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.MONTH,
                List.of(-1L), List.of(2L), List.of(3L));

        assertThat(validator.validate(query)).isNotEmpty();
    }

    @Test
    void rejectsOversizedProjectFilter() {
        AnalyticsQuery query = new AnalyticsQuery(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                null, null, AnalyticsGranularity.MONTH,
                java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList(),
                List.of(), List.of());

        assertThat(validator.validate(query)).isNotEmpty();
    }
}
