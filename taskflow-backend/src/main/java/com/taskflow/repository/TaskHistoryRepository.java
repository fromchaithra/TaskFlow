package com.taskflow.repository;

import com.taskflow.dto.analytics.projections.CountByKeyProjection;
import com.taskflow.dto.analytics.projections.StatusEventProjection;
import com.taskflow.dto.analytics.projections.TaskCompletionProjection;
import com.taskflow.entity.TaskHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

    @EntityGraph(attributePaths = {"changedBy"})
    List<TaskHistory> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    void deleteByTaskId(Long taskId);

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.TaskCompletionProjection(
                th.task.id, th.task.project.id, th.createdAt)
            FROM TaskHistory th
            WHERE LOWER(th.fieldName) = 'status'
              AND th.newValue = 'DONE'
              AND th.createdAt >= :from
              AND th.createdAt < :toExclusive
              AND th.task.project.organization.id = :orgId
              AND th.task.project.id IN :projectIds
              AND (:applyUserFilter = false OR th.task.assignee.id IN :userIds)
            """)
    List<TaskCompletionProjection> findAnalyticsTaskCompletions(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.StatusEventProjection(
                th.task.id, th.createdAt, th.newValue)
            FROM TaskHistory th
            WHERE LOWER(th.fieldName) = 'status'
              AND th.createdAt >= :from
              AND th.createdAt < :toExclusive
              AND th.task.id IN :taskIds
            """)
    List<com.taskflow.dto.analytics.projections.StatusEventProjection> findAnalyticsTaskStatusEvents(
            @Param("taskIds") Collection<Long> taskIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.CountByKeyProjection(th.completedAssignee.id, COUNT(DISTINCT th.task.id))
            FROM TaskHistory th
            WHERE LOWER(th.fieldName) = 'status'
              AND th.newValue = 'DONE'
              AND th.completedAssignee IS NOT NULL
              AND th.createdAt >= :from
              AND th.createdAt < :toExclusive
              AND th.task.project.organization.id = :orgId
              AND th.task.project.id IN :projectIds
              AND th.completedAssignee.id IN :userIds
            GROUP BY th.completedAssignee.id
            """)
    List<CountByKeyProjection> countAnalyticsTaskCompletionsByAssignee(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("userIds") Collection<Long> userIds
    );
}
