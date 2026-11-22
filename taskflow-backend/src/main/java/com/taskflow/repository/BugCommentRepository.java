package com.taskflow.repository;

import com.taskflow.entity.BugComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BugCommentRepository extends JpaRepository<BugComment, Long> {

    @EntityGraph(attributePaths = {"author"})
    List<BugComment> findByBugIdOrderByCreatedAtAsc(Long bugId);

    @EntityGraph(attributePaths = {"author", "bug", "bug.project"})
    Optional<BugComment> findDetailById(Long id);

    long countByBugId(Long bugId);

    void deleteByBugId(Long bugId);
}
