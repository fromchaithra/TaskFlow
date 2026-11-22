ALTER TABLE task_history ADD COLUMN completed_assignee_id BIGINT UNSIGNED NULL;

ALTER TABLE task_history
    ADD CONSTRAINT fk_task_history_completed_assignee
    FOREIGN KEY (completed_assignee_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_task_history_completed_assignee ON task_history (completed_assignee_id, created_at);
