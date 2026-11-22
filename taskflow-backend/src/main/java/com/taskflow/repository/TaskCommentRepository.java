package com.taskflow.repository;

import com.taskflow.entity.TaskComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    @EntityGraph(attributePaths = {"author"})
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    @EntityGraph(attributePaths = {"author", "task", "task.project"})
    Optional<TaskComment> findDetailById(Long id);

    long countByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);
}
