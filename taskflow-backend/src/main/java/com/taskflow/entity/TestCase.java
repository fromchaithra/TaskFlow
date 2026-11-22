package com.taskflow.entity;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_cases")
public class TestCase extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "test_key", nullable = false, unique = true, length = 32, updatable = false)
    private String testKey;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "preconditions", columnDefinition = "TEXT")
    private String preconditions;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private PriorityLevel priority = PriorityLevel.MEDIUM;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @OneToMany(mappedBy = "testCase", fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<TestStep> steps = new ArrayList<>();

    protected TestCase() {}

    public TestCase(Organization organization, Project project, String testKey, String title, User createdBy) {
        this.organization = organization;
        this.project = project;
        this.testKey = testKey;
        this.title = title;
        this.createdBy = createdBy;
        this.priority = PriorityLevel.MEDIUM;
        this.archived = false;
    }

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public Project getProject() { return project; }
    public String getTestKey() { return testKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPreconditions() { return preconditions; }
    public void setPreconditions(String preconditions) { this.preconditions = preconditions; }
    public PriorityLevel getPriority() { return priority; }
    public void setPriority(PriorityLevel priority) { this.priority = priority; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public User getCreatedBy() { return createdBy; }
    public List<TestStep> getSteps() { return steps; }
}
