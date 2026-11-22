-- =============================================================================
-- V3: Bugs, Bug Comments/Attachments/History, Notifications, and Audit Logs
-- =============================================================================

CREATE TABLE bugs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    bug_key VARCHAR(28) NOT NULL,
    task_id BIGINT UNSIGNED NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    steps_to_reproduce TEXT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reporter_id BIGINT UNSIGNED NOT NULL,
    assignee_id BIGINT UNSIGNED NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_bugs_key UNIQUE (bug_key),
    CONSTRAINT fk_bugs_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_bugs_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL,
    CONSTRAINT fk_bugs_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_bugs_assignee FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_bugs_project_status_severity (project_id, status, severity),
    INDEX idx_bugs_assignee_status (assignee_id, status),
    INDEX idx_bugs_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bug_comments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bug_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    content TEXT NOT NULL,
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bug_comments_bug FOREIGN KEY (bug_id) REFERENCES bugs (id) ON DELETE CASCADE,
    CONSTRAINT fk_bug_comments_author FOREIGN KEY (author_id) REFERENCES users (id),
    INDEX idx_bug_comments_bug_created (bug_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bug_attachments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bug_id BIGINT UNSIGNED NOT NULL,
    uploaded_by BIGINT UNSIGNED NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size_bytes BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_bug_attachments_stored UNIQUE (stored_filename),
    CONSTRAINT fk_bug_attachments_bug FOREIGN KEY (bug_id) REFERENCES bugs (id) ON DELETE CASCADE,
    CONSTRAINT fk_bug_attachments_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id),
    INDEX idx_bug_attachments_bug (bug_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bug_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bug_id BIGINT UNSIGNED NOT NULL,
    changed_by BIGINT UNSIGNED NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    old_value VARCHAR(255) NULL,
    new_value VARCHAR(255) NULL,
    remarks VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bug_history_bug FOREIGN KEY (bug_id) REFERENCES bugs (id) ON DELETE CASCADE,
    CONSTRAINT fk_bug_history_user FOREIGN KEY (changed_by) REFERENCES users (id),
    INDEX idx_bug_history_bug_created (bug_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(500) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT UNSIGNED NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_notifications_recipient_read_created (recipient_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_id BIGINT UNSIGNED NOT NULL,
    actor_id BIGINT UNSIGNED NULL,
    project_id BIGINT UNSIGNED NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT UNSIGNED NOT NULL,
    previous_value JSON NULL,
    new_value JSON NULL,
    ip_address VARCHAR(45) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_logs_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL,
    INDEX idx_audit_logs_org_created (organization_id, created_at),
    INDEX idx_audit_logs_project_created (project_id, created_at),
    INDEX idx_audit_logs_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
