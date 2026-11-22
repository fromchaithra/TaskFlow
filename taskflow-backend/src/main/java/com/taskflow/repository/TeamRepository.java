package com.taskflow.repository;

import com.taskflow.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(Long organizationId, String name, Long id);

    @EntityGraph(attributePaths = {"leadUser"})
    Optional<Team> findWithLeadById(Long id);

    @EntityGraph(attributePaths = {"leadUser"})
    @Query("""
            SELECT t FROM Team t
            WHERE t.organization.id = :orgId
              AND (:active IS NULL OR t.active = :active)
              AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Team> searchTeams(
            @Param("orgId") Long organizationId,
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
