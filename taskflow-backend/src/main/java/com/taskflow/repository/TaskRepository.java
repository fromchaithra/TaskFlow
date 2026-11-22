package com.taskflow.repository;

import com.taskflow.dto.analytics.projections.CountByKeyProjection;
import com.taskflow.dto.analytics.projections.DailyCountProjection;
import com.taskflow.dto.analytics.projections.TaskStatusCountProjection;
import com.taskflow.dto.analytics.projections.WorkloadProjection;
import com.taskflow.entity.Task;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {"project", "assignee", "reporter", "sprint", "parentTask"})
    Optional<Task> findDetailById(Long id);

    @EntityGraph(attributePaths = {"assignee", "reporter", "sprint", "project"})
    List<Task> findByParentTaskIdOrderByCreatedAtAsc(Long parentTaskId);

    @EntityGraph(attributePaths = {"assignee", "reporter", "sprint", "project"})
    @Query("""
            SELECT t FROM Task t
            WHERE t.project.id = :projectId
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)
              AND (:sprintId IS NULL OR t.sprint.id = :sprintId)
              AND (:search IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.taskKey) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Task> searchProjectTasks(
            @Param("projectId") Long projectId,
            @Param("status") TaskStatus status,
            @Param("priority") PriorityLevel priority,
            @Param("assigneeId") Long assigneeId,
            @Param("sprintId") Long sprintId,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"assignee", "reporter", "sprint", "project"})
    @Query("""
            SELECT DISTINCT t FROM Task t
            LEFT JOIN ProjectMember pm ON pm.project.id = t.project.id
            WHERE t.project.organization.id = :orgId
              AND (:isAdmin = true OR t.project.manager.id = :viewerId OR pm.user.id = :viewerId)
              AND (:projectId IS NULL OR t.project.id = :projectId)
              AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:sprintId IS NULL OR t.sprint.id = :sprintId)
              AND (:overdueOnly = false OR (t.dueDate < :today AND t.status <> com.taskflow.enums.TaskStatus.DONE))
              AND (:search IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.taskKey) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Task> searchAuthorizedTasks(
            @Param("orgId") Long organizationId,
            @Param("viewerId") Long viewerId,
            @Param("isAdmin") boolean isAdmin,
            @Param("projectId") Long projectId,
            @Param("assigneeId") Long assigneeId,
            @Param("status") TaskStatus status,
            @Param("priority") PriorityLevel priority,
            @Param("sprintId") Long sprintId,
            @Param("overdueOnly") boolean overdueOnly,
            @Param("today") LocalDate today,
            @Param("search") String search,
            Pageable pageable
    );

    List<Task> findBySprintId(Long sprintId);

    boolean existsByProjectIdAndAssigneeIdAndStatusIn(Long projectId, Long assigneeId, Collection<TaskStatus> statuses);

    long countByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.project.id = :projectId
              AND t.dueDate < :today
              AND t.status <> com.taskflow.enums.TaskStatus.DONE
            """)
    long countOverdueByProjectId(@Param("projectId") Long projectId, @Param("today") LocalDate today);

    @Query("""
            SELECT COUNT(DISTINCT t) FROM Task t
            LEFT JOIN ProjectMember pm ON pm.project.id = t.project.id
            WHERE t.project.organization.id = :orgId
              AND (:isAdmin = true OR t.project.manager.id = :viewerId OR pm.user.id = :viewerId)
              AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)
              AND t.dueDate = :today
              AND t.status <> com.taskflow.enums.TaskStatus.DONE
            """)
    long countDueToday(
            @Param("orgId") Long organizationId,
            @Param("viewerId") Long viewerId,
            @Param("isAdmin") boolean isAdmin,
            @Param("assigneeId") Long assigneeId,
            @Param("today") LocalDate today
    );

    @EntityGraph(attributePaths = {"assignee", "reporter", "sprint", "project"})
    @Query("""
            SELECT t FROM Task t
            WHERE t.project.id = :projectId
              AND t.status = :status
              AND (:sprintId IS NULL OR t.sprint.id = :sprintId)
              AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:search IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.taskKey) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY t.boardOrder ASC, t.createdAt DESC
            """)
    Page<Task> findKanbanColumnTasks(
            @Param("projectId") Long projectId,
            @Param("status") TaskStatus status,
            @Param("sprintId") Long sprintId,
            @Param("assigneeId") Long assigneeId,
            @Param("priority") PriorityLevel priority,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            SELECT COALESCE(MAX(t.boardOrder), -1) FROM Task t
            WHERE t.project.id = :projectId AND t.status = :status
            """)
    Integer findMaxBoardOrder(@Param("projectId") Long projectId, @Param("status") TaskStatus status);

    boolean existsByParentTaskId(Long parentTaskId);

    @EntityGraph(attributePaths = {"assignee", "project"})
    @Query("""
            SELECT t FROM Task t
            WHERE t.assignee IS NOT NULL
              AND t.dueDate IS NOT NULL
              AND t.dueDate <= :horizonDate
              AND t.status <> com.taskflow.enums.TaskStatus.DONE
            """)
    List<Task> findTasksWithUpcomingOrOverdueDeadlines(@Param("horizonDate") LocalDate horizonDate);

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.TaskStatusCountProjection(t.project.id, CAST(t.status AS string), COUNT(t))
            FROM Task t
            WHERE t.project.organization.id = :orgId
              AND t.project.id IN :projectIds
              AND (:applyUserFilter = false OR t.assignee.id IN :userIds)
            GROUP BY t.project.id, t.status
            """)
    List<TaskStatusCountProjection> countAnalyticsTasksByProjectAndStatus(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.CountByKeyProjection(t.project.id, COUNT(t))
            FROM Task t
            WHERE t.project.organization.id = :orgId
              AND t.project.id IN :projectIds
              AND t.dueDate < :today
              AND t.status <> com.taskflow.enums.TaskStatus.DONE
              AND (:applyUserFilter = false OR t.assignee.id IN :userIds)
            GROUP BY t.project.id
            """)
    List<CountByKeyProjection> countAnalyticsOverdueTasksByProject(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("today") LocalDate today,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.TaskStatusCountProjection(t.assignee.id, CAST(t.status AS string), COUNT(t))
            FROM Task t
            WHERE t.project.organization.id = :orgId
              AND t.assignee.id IN :userIds
            GROUP BY t.assignee.id, t.status
            """)
    List<TaskStatusCountProjection> countAnalyticsTasksByAssigneeAndStatus(
            @Param("orgId") Long organizationId,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.WorkloadProjection(
                t.assignee.id, CAST(t.status AS string), COUNT(t), SUM(t.estimatedHours))
            FROM Task t
            WHERE t.project.organization.id = :orgId
              AND t.assignee.id IN :userIds
            GROUP BY t.assignee.id, t.status
            """)
    List<WorkloadProjection> findAnalyticsWorkload(
            @Param("orgId") Long organizationId,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.CountByKeyProjection(t.assignee.id, COUNT(t))
            FROM Task t
            WHERE t.project.organization.id = :orgId
              AND t.assignee.id IN :userIds
              AND t.dueDate < :today
              AND t.status <> com.taskflow.enums.TaskStatus.DONE
            GROUP BY t.assignee.id
            """)
    List<CountByKeyProjection> countAnalyticsOverdueTasksByAssignee(
            @Param("orgId") Long organizationId,
            @Param("userIds") Collection<Long> userIds,
            @Param("today") LocalDate today
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.WorkloadProjection(
                t.assignee.id, CAST(t.status AS string), COUNT(t), SUM(t.estimatedHours))
            FROM Task t
            WHERE t.project.organization.id = :orgId
              AND t.project.id IN :projectIds
              AND t.assignee.id IN :userIds
            GROUP BY t.assignee.id, t.status
            """)
    List<WorkloadProjection> findAnalyticsWorkloadInProjects(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.StringKeyCountProjection(CAST(t.priority AS string), COUNT(t))
            FROM Task t
            WHERE t.project.organization.id = :orgId
              AND t.project.id IN :projectIds
              AND (:applyUserFilter = false OR t.assignee.id IN :userIds)
            GROUP BY t.priority
            """)
    List<com.taskflow.dto.analytics.projections.StringKeyCountProjection> countAnalyticsTasksByPriority(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );

    @Query(value = """
            SELECT CAST(t.created_at AS DATE) AS bucket_day,
                   COUNT(*) AS taskCount
            FROM tasks t
            JOIN projects p ON p.id = t.project_id
            WHERE p.organization_id = :orgId
              AND t.project_id IN (:projectIds)
              AND t.created_at >= :from
              AND t.created_at < :toExclusive
            GROUP BY CAST(t.created_at AS DATE)
            ORDER BY bucket_day
            """, nativeQuery = true)
    List<DailyCountProjection> countAnalyticsTaskCreationsByDay(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive
    );

    @Query(value = """
            SELECT CAST(t.created_at AS DATE) AS bucket_day,
                   COUNT(*) AS taskCount
            FROM tasks t
            JOIN projects p ON p.id = t.project_id
            WHERE p.organization_id = :orgId
              AND t.project_id IN (:projectIds)
              AND t.created_at >= :from
              AND t.created_at < :toExclusive
              AND t.assignee_id IN (:userIds)
            GROUP BY CAST(t.created_at AS DATE)
            ORDER BY bucket_day
            """, nativeQuery = true)
    List<DailyCountProjection> countAnalyticsTaskCreationsByDayForUsers(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.TaskStatusCountProjection(t.assignee.id, CAST(t.status AS string), COUNT(t))
            FROM Task t
            WHERE t.project.organization.id = :orgId
              AND t.project.id IN :projectIds
              AND t.assignee.id IN :userIds
            GROUP BY t.assignee.id, t.status
            """)
    List<TaskStatusCountProjection> countAnalyticsTasksByAssigneeAndStatusInProjects(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.CountByKeyProjection(t.assignee.id, COUNT(t))
            FROM Task t
            WHERE t.project.organization.id = :orgId
              AND t.project.id IN :projectIds
              AND t.assignee.id IN :userIds
              AND t.dueDate < :today
              AND t.status <> com.taskflow.enums.TaskStatus.DONE
            GROUP BY t.assignee.id
            """)
    List<CountByKeyProjection> countAnalyticsOverdueTasksByAssigneeInProjects(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("userIds") Collection<Long> userIds,
            @Param("today") LocalDate today
    );
}
