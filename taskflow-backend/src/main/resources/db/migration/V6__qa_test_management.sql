-- =============================================================================
-- V6: QA Test Management (test cases, suites, runs, executions)
-- =============================================================================
CREATE TABLE test_cases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    test_key VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    preconditions TEXT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_test_cases_key UNIQUE (test_key),
    CONSTRAINT fk_test_cases_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_test_cases_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_test_cases_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_test_cases_project (project_id, is_archived)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE test_steps (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    test_case_id BIGINT UNSIGNED NOT NULL,
    step_order INT NOT NULL,
    action TEXT NOT NULL,
    expected_result TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_test_steps_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id) ON DELETE CASCADE,
    INDEX idx_test_steps_case (test_case_id, step_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE test_suites (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_test_suites_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_test_suites_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_test_suites_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_test_suites_project (project_id, is_archived)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE test_suite_cases (
    suite_id BIGINT UNSIGNED NOT NULL,
    test_case_id BIGINT UNSIGNED NOT NULL,
    added_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (suite_id, test_case_id),
    CONSTRAINT fk_tsc_suite FOREIGN KEY (suite_id) REFERENCES test_suites (id) ON DELETE CASCADE,
    CONSTRAINT fk_tsc_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE test_runs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    suite_id BIGINT UNSIGNED NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    assigned_to BIGINT UNSIGNED NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_test_runs_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_test_runs_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_test_runs_suite FOREIGN KEY (suite_id) REFERENCES test_suites (id) ON DELETE SET NULL,
    CONSTRAINT fk_test_runs_assigned FOREIGN KEY (assigned_to) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_test_runs_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_test_runs_project (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE test_executions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    test_case_id BIGINT UNSIGNED NOT NULL,
    result VARCHAR(16) NOT NULL DEFAULT 'NOT_RUN',
    actual_result TEXT NULL,
    notes TEXT NULL,
    executed_by BIGINT UNSIGNED NULL,
    executed_at TIMESTAMP(6) NULL,
    bug_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_te_run_case UNIQUE (run_id, test_case_id),
    CONSTRAINT fk_te_run FOREIGN KEY (run_id) REFERENCES test_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_te_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id),
    CONSTRAINT fk_te_executed_by FOREIGN KEY (executed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_te_bug FOREIGN KEY (bug_id) REFERENCES bugs (id) ON DELETE SET NULL,
    INDEX idx_te_run (run_id, result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
