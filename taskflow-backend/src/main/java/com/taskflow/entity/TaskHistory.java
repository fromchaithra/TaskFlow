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

import java.time.Instant;

@Entity
@Table(name = "task_history")
public class TaskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    /** Assignee at the moment of a DONE transition; NULL for other events or legacy rows. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_assignee_id")
    private User completedAssignee;

    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    @Column(name = "old_value", length = 255)
    private String oldValue;

    @Column(name = "new_value", length = 255)
    private String newValue;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TaskHistory() {
    }

    public TaskHistory(
            Task task,
            User changedBy,
            String fieldName,
            String oldValue,
            String newValue,
            String remarks
    ) {
        this(task, changedBy, null, fieldName, oldValue, newValue, remarks);
    }

    public TaskHistory(
            Task task,
            User changedBy,
            User completedAssignee,
            String fieldName,
            String oldValue,
            String newValue,
            String remarks
    ) {
        this.task = task;
        this.changedBy = changedBy;
        this.completedAssignee = completedAssignee;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.remarks = remarks;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public User getCompletedAssignee() {
        return completedAssignee;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getRemarks() {
        return remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
