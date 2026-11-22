package com.taskflow.entity;

import com.taskflow.enums.AuditAction;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_value", columnDefinition = "json")
    private Map<String, Object> previousValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "json")
    private Map<String, Object> newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(
            Organization organization,
            User actor,
            Project project,
            AuditAction action,
            String entityType,
            Long entityId,
            Map<String, Object> previousValue,
            Map<String, Object> newValue,
            String ipAddress
    ) {
        this.organization = organization;
        this.actor = actor;
        this.project = project;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.previousValue = previousValue != null ? new LinkedHashMap<>(previousValue) : null;
        this.newValue = newValue != null ? new LinkedHashMap<>(newValue) : null;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public User getActor() {
        return actor;
    }

    public Project getProject() {
        return project;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Map<String, Object> getPreviousValue() {
        return previousValue != null ? Collections.unmodifiableMap(previousValue) : null;
    }

    public Map<String, Object> getNewValue() {
        return newValue != null ? Collections.unmodifiableMap(newValue) : null;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
