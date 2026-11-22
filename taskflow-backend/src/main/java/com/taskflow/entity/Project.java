package com.taskflow.entity;

import com.taskflow.enums.ProjectStatus;
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

import java.time.LocalDate;

@Entity
@Table(name = "projects")
public class Project extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "project_key", nullable = false, length = 10, updatable = false)
    private String projectKey;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "task_sequence", nullable = false)
    private Integer taskSequence = 100;

    @Column(name = "bug_sequence", nullable = false)
    private Integer bugSequence = 100;

    protected Project() {
    }

    public Project(
            Organization organization,
            String projectKey,
            String name,
            String description,
            User manager,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.organization = organization;
        this.projectKey = projectKey.toUpperCase();
        this.name = name;
        this.description = description;
        this.manager = manager;
        this.status = ProjectStatus.ACTIVE;
        this.startDate = startDate;
        this.endDate = endDate;
        this.taskSequence = 100;
        this.bugSequence = 100;
    }

    public String nextTaskKey() {
        this.taskSequence = this.taskSequence + 1;
        return this.projectKey + "-" + this.taskSequence;
    }

    public String nextBugKey() {
        this.bugSequence = this.bugSequence + 1;
        return this.projectKey + "-BUG-" + this.bugSequence;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getManager() {
        return manager;
    }

    public void setManager(User manager) {
        this.manager = manager;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getTaskSequence() {
        return taskSequence;
    }

    public Integer getBugSequence() {
        return bugSequence;
    }
}
