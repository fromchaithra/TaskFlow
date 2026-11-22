package com.taskflow.dto.report;

/**
 * The ten analytics report modules.
 *
 * <p>This enum intentionally contains no authorization rules; report access is
 * enforced by the existing security architecture via the request scope.</p>
 */
public enum ReportType {

    EXECUTIVE_OVERVIEW("Executive Overview"),
    PROJECT_PERFORMANCE("Project Performance"),
    PROJECT_GROWTH("Project Growth"),
    SPRINT_VELOCITY("Sprint Velocity"),
    SPRINT_BURNOWN("Sprint Burndown"),
    TASK_COMPLETION_TRENDS("Task Completion Trends"),
    TEAM_PRODUCTIVITY("Team Productivity"),
    EMPLOYEE_WORKLOAD("Employee Workload"),
    BUG_TRENDS("Bug Trends"),
    QA_ANALYTICS("QA Analytics");

    private final String label;

    ReportType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
