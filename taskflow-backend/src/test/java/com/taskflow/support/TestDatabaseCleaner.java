package com.taskflow.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Clears the shared H2 test schema in foreign-key-safe order.
 *
 * <p>All integration test classes share the same in-memory database
 * ({@code jdbc:h2:mem:taskflow_test}). Each class must remove rows left behind by
 * the previous class, otherwise parent rows (projects, users, organizations) cannot
 * be deleted while child rows (bugs, sprints, teams, ...) still reference them.</p>
 */
public final class TestDatabaseCleaner {

    private static final String[] DELETE_STATEMENTS = {
            // QA test management children, then QA parents
            "DELETE FROM test_executions",
            "DELETE FROM test_runs",
            "DELETE FROM test_suite_cases",
            "DELETE FROM test_suites",
            "DELETE FROM test_steps",
            "DELETE FROM test_cases",
            // Bug children, then bugs
            "DELETE FROM bug_attachments",
            "DELETE FROM bug_comments",
            "DELETE FROM bug_history",
            "DELETE FROM bugs",
            // Task children, then tasks
            "DELETE FROM task_attachments",
            "DELETE FROM task_comments",
            "DELETE FROM task_history",
            "DELETE FROM sprint_tasks",
            "UPDATE tasks SET parent_task_id = NULL",
            "DELETE FROM tasks",
            "DELETE FROM sprints",
            // Audit and auth records reference users/projects/organizations
            "DELETE FROM notifications",
            "DELETE FROM audit_logs",
            "DELETE FROM activation_tokens",
            "DELETE FROM refresh_tokens",
            "DELETE FROM revoked_tokens",
            // Project children, then projects
            "DELETE FROM project_members",
            "DELETE FROM projects",
            // Join table and team children, then teams
            "DELETE FROM user_roles",
            "DELETE FROM team_members",
            "DELETE FROM teams",
            // Remaining parents
            "DELETE FROM users",
            "DELETE FROM organizations",
            "DELETE FROM roles"
    };

    private TestDatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbcTemplate) {
        for (String statement : DELETE_STATEMENTS) {
            jdbcTemplate.update(statement);
        }
    }
}
