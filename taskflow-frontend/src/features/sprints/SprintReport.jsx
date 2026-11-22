import { CheckCircle2, Clock, ListChecks, PieChart } from 'lucide-react';
import { Skeleton, ErrorState } from '../../components/common/Loading';
import { formatDateRange, formatDateTime } from '../../utils/formatters';
import { TaskAssignee } from '../tasks/components/TaskAssignee';
import { TaskStatusBadge } from '../tasks/components/TaskStatusBadge';
import { SPRINT_STATUS } from './sprintConstants';

function MetricTile({ icon: Icon, label, value, accent = 'text-ink' }) {
  return (
    <div className="rounded-lg border border-line bg-subtle/60 px-3.5 py-3">
      <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-ink-faint">
        <Icon className="h-3.5 w-3.5" aria-hidden="true" />
        <span>{label}</span>
      </div>
      <p className={`mt-1 text-xl font-semibold tabular-nums ${accent}`}>{value}</p>
    </div>
  );
}

function DistributionRow({ label, count, total, barClass }) {
  const percentage = total > 0 ? Math.round((count / total) * 100) : 0;

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between text-[11px]">
        <span className="text-ink-muted">{label}</span>
        <span className="font-mono tabular-nums text-ink-muted">
          {count} · {percentage}%
        </span>
      </div>
      <div className="h-1.5 rounded-full bg-subtle overflow-hidden">
        <div
          className={`h-full rounded-full ${barClass}`}
          style={{ width: `${percentage}%` }}
          aria-hidden="true"
        />
      </div>
    </div>
  );
}

function ReportTaskList({ title, tasks, emptyLabel }) {
  return (
    <section className="space-y-2">
      <h4 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
        {title} <span className="font-mono tabular-nums text-ink-faint">({tasks.length})</span>
      </h4>
      {tasks.length === 0 ? (
        <p className="text-xs text-ink-faint rounded-lg border border-dashed border-line bg-subtle/40 px-3.5 py-3">
          {emptyLabel}
        </p>
      ) : (
        <ul className="max-h-52 overflow-y-auto divide-y divide-line rounded-lg border border-line">
          {tasks.map((task) => (
            <li
              key={task.id}
              className="flex items-center justify-between gap-2 px-3.5 py-2.5 bg-subtle/40"
            >
              <div className="flex items-center gap-2.5 min-w-0">
                <span className="font-mono text-[11px] text-ink-faint tabular-nums shrink-0">
                  {task.taskKey}
                </span>
                <span className="text-xs text-ink truncate">{task.title}</span>
                <TaskStatusBadge status={task.status} className="shrink-0" />
              </div>
              <TaskAssignee assignee={task.assignee} compact />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/**
 * Renders the real SprintReportResponse payload. No velocity or burndown metrics are
 * invented: the backend exposes status counts and completed/incomplete task lists only.
 */
export function SprintReport({ report, loading = false, error = null, onRetry = null }) {
  if (loading) {
    return (
      <div className="space-y-3">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          {Array.from({ length: 4 }).map((_, idx) => (
            <Skeleton key={`report-metric-${idx}`} className="h-20 w-full rounded-lg" />
          ))}
        </div>
        <Skeleton className="h-40 w-full rounded-lg" />
        <Skeleton className="h-40 w-full rounded-lg" />
      </div>
    );
  }

  if (error) {
    return (
      <ErrorState
        title="Unable to load sprint report"
        description={error}
        onRetry={onRetry}
      />
    );
  }

  if (!report) {
    return (
      <p className="text-xs text-ink-muted rounded-lg border border-dashed border-line bg-subtle/40 px-3.5 py-3">
        No report data is available for this sprint.
      </p>
    );
  }

  const total = Number(report.totalTasks ?? 0);
  const completed = Number(report.completedTasks ?? 0);
  const remaining = Number(report.remainingTasks ?? 0);
  const completionPercentage = Number(report.completionPercentage ?? 0);
  const isSnapshot =
    report.status === SPRINT_STATUS.COMPLETED || report.status === SPRINT_STATUS.CANCELLED;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-ink">{report.sprintName}</p>
          <p className="text-[11px] text-ink-muted">
            {formatDateRange(report.startDate, report.endDate)}
            {report.projectKey ? ` · ${report.projectKey}` : ''}
          </p>
        </div>
        <div className="text-right text-[11px] text-ink-faint">
          <p>Started: {formatDateTime(report.startedAt)}</p>
          <p>Closed: {formatDateTime(report.completedAt)}</p>
        </div>
      </div>

      {isSnapshot && (
        <p className="text-[11px] text-ink-muted rounded-lg border border-line bg-subtle/60 px-3.5 py-2.5">
          This report is a snapshot recorded when the sprint was{' '}
          {report.status === SPRINT_STATUS.CANCELLED ? 'cancelled' : 'completed'}. Task statuses
          reflect their state at that moment.
        </p>
      )}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <MetricTile icon={ListChecks} label="Total tasks" value={total} />
        <MetricTile
          icon={CheckCircle2}
          label="Completed"
          value={completed}
          accent="text-emerald-700"
        />
        <MetricTile icon={Clock} label="Remaining" value={remaining} accent="text-amber-700" />
        <MetricTile
          icon={PieChart}
          label="Completion"
          value={`${completionPercentage}%`}
          accent="text-brand-600"
        />
      </div>

      <div className="rounded-lg border border-line bg-subtle/60 px-3.5 py-3 space-y-2.5">
        <div className="flex items-center justify-between text-[11px]">
          <span className="font-semibold uppercase tracking-wider text-ink-muted">
            Completion
          </span>
          <span className="font-mono tabular-nums text-ink-muted">
            {completed}/{total} done
          </span>
        </div>
        <div className="h-2 rounded-full bg-subtle overflow-hidden">
          <div
            className="h-full rounded-full bg-emerald-400/80"
            style={{ width: `${Math.min(Math.max(completionPercentage, 0), 100)}%` }}
            aria-hidden="true"
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 pt-1">
          <DistributionRow
            label="To do"
            count={Number(report.todoTasks ?? 0)}
            total={total}
            barClass="bg-ink-faint"
          />
          <DistributionRow
            label="In progress"
            count={Number(report.inProgressTasks ?? 0)}
            total={total}
            barClass="bg-brand-500"
          />
          <DistributionRow
            label="In review"
            count={Number(report.inReviewTasks ?? 0)}
            total={total}
            barClass="bg-sky-500"
          />
          <DistributionRow
            label="Blocked"
            count={Number(report.blockedTasks ?? 0)}
            total={total}
            barClass="bg-red-400"
          />
        </div>
      </div>

      <ReportTaskList
        title="Completed tasks"
        tasks={Array.isArray(report.completedTaskList) ? report.completedTaskList : []}
        emptyLabel="No tasks were completed in this sprint snapshot."
      />

      <ReportTaskList
        title="Incomplete tasks"
        tasks={Array.isArray(report.incompleteTaskList) ? report.incompleteTaskList : []}
        emptyLabel="All sprint tasks were completed."
      />
    </div>
  );
}
