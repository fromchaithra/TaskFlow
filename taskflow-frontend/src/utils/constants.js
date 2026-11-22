/**
 * Exact backend RoleName enum values from com.taskflow.enums.RoleName
 */
export const ROLES = Object.freeze({
  ADMIN: 'ADMIN',
  PROJECT_MANAGER: 'PROJECT_MANAGER',
  DEVELOPER: 'DEVELOPER',
  QA_TESTER: 'QA_TESTER',
});

export const ALL_ROLES = Object.freeze([
  ROLES.ADMIN,
  ROLES.PROJECT_MANAGER,
  ROLES.DEVELOPER,
  ROLES.QA_TESTER,
]);

export const ROLE_META = Object.freeze({
  [ROLES.ADMIN]: {
    label: 'Administrator',
    shortLabel: 'Admin',
    description: 'Full organization governance, employee provisioning, teams, and cross-project oversight.',
    tone: 'cyan',
  },
  [ROLES.PROJECT_MANAGER]: {
    label: 'Project Manager',
    shortLabel: 'PM',
    description: 'Project delivery, sprint planning, member allocation, and workflow execution.',
    tone: 'steel',
  },
  [ROLES.DEVELOPER]: {
    label: 'Developer',
    shortLabel: 'Developer',
    description: 'Engineering implementation, kanban task progression, and defect resolution.',
    tone: 'emerald',
  },
  [ROLES.QA_TESTER]: {
    label: 'QA Tester',
    shortLabel: 'QA',
    description: 'Quality assurance, bug reporting, verification workflows, and release readiness.',
    tone: 'amber',
  },
});

/**
 * Role-aware sidebar navigation blueprint.
 * Phase 1 activates /dashboard; upcoming workspace modules route to role-gated placeholders
 * matching the implemented Spring Boot backend REST APIs.
 */
export const NAVIGATION_SECTIONS = [
  {
    id: 'workspace',
    label: 'Workspace',
    items: [
      {
        id: 'dashboard',
        label: 'Dashboard',
        path: '/dashboard',
        icon: 'LayoutDashboard',
        roles: ALL_ROLES,
        phaseStatus: 'active',
        description: 'Workspace overview and role-specific operational shortcuts.',
      },
      {
        id: 'projects',
        label: 'Projects',
        path: '/projects',
        icon: 'FolderKanban',
        roles: ALL_ROLES,
        phaseStatus: 'active',
        backendEndpoint: '/api/v1/projects',
        description: 'Manage project portfolios, memberships, lifecycle statuses, and delivery scopes.',
      },
      {
        id: 'tasks',
        label: 'Tasks',
        path: '/tasks',
        icon: 'CheckSquare',
        roles: ALL_ROLES,
        phaseStatus: 'active',
        backendEndpoint: '/api/v1/tasks',
        description: 'Track engineering deliverables, assignees, priorities, and personal work queues.',
      },
      {
        id: 'kanban',
        label: 'Kanban Board',
        path: '/kanban',
        icon: 'Columns3',
        roles: ALL_ROLES,
        phaseStatus: 'active',
        backendEndpoint: '/api/v1/projects/{projectId}/kanban',
        description: 'Visual workflow columns with controlled transitions across project stages.',
      },
      {
        id: 'sprints',
        label: 'Sprints',
        path: '/sprints',
        icon: 'IterationCw',
        roles: ALL_ROLES,
        phaseStatus: 'active',
        backendEndpoint: '/api/v1/projects/{projectId}/sprints',
        description: 'Plan iterations, manage sprint backlogs, start cycles, and track completion.',
      },
      {
        id: 'bugs',
        label: 'Bug Tracking',
        path: '/bugs',
        icon: 'Bug',
        roles: ALL_ROLES,
        phaseStatus: 'active',
        backendEndpoint: '/api/v1/bugs',
        description: 'Log defects, triage severity, coordinate fixes, and run QA verification.',
      },
      {
        id: 'reports',
        label: 'Reports & Analytics',
        path: '/reports',
        icon: 'FileBarChart',
        roles: ALL_ROLES,
        phaseStatus: 'active',
        backendEndpoint: '/api/v1/reports/catalog',
        description: 'Generate downloadable PDF and Excel reports from authorized workspace data.',
      },
    ],
  },
  {
    id: 'administration',
    label: 'Organization',
    items: [
      {
        id: 'teams',
        label: 'Teams',
        path: '/teams',
        icon: 'UsersRound',
        roles: ALL_ROLES,
        phaseStatus: 'active',
        backendEndpoint: '/api/v1/teams',
        description: 'Organize engineering squads, assign team leads, and manage roster membership.',
      },
      {
        id: 'employees',
        label: 'Employees',
        path: '/employees',
        icon: 'UserCog',
        roles: [ROLES.ADMIN, ROLES.PROJECT_MANAGER],
        phaseStatus: 'active',
        backendEndpoint: '/api/v1/users',
        description: 'Directory of organization members, role governance, and account lifecycle controls.',
      },
      {
        id: 'organization',
        label: 'Organization',
        path: '/organization',
        icon: 'Building2',
        roles: ALL_ROLES,
        phaseStatus: 'active',
        backendEndpoint: '/api/v1/organization',
        description: 'Configure workspace identity, timezone, and operating working-day schedules.',
      },
    ],
  },
];

export const ALL_NAV_ITEMS = NAVIGATION_SECTIONS.flatMap((section) => section.items);

export const ROUTE_LABELS = Object.freeze({
  dashboard: 'Dashboard',
  projects: 'Projects',
  tasks: 'Tasks',
  kanban: 'Kanban Board',
  sprints: 'Sprints',
  bugs: 'Bug Tracking',
  reports: 'Reports & Analytics',
  teams: 'Teams',
  employees: 'Employees',
  organization: 'Organization',
  unauthorized: 'Access Denied',
});

export const CSRF_HEADER_NAME = 'X-Requested-With';
export const CSRF_HEADER_VALUE = 'TaskFlow';
