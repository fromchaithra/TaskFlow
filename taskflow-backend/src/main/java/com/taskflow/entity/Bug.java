package com.taskflow.entity;

import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import com.taskflow.enums.PriorityLevel;
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
import jakarta.persistence.Version;

@Entity
@Table(name = "bugs")
public class Bug extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "bug_key", nullable = false, unique = true, length = 28, updatable = false)
    private String bugKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task relatedTask;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "steps_to_reproduce", columnDefinition = "TEXT")
    private String stepsToReproduce;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private BugSeverity severity = BugSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private PriorityLevel priority = PriorityLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BugStatus status = BugStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    protected Bug() {
    }

    public Bug(
            Project project,
            String bugKey,
            Task relatedTask,
            String title,
            String description,
            String stepsToReproduce,
            BugSeverity severity,
            PriorityLevel priority,
            User reporter,
            User assignee
    ) {
        this.project = project;
        this.bugKey = bugKey;
        this.relatedTask = relatedTask;
        this.title = title;
        this.description = description;
        this.stepsToReproduce = stepsToReproduce;
        this.severity = severity != null ? severity : BugSeverity.MEDIUM;
        this.priority = priority != null ? priority : PriorityLevel.MEDIUM;
        this.status = assignee != null ? BugStatus.ASSIGNED : BugStatus.OPEN;
        this.reporter = reporter;
        this.assignee = assignee;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getBugKey() {
        return bugKey;
    }

    public Task getRelatedTask() {
        return relatedTask;
    }

    public void setRelatedTask(Task relatedTask) {
        this.relatedTask = relatedTask;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStepsToReproduce() {
        return stepsToReproduce;
    }

    public void setStepsToReproduce(String stepsToReproduce) {
        this.stepsToReproduce = stepsToReproduce;
    }

    public BugSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(BugSeverity severity) {
        this.severity = severity;
    }

    public PriorityLevel getPriority() {
        return priority;
    }

    public void setPriority(PriorityLevel priority) {
        this.priority = priority;
    }

    public BugStatus getStatus() {
        return status;
    }

    public void setStatus(BugStatus status) {
        this.status = status;
    }

    public User getReporter() {
        return reporter;
    }

    public User getAssignee() {
        return assignee;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }

    public Long getVersion() {
        return version;
    }
}
