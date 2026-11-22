import { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  Bug,
  CheckCircle2,
  CircleAlert,
  Clock3,
  FolderKanban,
  ListChecks,
  RefreshCw,
  UsersRound,
  WifiOff,
} from 'lucide-react';
import {
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { Select } from '../../components/common/FormControls';
import { EmptyState, ErrorState, Skeleton } from '../../components/common/Loading';
import { useAuth } from '../../hooks/useAuth';
import { LIVE_STATUS, useLiveAnalytics } from '../../hooks/useLiveAnalytics';
import { projectService } from '../../services/projectService';
import { formatDate, formatDateTime, getTodayIsoDate } from '../../utils/formatters';

const KPI_DEFINITIONS = [
  { key: 'totalTasks', label: 'Total Tasks', icon: ListChecks, color: 'var(--color-chart-1)' },
  { key: 'completedTasks', label: 'Completed Tasks', icon: CheckCircle2, color: 'var(--color-chart-3)' },
  { key: 'inProgressTasks', label: 'In Progress Tasks', icon: Activity, color: 'var(--color-chart-2)' },
  { key: 'overdueTasks', label: 'Overdue Tasks', icon: Clock3, color: 'var(--color-chart-5)' },
];

const STATUS_META = {
  [LIVE_STATUS.CONNECTING]: { tone: 'amber', label: 'Connecting' },
  [LIVE_STATUS.LIVE]: { tone: 'emerald', label: 'Live' },
  [LIVE_STATUS.RECONNECTING]: { tone: 'amber', label: 'Reconnecting' },
  [LIVE_STATUS.POLLING]: { tone: 'steel', label: 'Polling' },
  [LIVE_STATUS.ERROR]: { tone: 'red', label: 'Offline' },
};

const OFFLINE_STATUSES = new Set([
  LIVE_STATUS.RECONNECTING,
  LIVE_STATUS.POLLING,
  LIVE_STATUS.ERROR,
]);

const CHART_COLORS = [1, 2, 3, 4, 5, 6].map((n) => `var(--color-chart-${n})`);

const INPUT_CLASS =
  'h-9 rounded-lg border border-line-strong bg-canvas px-2.5 text-sm text-ink focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20';

function isoDaysAgo(days) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function defaultFilters() {
  return {
    from: isoDaysAgo(30),
    to: getTodayIsoDate(),
    granularity: 'WEEK',
    projectIds: [],
    teamIds: [],
    employeeIds: [],
  };
}

function metricNumber(metric) {
  if (!metric || metric.availability !== 'AVAILABLE' || metric.value === null || metric.value === undefined) {
    return null;
  }
  const value = Number(metric.value);
  return Number.isFinite(value) ? value : null;
}

function availabilityLabel(metric) {
  if (metric?.availability === 'NO_DATA_IN_RANGE') return 'No data in range';
  if (metric?.availability === 'INSUFFICIENT_HISTORY') return 'Insufficient history';
  if (metric?.availability === 'NOT_CONFIGURED') return 'Not configured';
  return 'Unavailable';
}

function friendlyStatus(name) {
  return String(name).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

function KpiCard({ label, metric, icon: Icon, color }) {
  const value = metricNumber(metric);
  const unavailable = value === null;

  return (
    <div className="rounded-xl border border-line bg-surface p-4 shadow-card transition-colors hover:border-line-strong sm:p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-ink-faint">
            {label}
          </p>
          <p className="mt-3 text-3xl font-semibold leading-none tracking-tight text-ink tabular-nums sm:text-[32px]">
            {unavailable ? '—' : value.toLocaleString()}
          </p>
        </div>
        <span
          className="grid h-9 w-9 shrink-0 place-items-center rounded-lg"
          style={{ color, backgroundColor: 'var(--color-subtle)' }}
          aria-hidden="true"
        >
          <Icon className="h-[18px] w-[18px]" />
        </span>
      </div>
      <p className={`mt-3 text-[11px] ${unavailable ? 'text-ink-faint' : 'text-ink-muted'}`}>
        {unavailable ? availabilityLabel(metric) : 'Measured value'}
      </p>
    </div>
  );
}

function FilterBar({ filters, onChange, projects }) {
  const update = (patch) => onChange({ ...filters, ...patch });

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <label className="flex flex-col gap-1 text-xs text-ink-muted">
        From
        <input
          type="date"
          value={filters.from}
          max={filters.to}
          onChange={(event) => update({ from: event.target.value })}
          className={INPUT_CLASS}
        />
      </label>
      <label className="flex flex-col gap-1 text-xs text-ink-muted">
        To
        <input
          type="date"
          value={filters.to}
          min={filters.from}
          onChange={(event) => update({ to: event.target.value })}
          className={INPUT_CLASS}
        />
      </label>
      <Select
        label="Granularity"
        value={filters.granularity}
        onChange={(event) => update({ granularity: event.target.value })}
        options={[
          { value: 'WEEK', label: 'Weekly' },
          { value: 'MONTH', label: 'Monthly' },
        ]}
      />
      <label className="flex flex-col gap-1 text-xs text-ink-muted">
        Projects
        <select
          multiple
          size={2}
          value={filters.projectIds.map(String)}
          onChange={(event) =>
            update({
              projectIds: Array.from(event.target.selectedOptions).map((option) => Number(option.value)),
            })
          }
          className="rounded-lg border border-line-strong bg-canvas px-2 py-1 text-sm text-ink focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20"
          title="Select projects; leave empty for all authorized projects"
        >
          {projects.map((project) => (
            <option key={project.id} value={project.id}>
              {project.name || project.projectKey}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}

function ChartCard({ title, subtitle, children }) {
  return (
    <div className="rounded-xl border border-line bg-surface">
      <div className="px-5 py-4 border-b border-line">
        <h3 className="text-sm font-semibold text-ink">{title}</h3>
        {subtitle && <p className="mt-0.5 text-[11px] text-ink-faint">{subtitle}</p>}
      </div>
      <div className="p-4">{children}</div>
    </div>
  );
}

function StatusChart({ distribution }) {
  const data = useMemo(
    () => Object.entries(distribution || {})
      .map(([name, value]) => ({ name: friendlyStatus(name), value: Number(value) || 0 }))
      .filter((entry) => entry.value > 0),
    [distribution]
  );

  if (data.length === 0) {
    return (
      <EmptyState
        icon={ListChecks}
        title="No status data"
        description="No tasks fall inside the selected period and project scope."
      />
    );
  }

  return (
    <div className="h-60 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            innerRadius={52}
            outerRadius={86}
            paddingAngle={2}
          >
            {data.map((entry, index) => (
              <Cell key={entry.name} fill={CHART_COLORS[index % CHART_COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            formatter={(value, name) => [value, name]}
            contentStyle={{
              backgroundColor: 'var(--color-chart-tooltip)',
              borderColor: 'var(--color-line)',
              color: 'var(--color-ink)',
            }}
            labelStyle={{ color: 'var(--color-ink)' }}
            itemStyle={{ color: 'var(--color-ink)' }}
          />
          <Legend wrapperStyle={{ color: 'var(--color-ink-muted)', fontSize: 11 }} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

function TrendChart({ buckets }) {
  const data = useMemo(
    () => (buckets || []).map((bucket) => {
      const metric = bucket?.series?.tasksCompleted;
      const available = metric?.availability === 'AVAILABLE' && metric.value !== null;
      return {
        label: formatDate(bucket.start),
        completed: available ? Number(metric.value) : null,
      };
    }),
    [buckets]
  );
  const hasValues = data.some((point) => point.completed !== null);

  if (!hasValues) {
    return (
      <EmptyState
        icon={Activity}
        title="No completion trend"
        description="No DONE transitions were recorded in this period for the selected projects."
      />
    );
  }

  return (
    <div className="h-60 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 8, left: -16 }}>
          <CartesianGrid stroke="var(--color-chart-grid)" strokeDasharray="3 3" />
          <XAxis dataKey="label" tick={{ fontSize: 10, fill: 'var(--color-chart-axis)' }} interval="preserveStartEnd" />
          <YAxis allowDecimals={false} tick={{ fontSize: 10, fill: 'var(--color-chart-axis)' }} />
          <Tooltip
            formatter={(value) => [value, 'Tasks completed']}
            contentStyle={{
              backgroundColor: 'var(--color-chart-tooltip)',
              borderColor: 'var(--color-line)',
              color: 'var(--color-ink)',
            }}
            labelStyle={{ color: 'var(--color-ink)' }}
            itemStyle={{ color: 'var(--color-ink)' }}
          />
          <Line
            type="monotone"
            dataKey="completed"
            name="Tasks completed"
            stroke="var(--color-chart-1)"
            strokeWidth={2}
            dot={{ r: 3, fill: 'var(--color-chart-1)' }}
            connectNulls={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function activityIcon(action) {
  const value = String(action || '');
  if (value.startsWith('BUG')) return Bug;
  if (value.startsWith('PROJECT')) return FolderKanban;
  if (value.startsWith('USER') || value.startsWith('TEAM')) return UsersRound;
  return ListChecks;
}

function ActivityList({ entries }) {
  if (!entries || entries.length === 0) {
    return (
      <EmptyState
        icon={Activity}
        title="No activity yet"
        description="Authorized activity for the selected projects will appear here."
      />
    );
  }

  return (
    <ul className="divide-y divide-line">
      {entries.slice(0, 10).map((entry) => {
        const Icon = activityIcon(entry.action);
        return (
          <li key={entry.id} className="flex items-start gap-3 py-3 first:pt-0 last:pb-0">
            <span
              className="grid h-8 w-8 shrink-0 place-items-center rounded-lg"
              style={{ color: 'var(--color-brand-600)', backgroundColor: 'var(--color-brand-50)' }}
              aria-hidden="true"
            >
              <Icon className="h-4 w-4" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm text-ink">
                <span className="font-semibold">{entry.actorName}</span>{' '}
                <span className="text-ink-muted">{friendlyStatus(entry.action)}</span>{' '}
                <span className="text-ink-muted">{entry.entityType} #{entry.entityId}</span>
                {entry.projectName && (
                  <span className="text-ink-muted"> in {entry.projectName}</span>
                )}
              </p>
              <p className="mt-0.5 text-[11px] text-ink-faint">{formatDateTime(entry.createdAt)}</p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}

export function LiveAnalyticsSection() {
  const { user, isAuthenticated } = useAuth();
  const [filters, setFilters] = useState(defaultFilters);
  const [projects, setProjects] = useState([]);

  const identityKey = `${user?.id || ''}:${user?.organizationId || ''}`;
  const { snapshot, status, lastUpdated, error, refreshing, manualRefresh } =
    useLiveAnalytics(filters, Boolean(isAuthenticated), identityKey);

  // Project options and filters are account/organization scoped: reset both when identity changes.
  useEffect(() => {
    setFilters(defaultFilters());
  }, [identityKey]);

  useEffect(() => {
    let active = true;
    projectService.listProjects({ size: 100 })
      .then((response) => {
        if (!active) return;
        setProjects(Array.isArray(response?.content) ? response.content : []);
      })
      .catch(() => {
        if (active) setProjects([]);
      });
    return () => { active = false; };
  }, [identityKey]);

  const statusMeta = STATUS_META[status] || STATUS_META[LIVE_STATUS.CONNECTING];
  const offline = OFFLINE_STATUSES.has(status);
  const hasData = snapshot
    ? Object.values(snapshot.taskKpis || {}).some((metric) => metric?.availability === 'AVAILABLE')
      || (snapshot.recentActivity || []).length > 0
    : false;

  return (
    <Card>
      <CardHeader
        title="Live overview"
        description="Current delivery metrics for your authorized projects."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={statusMeta.tone} dot>{statusMeta.label}</Badge>
            {lastUpdated && (
              <span className="hidden text-[11px] text-ink-muted sm:inline">
                Updated {formatDateTime(new Date(lastUpdated).toISOString())}
              </span>
            )}
            <Button
              variant="secondary"
              size="sm"
              loading={refreshing}
              onClick={manualRefresh}
              leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
            >
              Refresh
            </Button>
          </div>
        }
      />
      <CardBody className="space-y-5">
        <FilterBar filters={filters} onChange={setFilters} projects={projects} />

        {offline && (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border border-line bg-subtle/60 px-3 py-2 text-xs text-ink-muted">
            <WifiOff className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span>
              {status === LIVE_STATUS.POLLING
                ? 'Live stream unavailable — showing periodic snapshot updates.'
                : 'Live updates interrupted — reconnecting with the last available data.'}
            </span>
          </div>
        )}

        {!snapshot && status !== LIVE_STATUS.ERROR && (
          <div className="space-y-5" aria-busy="true">
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
              {KPI_DEFINITIONS.map((kpi) => (
                <div key={kpi.key} className="rounded-xl border border-line bg-surface p-4">
                  <Skeleton className="h-3 w-20" />
                  <Skeleton className="mt-3 h-8 w-16" />
                </div>
              ))}
            </div>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <Skeleton className="h-60 w-full" />
              <Skeleton className="h-60 w-full" />
            </div>
          </div>
        )}

        {!snapshot && status === LIVE_STATUS.ERROR && (
          <ErrorState
            title="Live analytics unavailable"
            description={error || 'The live data source could not be reached.'}
            onRetry={manualRefresh}
            retrying={refreshing}
          />
        )}

        {snapshot && (
          <div className="space-y-5">
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
              {KPI_DEFINITIONS.map((kpi) => (
                <KpiCard
                  key={kpi.key}
                  label={kpi.label}
                  icon={kpi.icon}
                  color={kpi.color}
                  metric={snapshot.taskKpis?.[kpi.key]}
                />
              ))}
            </div>

            {!hasData && (
              <div className="flex items-start gap-2 rounded-lg border border-line bg-subtle/60 px-3 py-2 text-xs text-ink-muted">
                <CircleAlert className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span>No qualifying data in this range. Unavailable metrics are shown as “—”, not zero.</span>
              </div>
            )}

            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <ChartCard title="Task Status Distribution" subtitle="Current statuses in the selected scope">
                <StatusChart distribution={snapshot.taskStatusDistribution} />
              </ChartCard>
              <ChartCard title="Task Completion Trend" subtitle="DONE transitions over the selected period">
                <TrendChart buckets={snapshot.completionTrend} />
              </ChartCard>
            </div>

            <ChartCard title="Recent Activity" subtitle="Authorized changes in the selected projects">
              <ActivityList entries={snapshot.recentActivity} />
            </ChartCard>

            {status === LIVE_STATUS.ERROR && error && (
              <div className="flex flex-wrap items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
                <CircleAlert className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span>Live updates interrupted: {error}</span>
                <Button variant="ghost" size="sm" onClick={manualRefresh}>Retry</Button>
              </div>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}
