package com.taskflow.entity;

import com.taskflow.enums.TaskStatus;
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

import java.time.Instant;

@Entity
@Table(name = "sprint_tasks")
public class SprintTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sprint_id", nullable = false)
    private Sprint sprint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by", nullable = false)
    private User addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "removed_at")
    private Instant removedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_sprint_end", length = 20)
    private TaskStatus statusAtSprintEnd;

    protected SprintTask() {
    }

    public SprintTask(Sprint sprint, Task task, User addedBy) {
        this.sprint = sprint;
        this.task = task;
        this.addedBy = addedBy;
        this.addedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Sprint getSprint() {
        return sprint;
    }

    public Task getTask() {
        return task;
    }

    public User getAddedBy() {
        return addedBy;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public TaskStatus getStatusAtSprintEnd() {
        return statusAtSprintEnd;
    }

    public void setStatusAtSprintEnd(TaskStatus statusAtSprintEnd) {
        this.statusAtSprintEnd = statusAtSprintEnd;
    }
}
