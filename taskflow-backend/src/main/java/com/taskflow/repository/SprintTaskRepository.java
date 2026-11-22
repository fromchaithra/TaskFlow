package com.taskflow.repository;

import com.taskflow.entity.SprintTask;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SprintTaskRepository extends JpaRepository<SprintTask, Long> {

    Optional<SprintTask> findBySprintIdAndTaskId(Long sprintId, Long taskId);

    @EntityGraph(attributePaths = {"task", "task.assignee", "task.reporter", "task.project", "task.sprint", "addedBy"})
    List<SprintTask> findBySprintIdAndRemovedAtIsNull(Long sprintId);

    long countBySprintIdAndRemovedAtIsNull(Long sprintId);

    @Query("SELECT st.sprint.id, COUNT(st.id) FROM SprintTask st WHERE st.sprint.id IN :sprintIds AND st.removedAt IS NULL GROUP BY st.sprint.id")
    List<Object[]> countActiveTasksBySprintIds(@Param("sprintIds") Collection<Long> sprintIds);

    void deleteByTaskId(Long taskId);

    @Query("""
            SELECT new com.taskflow.dto.analytics.projections.SprintTaskAnalyticsProjection(
                st.sprint.id, st.task.id, st.addedAt, st.removedAt,
                CAST(st.statusAtSprintEnd AS string), CAST(t.status AS string), t.assignee.id)
            FROM SprintTask st JOIN st.task t
            WHERE st.sprint.id IN :sprintIds
              AND (:applyUserFilter = false OR t.assignee.id IN :userIds)
            """)
    List<com.taskflow.dto.analytics.projections.SprintTaskAnalyticsProjection> findAnalyticsSprintTasks(
            @Param("sprintIds") Collection<Long> sprintIds,
            @Param("applyUserFilter") Boolean applyUserFilter,
            @Param("userIds") Collection<Long> userIds
    );
}
