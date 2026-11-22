package com.taskflow.repository;

import com.taskflow.entity.TaskAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    @EntityGraph(attributePaths = {"uploadedBy"})
    List<TaskAttachment> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    @EntityGraph(attributePaths = {"task", "task.project", "uploadedBy"})
    Optional<TaskAttachment> findDetailById(Long id);

    long countByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);
}
