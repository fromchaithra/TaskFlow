package com.taskflow.repository;

import com.taskflow.entity.Sprint;
import com.taskflow.enums.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    boolean existsByProjectIdAndNameIgnoreCase(Long projectId, String name);

    boolean existsByProjectIdAndNameIgnoreCaseAndIdNot(Long projectId, String name, Long id);

    boolean existsByProjectIdAndStatus(Long projectId, SprintStatus status);

    boolean existsByProjectIdAndStatusAndIdNot(Long projectId, SprintStatus status, Long id);

    Optional<Sprint> findByIdAndProjectId(Long id, Long projectId);

    Optional<Sprint> findByProjectIdAndStatus(Long projectId, SprintStatus status);

    List<Sprint> findByProjectIdOrderByStartDateDesc(Long projectId);

    List<Sprint> findByProjectIdAndStatusOrderByStartDateDesc(Long projectId, SprintStatus status);

    @Query("""
            SELECT COUNT(s) > 0 FROM Sprint s
            WHERE s.project.id = :projectId
              AND s.status = com.taskflow.enums.SprintStatus.ACTIVE
              AND (:excludeId IS NULL OR s.id <> :excludeId)
              AND s.startDate <= :endDate
              AND s.endDate >= :startDate
            """)
    boolean existsOverlappingActiveSprint(
            @Param("projectId") Long projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            SELECT s FROM Sprint s
            WHERE s.project.organization.id = :orgId
              AND s.project.id IN :projectIds
              AND s.startDate <= :toDate
              AND s.endDate >= :fromDate
            """)
    List<Sprint> findAnalyticsSprints(
            @Param("orgId") Long organizationId,
            @Param("projectIds") Collection<Long> projectIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
