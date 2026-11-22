package com.taskflow.repository;

import com.taskflow.entity.ProjectMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    @EntityGraph(attributePaths = {"user", "addedBy"})
    List<ProjectMember> findByProjectIdOrderByJoinedAtAsc(Long projectId);

    long countByProjectId(Long projectId);

    @Query("SELECT pm.project.id, COUNT(pm.id) FROM ProjectMember pm WHERE pm.project.id IN :projectIds GROUP BY pm.project.id")
    List<Object[]> countMembersByProjectIds(@Param("projectIds") Collection<Long> projectIds);
}
