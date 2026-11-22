package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "test_suite_cases")
@IdClass(TestSuiteCase.PK.class)
public class TestSuiteCase {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suite_id", nullable = false)
    private TestSuite suite;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    protected TestSuiteCase() {}

    public TestSuiteCase(TestSuite suite, TestCase testCase) {
        this.suite = suite;
        this.testCase = testCase;
        this.addedAt = Instant.now();
    }

    public TestSuite getSuite() { return suite; }
    public TestCase getTestCase() { return testCase; }
    public Instant getAddedAt() { return addedAt; }

    public static class PK implements Serializable {
        private Long suite;
        private Long testCase;

        public PK() {}

        public PK(Long suite, Long testCase) {
            this.suite = suite;
            this.testCase = testCase;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK other)) return false;
            return Objects.equals(suite, other.suite) && Objects.equals(testCase, other.testCase);
        }

        @Override
        public int hashCode() {
            return Objects.hash(suite, testCase);
        }
    }
}
