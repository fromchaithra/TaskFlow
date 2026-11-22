-- =============================================================================
-- V2: Projects, Project Members, Sprints, Tasks, Comments, Attachments, History
-- =============================================================================

CREATE TABLE projects (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_id BIGINT UNSIGNED NOT NULL,
    project_key VARCHAR(10) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    manager_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    start_date DATE NOT NULL,
    end_date DATE NULL,
    task_sequence INT UNSIGNED NOT NULL DEFAULT 100,
    bug_sequence INT UNSIGNED NOT NULL DEFAULT 100,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_projects_org_key UNIQUE (organization_id, project_key),
    CONSTRAINT fk_projects_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_projects_manager FOREIGN KEY (manager_id) REFERENCES users (id),
    INDEX idx_projects_status (status),
    INDEX idx_projects_manager (manager_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    project_role VARCHAR(32) NOT NULL,
    added_by BIGINT UNSIGNED NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_project_members_added_by FOREIGN KEY (added_by) REFERENCES users (id),
    INDEX idx_project_members_user_project (user_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sprints (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    goal TEXT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_sprints_project_name UNIQUE (project_id, name),
    CONSTRAINT fk_sprints_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_sprints_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_sprints_project_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    task_key VARCHAR(24) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    assignee_id BIGINT UNSIGNED NULL,
    reporter_id BIGINT UNSIGNED NOT NULL,
    sprint_id BIGINT UNSIGNED NULL,
    parent_task_id BIGINT UNSIGNED NULL,
    estimated_hours DECIMAL(6, 2) NULL,
    due_date DATE NULL,
    board_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_tasks_key UNIQUE (task_key),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_tasks_sprint FOREIGN KEY (sprint_id) REFERENCES sprints (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_parent FOREIGN KEY (parent_task_id) REFERENCES tasks (id) ON DELETE SET NULL,
    INDEX idx_tasks_project_status_order (project_id, status, board_order),
    INDEX idx_tasks_assignee_status (assignee_id, status),
    INDEX idx_tasks_due_date_status (due_date, status),
    INDEX idx_tasks_sprint (sprint_id),
    INDEX idx_tasks_parent (parent_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sprint_tasks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sprint_id BIGINT UNSIGNED NOT NULL,
    task_id BIGINT UNSIGNED NOT NULL,
    added_by BIGINT UNSIGNED NOT NULL,
    added_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    removed_at TIMESTAMP(6) NULL,
    status_at_sprint_end VARCHAR(20) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sprint_tasks_sprint_task UNIQUE (sprint_id, task_id),
    CONSTRAINT fk_sprint_tasks_sprint FOREIGN KEY (sprint_id) REFERENCES sprints (id) ON DELETE CASCADE,
    CONSTRAINT fk_sprint_tasks_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_sprint_tasks_added_by FOREIGN KEY (added_by) REFERENCES users (id),
    INDEX idx_sprint_tasks_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_comments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    content TEXT NOT NULL,
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_task_comments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_comments_author FOREIGN KEY (author_id) REFERENCES users (id),
    INDEX idx_task_comments_task_created (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_attachments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    uploaded_by BIGINT UNSIGNED NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size_bytes BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_task_attachments_stored UNIQUE (stored_filename),
    CONSTRAINT fk_task_attachments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_attachments_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id),
    INDEX idx_task_attachments_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    changed_by BIGINT UNSIGNED NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    old_value VARCHAR(255) NULL,
    new_value VARCHAR(255) NULL,
    remarks VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_task_history_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_history_user FOREIGN KEY (changed_by) REFERENCES users (id),
    INDEX idx_task_history_task_created (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
