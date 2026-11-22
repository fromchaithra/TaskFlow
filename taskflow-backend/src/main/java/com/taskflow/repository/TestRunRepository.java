package com.taskflow.repository;

import com.taskflow.entity.TestRun;
import com.taskflow.enums.TestRunStatus;
import org.springframework.data.domain.Page;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {

    @EntityGraph(attributePaths = {"project", "suite", "assignedTo", "createdBy"})
    @Query("""
            SELECT tr FROM TestRun tr
            WHERE tr.project.organization.id = :orgId
              AND (:projectId IS NULL OR tr.project.id = :projectId)
              AND (:status IS NULL OR tr.status = :status)
              AND (:assignedToId IS NULL OR tr.assignedTo.id = :assignedToId)
            """)
    Page<TestRun> searchTestRuns(
            @Param("orgId") Long organizationId,
            @Param("projectId") Long projectId,
            @Param("status") TestRunStatus status,
            @Param("assignedToId") Long assignedToId,
            Pageable pageable
    );

    @Query("""
            SELECT tr FROM TestRun tr
            WHERE tr.project.organization.id = :orgId
              AND tr.project.id IN :projectIds
              AND tr.createdAt >= :from
              AND tr.createdAt < :toExclusive
            """)
    java.util.List<TestRun> findAnalyticsQaRuns(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive
    );
}
