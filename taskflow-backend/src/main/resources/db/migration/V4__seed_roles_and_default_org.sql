-- =============================================================================
-- V4: Seed Core RBAC Roles and Default Organization Profile
-- =============================================================================

INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'Organization-level administrator with employee, team, settings, and audit authority'),
    ('PROJECT_MANAGER', 'Project owner responsible for projects, members, sprints, tasks, and approvals'),
    ('DEVELOPER', 'Engineering team member working on assigned tasks and bug fixes'),
    ('QA_TESTER', 'Quality assurance engineer validating completed tasks and tracking defects');

INSERT INTO organizations (name, slug, industry, timezone, working_days) VALUES
    ('TaskFlow Enterprise', 'taskflow-enterprise', 'Software Engineering', 'UTC', 'MON,TUE,WED,THU,FRI');
