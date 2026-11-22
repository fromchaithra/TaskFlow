import { useEffect, useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import {
  ArrowRight,
  Bug,
  Building2,
  CheckSquare,
  Columns3,
  FileBarChart,
  FolderKanban,
  IterationCw,
  RefreshCw,
  UserPlus,
  UsersRound,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { ALL_NAV_ITEMS, ROLE_META } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateTime, getGreeting } from '../../utils/formatters';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { EmptyState } from '../../components/common/Loading';
import { dashboardService, activityService } from '../../services/dashboardService';
import { RegisterUserModal } from '../users/RegisterUserModal';
import { LiveAnalyticsSection } from './LiveAnalyticsSection';

const MODULE_ICONS = {
  projects: FolderKanban,
  tasks: CheckSquare,
  kanban: Columns3,
  sprints: IterationCw,
  bugs: Bug,
  reports: FileBarChart,
  teams: UsersRound,
  organization: Building2,
};

function StatCard({ label, value, hint }) {
  return (
    <div className="rounded-xl border border-line bg-surface p-4 shadow-card">
      <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-ink-faint">
        {label}
      </p>
      <p className="mt-2 text-xl font-semibold tracking-tight text-ink tabular-nums">
        {value ?? 0}
      </p>
      {hint && <p className="mt-1 text-[11px] text-ink-muted">{hint}</p>}
    </div>
  );
}

function WelcomeSection({ user, roleInfo, onVerifySession, syncing }) {
  const firstName = user?.fullName?.split(' ')[0] || 'there';
  const organization = user?.organizationName || 'your organization';

  return (
    <section className="rounded-2xl border border-line bg-surface p-5 shadow-card sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-brand-600">
            Workspace overview
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2.5">
            <h1 className="text-2xl font-semibold tracking-tight text-ink sm:text-[28px]">
              {getGreeting()}, {firstName}
            </h1>
            <Badge tone={roleInfo.tone || 'neutral'} dot>
              {roleInfo.label || user?.role}
            </Badge>
          </div>
          <p className="mt-2 max-w-2xl text-sm leading-relaxed text-ink-muted">
            {roleInfo.description} You are viewing {organization}.
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          onClick={onVerifySession}
          loading={syncing}
          leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
        >
          Sync Profile
        </Button>
      </div>
    </section>
  );
}

function WorkspaceStats({ role, stats }) {
  if (!stats) return null;

  let cards;
  if (role === 'ADMIN') {
    cards = [
      { label: 'Employees', value: stats.totalEmployees, hint: `${stats.activeEmployees} active · ${stats.inactiveEmployees} inactive` },
      { label: 'Projects', value: stats.totalProjects, hint: `${stats.activeProjects} active · ${stats.completedProjects} done` },
      { label: 'Teams', value: stats.totalTeams, hint: 'Engineering squads' },
      { label: 'Tasks', value: stats.totalTasks, hint: `${stats.overdueTasks} overdue` },
    ];
  } else if (role === 'PROJECT_MANAGER') {
    cards = [
      { label: 'Managed projects', value: stats.managedProjects, hint: 'Owned by you' },
      { label: 'Active projects', value: stats.activeProjects, hint: 'In progress' },
      { label: 'Tasks', value: stats.totalTasks, hint: `${stats.overdueTasks} overdue` },
      { label: 'Teams', value: stats.totalTeams, hint: 'Organization squads' },
    ];
  } else if (role === 'DEVELOPER') {
    cards = [
      { label: 'My tasks', value: stats.totalTasks, hint: `${stats.overdueTasks} overdue` },
      { label: 'Due today', value: stats.tasksDueToday, hint: 'Due today' },
      { label: 'In progress', value: stats.tasksByStatus?.IN_PROGRESS ?? 0, hint: 'Active work' },
      { label: 'Done', value: stats.tasksByStatus?.DONE ?? 0, hint: 'Completed' },
    ];
  } else {
    cards = [
      { label: 'Test cases', value: stats.totalTestCases, hint: 'Total' },
      { label: 'Test suites', value: stats.totalTestSuites, hint: 'Total' },
      { label: 'Active runs', value: stats.activeTestRuns, hint: 'Planned + in progress' },
      { label: 'Passed', value: stats.passedExecutions, hint: `${stats.failedExecutions} failed · ${stats.blockedExecutions} blocked` },
    ];
  }

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-ink">Workspace snapshot</h2>
        <span className="text-[11px] text-ink-faint">Role-based summary</span>
      </div>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        {cards.map((card) => (
          <StatCard key={card.label} {...card} />
        ))}
      </div>
    </section>
  );
}

