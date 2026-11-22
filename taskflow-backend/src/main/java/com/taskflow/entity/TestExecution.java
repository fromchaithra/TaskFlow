package com.taskflow.entity;

import com.taskflow.enums.TestExecutionResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "test_executions", uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "test_case_id"}))
public class TestExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private TestRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private TestExecutionResult result = TestExecutionResult.NOT_RUN;

    @Column(name = "actual_result", columnDefinition = "TEXT")
    private String actualResult;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_by")
    private User executedBy;

    @Column(name = "executed_at")
    private Instant executedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_id")
    private Bug bug;

    protected TestExecution() {}

    public TestExecution(TestRun run, TestCase testCase) {
        this.run = run;
        this.testCase = testCase;
        this.result = TestExecutionResult.NOT_RUN;
    }

    public Long getId() { return id; }
    public TestRun getRun() { return run; }
    public TestCase getTestCase() { return testCase; }
    public TestExecutionResult getResult() { return result; }
    public void setResult(TestExecutionResult result) { this.result = result; }
    public String getActualResult() { return actualResult; }
    public void setActualResult(String actualResult) { this.actualResult = actualResult; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public User getExecutedBy() { return executedBy; }
    public void setExecutedBy(User executedBy) { this.executedBy = executedBy; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public Bug getBug() { return bug; }
    public void setBug(Bug bug) { this.bug = bug; }
}
