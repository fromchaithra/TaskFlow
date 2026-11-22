package com.taskflow.repository;

import com.taskflow.dto.analytics.projections.AuditActivityProjection;
import com.taskflow.entity.AuditLog;
import com.taskflow.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @EntityGraph(attributePaths = {"actor", "project"})
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.organization.id = :orgId
              AND (:projectId IS NULL OR a.project.id = :projectId)
              AND (:actorId IS NULL OR a.actor.id = :actorId)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> searchAuditLogs(
            @Param("orgId") Long organizationId,
            @Param("projectId") Long projectId,
            @Param("actorId") Long actorId,
            @Param("action") AuditAction action,
            @Param("entityType") String entityType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"actor", "project"})
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.organization.id = :orgId
              AND (:admin = true OR a.project.id IN :projectIds)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> searchAuthorizedAuditLogs(
            @Param("orgId") Long organizationId,
            @Param("admin") boolean admin,
            @Param("projectIds") Collection<Long> projectIds,
            Pageable pageable
    );

    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.organization.id = :orgId
              AND a.action = com.taskflow.enums.AuditAction.PROJECT_STATUS_CHANGED
              AND a.project.id IN :projectIds
              AND a.createdAt >= :from
              AND a.createdAt < :toExclusive
            """)
    List<AuditLog> findAnalyticsProjectStatusChanges(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive
    );

    @Query(value = """
            SELECT CAST(a.created_at AS DATE) AS bucket_day,
                   a.action AS action,
                   COUNT(*) AS eventCount
            FROM audit_logs a
            WHERE a.organization_id = :orgId
              AND a.project_id IN (:projectIds)
              AND a.created_at >= :from
              AND a.created_at < :toExclusive
              AND a.action IN (:actions)
            GROUP BY CAST(a.created_at AS DATE), a.action
            ORDER BY bucket_day, action
            """, nativeQuery = true)
    List<AuditActivityProjection> aggregateAuditActivity(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("actions") Collection<String> actions
    );

    @Query(value = """
            SELECT CAST(a.created_at AS DATE) AS bucket_day,
                   a.action AS action,
                   COUNT(*) AS eventCount
            FROM audit_logs a
            WHERE a.organization_id = :orgId
              AND a.project_id IN (:projectIds)
              AND a.created_at >= :from
              AND a.created_at < :toExclusive
              AND a.actor_id IN (:userIds)
              AND a.action IN (:actions)
            GROUP BY CAST(a.created_at AS DATE), a.action
            ORDER BY bucket_day, action
            """, nativeQuery = true)
    List<AuditActivityProjection> aggregateAuditActivityForUsers(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("userIds") Collection<Long> userIds,
            @Param("actions") Collection<String> actions
    );
}
