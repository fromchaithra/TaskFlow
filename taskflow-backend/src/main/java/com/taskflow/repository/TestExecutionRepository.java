package com.taskflow.repository;

import com.taskflow.dto.analytics.projections.QaResultProjection;
import com.taskflow.dto.analytics.projections.QaRunResultProjection;
import com.taskflow.dto.analytics.projections.StringKeyCountProjection;
import com.taskflow.entity.TestExecution;
import com.taskflow.enums.TestExecutionResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TestExecutionRepository extends JpaRepository<TestExecution, Long> {

    @EntityGraph(attributePaths = {"testCase", "executedBy", "bug"})
    List<TestExecution> findByRunIdOrderByTestCaseIdAsc(Long runId);

    @EntityGraph(attributePaths = {"run", "run.project", "executedBy", "bug"})
    List<TestExecution> findByTestCaseIdOrderByExecutedAtDesc(Long testCaseId);

    Optional<TestExecution> findByRunIdAndTestCaseId(Long runId, Long testCaseId);

    long countByRunIdAndResult(Long runId, TestExecutionResult result);

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.QaResultProjection(
                CAST(te.result AS string),
                CASE WHEN te.executedAt IS NOT NULL THEN te.executedAt ELSE tr.createdAt END)
            FROM TestExecution te JOIN te.run tr
            WHERE tr.project.organization.id = :orgId
              AND tr.project.id IN :projectIds
              AND ( (te.executedAt IS NOT NULL AND te.executedAt >= :from AND te.executedAt < :toExclusive)
                 OR (te.executedAt IS NULL AND tr.createdAt >= :from AND te.executedAt IS NULL AND tr.createdAt < :toExclusive) )
              AND (:applyUserFilter = false OR te.executedBy.id IN :userIds)
            """)
    List<QaResultProjection> findAnalyticsQaResults(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.QaRunResultProjection(te.run.id, CAST(te.result AS string), COUNT(te))
            FROM TestExecution te
            WHERE te.run.id IN :runIds
              AND (:applyUserFilter = false OR te.executedBy.id IN :userIds)
            GROUP BY te.run.id, te.result
            """)
    List<QaRunResultProjection> countAnalyticsQaResultsByRun(
            @Param("runIds") Collection<Long> runIds,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.StringKeyCountProjection(CAST(te.result AS string), COUNT(te))
            FROM TestExecution te
            WHERE te.run.project.organization.id = :orgId
            GROUP BY te.result
            """)
    List<StringKeyCountProjection> countDashboardExecutionsByResult(@Param("orgId") Long organizationId);

    @Query("""
            SELECT COUNT(te) FROM TestExecution te JOIN te.run tr
            WHERE tr.project.organization.id = :orgId
              AND tr.project.id IN :projectIds
              AND te.result = com.taskflow.enums.TestExecutionResult.FAIL
              AND te.bug IS NOT NULL
              AND ( (te.executedAt IS NOT NULL AND te.executedAt >= :from AND te.executedAt < :toExclusive)
                 OR (te.executedAt IS NULL AND tr.createdAt >= :from AND tr.createdAt < :toExclusive) )
              AND (:applyUserFilter = false OR te.executedBy.id IN :userIds)
            """)
    long countAnalyticsDefectLinkedFailures(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );
}