function OrganizationActivity({ activity }) {
  return (
    <Card>
      <CardHeader
        title="Organization activity"
        description="Latest authorized changes recorded across the workspace."
      />
      <CardBody>
        {activity.length === 0 ? (
          <EmptyState
            title="No activity recorded"
            description="Changes to authorized projects and users will appear here."
          />
        ) : (
          <ul className="divide-y divide-line">
            {activity.map((item) => (
              <li key={item.id} className="flex items-start gap-3 py-3 first:pt-0 last:pb-0">
                <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand-500" aria-hidden="true" />
                <div className="min-w-0 flex-1">
                  <p className="text-sm text-ink">
                    <span className="font-medium">{item.actorName}</span>{' '}
                    <span className="text-ink-muted">{String(item.action).toLowerCase().replace(/_/g, ' ')}</span>{' '}
                    <span className="text-ink-muted">{item.entityType} #{item.entityId}</span>
                    {item.projectName && (
                      <span className="text-ink-muted"> in {item.projectName}</span>
                    )}
                  </p>
                  <p className="mt-0.5 text-[11px] text-ink-faint">{formatDateTime(item.createdAt)}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}

function WorkspaceModules({ modules }) {
  return (
    <Card>
      <CardHeader
        title="Workspace modules"
        description="Quick access to the areas available to your role."
      />
      <CardBody>
        <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 xl:grid-cols-4">
          {modules.map((item) => {
            const Icon = MODULE_ICONS[item.id] || FolderKanban;
            return (
              <Link
                key={item.id}
                to={item.path}
                className="group flex items-center gap-3 rounded-lg border border-line bg-subtle/40 p-3 transition-colors hover:border-brand-200 hover:bg-brand-50"
              >
                <span
                  className="grid h-8 w-8 shrink-0 place-items-center rounded-lg"
                  style={{ color: 'var(--color-brand-600)', backgroundColor: 'var(--color-surface)' }}
                  aria-hidden="true"
                >
                  <Icon className="h-4 w-4" />
                </span>
                <span className="min-w-0 flex-1 truncate text-xs font-medium text-ink">
                  {item.label}
                </span>
                <ArrowRight className="h-3.5 w-3.5 shrink-0 text-ink-faint transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
              </Link>
            );
          })}
        </div>
      </CardBody>
    </Card>
  );
}

export function DashboardPage() {
  const { user, hasRole } = useAuth();
  const toast = useToast();
  const outletContext = useOutletContext() || {};
  const [syncing, setSyncing] = useState(false);
  const [stats, setStats] = useState(null);
  const [activity, setActivity] = useState([]);
  const [loading, setLoading] = useState(true);
  const [registerUserOpen, setRegisterUserOpen] = useState(false);

  const roleInfo = ROLE_META[user?.role] || {
    label: user?.role || 'Member',
    description: 'Workspace participant.',
    tone: 'neutral',
  };

  const accessibleModules = ALL_NAV_ITEMS.filter(
    (item) => Object.hasOwn(MODULE_ICONS, item.id) && hasRole(item.roles)
  );
  const teams = Array.isArray(user?.teams) ? user.teams : [];

  const handleVerifySession = async () => {
    setSyncing(true);
    try {
      await (outletContext.openProfile ? Promise.resolve() : Promise.resolve());
      toast.success('Session verified.', 'Workspace');
    } catch (err) {
      toast.error(parseApiError(err).message, 'Verification failed');
    } finally {
      setSyncing(false);
    }
  };

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const [s, a] = await Promise.all([
          dashboardService.getStats(),
          activityService.list({ size: 8 }),
        ]);
        if (active) {
          setStats(s);
          setActivity(a?.content || []);
        }
      } catch (err) {
        if (active) toast.error(parseApiError(err).message, 'Failed to load dashboard');
      } finally {
        if (active) setLoading(false);
      }
    }
    load();
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="space-y-6">
      <WelcomeSection
        user={user}
        roleInfo={roleInfo}
        onVerifySession={handleVerifySession}
        syncing={syncing}
      />

      <WorkspaceStats role={user?.role} stats={loading ? null : stats} />

      <LiveAnalyticsSection />

      {!loading && user?.role === 'ADMIN' && (
        <Card>
          <CardHeader
            title="User Management"
            description="Provision employees for your organization."
            action={
              <Link to="/employees" className="text-xs font-semibold text-brand-700 hover:underline">
                View all users
              </Link>
            }
          />
          <CardBody>
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
                Register Project Managers, Developers, QA Testers, or additional Administrators.
                New accounts belong to your organization and can sign in with the credentials you set here.
              </p>
              <Button
                variant="primary"
                size="sm"
                onClick={() => setRegisterUserOpen(true)}
                leftIcon={<UserPlus className="h-3.5 w-3.5" />}
              >
                Register User
              </Button>
            </div>
          </CardBody>
        </Card>
      )}

      <OrganizationActivity activity={activity} />

      <WorkspaceModules modules={accessibleModules} />

      {teams.length > 0 && (
        <Card>
          <CardHeader title="Assigned teams" description="Teams you currently belong to." />
          <CardBody>
            <div className="flex flex-wrap gap-2">
              {teams.map((team) => (
                <Badge key={team} tone="neutral">{team}</Badge>
              ))}
            </div>
          </CardBody>
        </Card>
      )}

      <RegisterUserModal
        isOpen={registerUserOpen}
        onClose={() => setRegisterUserOpen(false)}
      />
    </div>
  );
}
