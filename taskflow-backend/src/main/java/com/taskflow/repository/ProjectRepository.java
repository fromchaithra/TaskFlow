package com.taskflow.repository;

import com.taskflow.entity.Project;
import com.taskflow.enums.ProjectStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByOrganizationIdAndProjectKeyIgnoreCase(Long organizationId, String projectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"manager"})
    Optional<Project> findWithManagerById(Long id);

    @EntityGraph(attributePaths = {"manager"})
    @Query("""
            SELECT p FROM Project p
            WHERE p.organization.id = :orgId
              AND (:status IS NULL OR p.status = :status)
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.projectKey) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Project> searchAllProjects(
            @Param("orgId") Long organizationId,
            @Param("status") ProjectStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"manager"})
    @Query("""
            SELECT DISTINCT p FROM Project p
            LEFT JOIN ProjectMember pm ON pm.project.id = p.id
            WHERE p.organization.id = :orgId
              AND (p.manager.id = :userId OR pm.user.id = :userId)
              AND (:status IS NULL OR p.status = :status)
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.projectKey) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Project> searchMemberProjects(
            @Param("orgId") Long organizationId,
            @Param("userId") Long userId,
            @Param("status") ProjectStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    long countByOrganizationId(Long organizationId);

    long countByOrganizationIdAndStatus(Long organizationId, ProjectStatus status);

    long countByOrganizationIdAndManagerId(Long organizationId, Long managerId);

    java.util.List<Project> findByOrganizationIdAndCreatedAtBetween(
            Long organizationId, Instant from, Instant toExclusive);

    @EntityGraph(attributePaths = {"manager"})
    @Query("SELECT p FROM Project p WHERE p.organization.id = :orgId AND p.id IN :ids")
    java.util.List<Project> findAnalyticsProjectsByIds(
            @Param("orgId") Long organizationId,
            @Param("ids") Collection<Long> ids);
}
