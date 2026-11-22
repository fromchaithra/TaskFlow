package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_steps")
public class TestStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "action", nullable = false, columnDefinition = "TEXT")
    private String action;

    @Column(name = "expected_result", nullable = false, columnDefinition = "TEXT")
    private String expectedResult;

    protected TestStep() {}

    public TestStep(TestCase testCase, int stepOrder, String action, String expectedResult) {
        this.testCase = testCase;
        this.stepOrder = stepOrder;
        this.action = action;
        this.expectedResult = expectedResult;
    }

    public Long getId() { return id; }
    public TestCase getTestCase() { return testCase; }
    public int getStepOrder() { return stepOrder; }
    public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getExpectedResult() { return expectedResult; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
}
