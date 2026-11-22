package com.taskflow.repository;

import com.taskflow.dto.analytics.projections.BugStatusCountProjection;
import com.taskflow.dto.analytics.projections.CountByKeyProjection;
import com.taskflow.dto.analytics.projections.StatusEventProjection;
import com.taskflow.entity.Bug;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BugRepository extends JpaRepository<Bug, Long> {

    @EntityGraph(attributePaths = {"project", "relatedTask", "reporter", "assignee"})
    Optional<Bug> findDetailById(Long id);

    @EntityGraph(attributePaths = {"project", "relatedTask", "reporter", "assignee"})
    @Query("""
            SELECT DISTINCT b FROM Bug b
            LEFT JOIN ProjectMember pm ON pm.project.id = b.project.id
            WHERE b.project.organization.id = :orgId
              AND (:isAdmin = true OR b.project.manager.id = :viewerId OR pm.user.id = :viewerId)
              AND (:projectId IS NULL OR b.project.id = :projectId)
              AND (:assigneeId IS NULL OR b.assignee.id = :assigneeId)
              AND (:severity IS NULL OR b.severity = :severity)
              AND (:status IS NULL OR b.status = :status)
              AND (:search IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(b.bugKey) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Bug> searchAuthorizedBugs(
            @Param("orgId") Long organizationId,
            @Param("viewerId") Long viewerId,
            @Param("isAdmin") boolean isAdmin,
            @Param("projectId") Long projectId,
            @Param("assigneeId") Long assigneeId,
            @Param("severity") BugSeverity severity,
            @Param("status") BugStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    boolean existsByProjectIdAndAssigneeIdAndStatusIn(Long projectId, Long assigneeId, Collection<BugStatus> statuses);

    long countByProjectIdAndStatusIn(Long projectId, Collection<BugStatus> statuses);

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.BugStatusCountProjection(b.project.id, CAST(b.status AS string), COUNT(b))
            FROM Bug b
            WHERE b.project.organization.id = :orgId
              AND b.project.id IN :projectIds
              AND (:applyUserFilter = false OR b.assignee.id IN :userIds OR b.reporter.id IN :userIds)
            GROUP BY b.project.id, b.status
            """)
    List<BugStatusCountProjection> countAnalyticsBugsByProjectAndStatus(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.StatusEventProjection(b.id, b.createdAt, null)
            FROM Bug b
            WHERE b.project.organization.id = :orgId
              AND b.project.id IN :projectIds
              AND b.createdAt >= :from
              AND b.createdAt < :toExclusive
              AND (:applyUserFilter = false OR b.assignee.id IN :userIds OR b.reporter.id IN :userIds)
            """)
    List<StatusEventProjection> findAnalyticsBugCreations(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.CountByKeyProjection(b.project.id, COUNT(b))
            FROM Bug b
            WHERE b.project.organization.id = :orgId
              AND b.project.id IN :projectIds
              AND b.severity = com.taskflow.enums.BugSeverity.CRITICAL
              AND b.status IN (com.taskflow.enums.BugStatus.OPEN,
                               com.taskflow.enums.BugStatus.ASSIGNED,
                               com.taskflow.enums.BugStatus.IN_PROGRESS,
                               com.taskflow.enums.BugStatus.REOPENED)
              AND (:applyUserFilter = false OR b.assignee.id IN :userIds OR b.reporter.id IN :userIds)
            GROUP BY b.project.id
            """)
    List<CountByKeyProjection> countAnalyticsCriticalBugsByProject(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.StringKeyCountProjection(CAST(b.severity AS string), COUNT(b))
            FROM Bug b
            WHERE b.project.organization.id = :orgId
              AND b.project.id IN :projectIds
              AND (:applyUserFilter = false OR b.assignee.id IN :userIds OR b.reporter.id IN :userIds)
            GROUP BY b.severity
            """)
    List<com.taskflow.dto.analytics.projections.StringKeyCountProjection> countAnalyticsBugsBySeverity(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );
}
