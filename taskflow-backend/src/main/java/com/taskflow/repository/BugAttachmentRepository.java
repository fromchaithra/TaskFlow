package com.taskflow.repository;

import com.taskflow.entity.BugAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BugAttachmentRepository extends JpaRepository<BugAttachment, Long> {

    @EntityGraph(attributePaths = {"uploadedBy"})
    List<BugAttachment> findByBugIdOrderByCreatedAtDesc(Long bugId);

    @EntityGraph(attributePaths = {"bug", "bug.project", "uploadedBy"})
    Optional<BugAttachment> findDetailById(Long id);

    long countByBugId(Long bugId);

    void deleteByBugId(Long bugId);
}
