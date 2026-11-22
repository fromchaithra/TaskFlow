package com.taskflow.repository;

import com.taskflow.entity.TestSuite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {

    @EntityGraph(attributePaths = {"project", "createdBy"})
    @Query("""
            SELECT ts FROM TestSuite ts
            WHERE ts.project.organization.id = :orgId
              AND (:projectId IS NULL OR ts.project.id = :projectId)
              AND (:archived IS NULL OR ts.archived = :archived)
              AND (:search IS NULL OR LOWER(ts.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<TestSuite> searchTestSuites(
            @Param("orgId") Long organizationId,
            @Param("projectId") Long projectId,
            @Param("archived") Boolean archived,
            @Param("search") String search,
            Pageable pageable
    );
}
