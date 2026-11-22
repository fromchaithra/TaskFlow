package com.taskflow.repository;

import com.taskflow.entity.BugHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface BugHistoryRepository extends JpaRepository<BugHistory, Long> {

    @EntityGraph(attributePaths = {"changedBy"})
    List<BugHistory> findByBugIdOrderByCreatedAtDesc(Long bugId);

    void deleteByBugId(Long bugId);

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.StatusEventProjection(bh.bug.id, bh.createdAt, bh.newValue)
            FROM BugHistory bh
            WHERE LOWER(bh.fieldName) = 'status'
              AND bh.createdAt >= :from
              AND bh.createdAt < :toExclusive
              AND bh.bug.project.organization.id = :orgId
              AND bh.bug.project.id IN :projectIds
              AND (:applyUserFilter = false OR bh.bug.assignee.id IN :userIds OR bh.bug.reporter.id IN :userIds)
            """)
    List<com.taskflow.dto.analytics.projections.StatusEventProjection> findAnalyticsBugStatusEvents(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.BugResolutionProjection(
                bh.bug.id, b.createdAt, bh.createdAt)
            FROM BugHistory bh JOIN bh.bug b
            WHERE LOWER(bh.fieldName) = 'status'
              AND bh.newValue IN ('RESOLVED', 'VERIFIED')
              AND bh.createdAt >= :from
              AND bh.createdAt < :toExclusive
              AND b.project.organization.id = :orgId
              AND b.project.id IN :projectIds
              AND (:applyUserFilter = false OR b.assignee.id IN :userIds OR b.reporter.id IN :userIds)
            """)
    List<com.taskflow.dto.analytics.projections.BugResolutionProjection> findAnalyticsBugResolutions(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );
}
