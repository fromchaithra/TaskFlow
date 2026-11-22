package com.taskflow.repository;

import com.taskflow.entity.TestCase;
import com.taskflow.enums.PriorityLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    @EntityGraph(attributePaths = {"project", "createdBy"})
    @Query("""
            SELECT tc FROM TestCase tc
            WHERE tc.project.organization.id = :orgId
              AND (:projectId IS NULL OR tc.project.id = :projectId)
              AND (:archived IS NULL OR tc.archived = :archived)
              AND (:priority IS NULL OR tc.priority = :priority)
              AND (:search IS NULL OR LOWER(tc.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(tc.testKey) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<TestCase> searchTestCases(
            @Param("orgId") Long organizationId,
            @Param("projectId") Long projectId,
            @Param("archived") Boolean archived,
            @Param("priority") PriorityLevel priority,
            @Param("search") String search,
            Pageable pageable
    );

    boolean existsByTestKey(String testKey);
}
