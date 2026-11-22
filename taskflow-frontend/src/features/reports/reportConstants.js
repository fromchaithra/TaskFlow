import {
  Activity,
  BarChart3,
  Bug,
  CheckCircle2,
  Clock,
  GitBranch,
  Layers,
  ListChecks,
  Target,
  TrendingUp,
  UsersRound,
} from 'lucide-react';

/**
 * Local presentation metadata only: icons, descriptions and categories.
 * Availability and supported formats always come from GET /reports/catalog.
 */
export const REPORT_CATEGORIES = Object.freeze({
  overview: { label: 'Overview', tone: 'brand' },
  delivery: { label: 'Delivery', tone: 'cyan' },
  quality: { label: 'Quality', tone: 'amber' },
  workspace: { label: 'Workspace', tone: 'steel' },
});

export const REPORT_TYPE_META = Object.freeze({
  EXECUTIVE_OVERVIEW: {
    icon: BarChart3,
    category: 'overview',
    description: 'A workspace-wide summary of delivery health and key outcomes for the selected period.',
  },
  PROJECT_PERFORMANCE: {
    icon: Target,
    category: 'delivery',
    description: 'Compare delivery performance across the projects you are authorized to view.',
  },
  PROJECT_GROWTH: {
    icon: TrendingUp,
    category: 'delivery',
    description: 'Track how project scope and activity change over the reporting window.',
  },
  SPRINT_VELOCITY: {
    icon: Activity,
    category: 'delivery',
    description: 'Review completed work across sprints to understand delivery rhythm.',
  },
  SPRINT_BURNOWN: {
    icon: ListChecks,
    category: 'delivery',
    description: 'Inspect remaining work for one specific sprint over time.',
  },
  TASK_COMPLETION_TRENDS: {
    icon: CheckCircle2,
    category: 'delivery',
    description: 'Follow task completion patterns and throughput across the selected range.',
  },
  TEAM_PRODUCTIVITY: {
    icon: UsersRound,
    category: 'workspace',
    description: 'Understand how teams contribute to completed work and active delivery.',
  },
  EMPLOYEE_WORKLOAD: {
    icon: Clock,
    category: 'workspace',
    description: 'See workload distribution across employees within your organization.',
  },
  BUG_TRENDS: {
    icon: Bug,
    category: 'quality',
    description: 'Monitor defect volume, severity and resolution patterns over time.',
  },
  QA_ANALYTICS: {
    icon: Layers,
    category: 'quality',
    description: 'Summarize test coverage, executions and verification outcomes.',
  },
});

export const FALLBACK_REPORT_META = Object.freeze({
  icon: GitBranch,
  category: 'overview',
  description: 'A downloadable report defined by the backend catalog.',
});

export function getReportMeta(typeId) {
  return REPORT_TYPE_META[typeId] || FALLBACK_REPORT_META;
}
