import { BarChart3, Calendar, CheckSquare, Pencil, Target } from 'lucide-react';
import { Avatar } from '../../../components/common/Avatar';
import { Button } from '../../../components/common/Button';
import { formatDateRange } from '../../../utils/formatters';
import { SprintStatusBadge } from './SprintStatusBadge';

/**
 * Sprint summary card consuming exact SprintSummaryResponse fields:
 * { id, projectId, projectKey, name, goal, startDate, endDate, status, taskCount,
 *   startedAt, completedAt, createdBy, createdAt, updatedAt }
 */
export function SprintCard({ sprint, canEdit = false, onOpen, onEdit, onReport }) {
  const taskCount = Number(sprint.taskCount ?? 0);

  const open = (tab = 'overview') => onOpen?.(sprint, tab);

  return (
    <article
      onClick={() => open('overview')}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          open('overview');
        }
      }}
      role="button"
      tabIndex={0}
      aria-label={`Open sprint ${sprint.name}`}
      className="group relative overflow-hidden bg-surface border border-line hover:border-brand-200 rounded-xl p-5 flex flex-col justify-between gap-4 transition-colors duration-150 cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/50"
    >
      <span className="absolute inset-x-0 top-0 h-0.5 bg-brand-500 opacity-0 transition-opacity group-hover:opacity-100" aria-hidden="true" />
      <div className="space-y-3 min-w-0">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-sm font-semibold text-ink truncate transition-colors group-hover:text-brand-700">{sprint.name}</h3>
              <SprintStatusBadge status={sprint.status} />
            </div>
            <p className="mt-1 text-[11px] font-mono text-ink-faint tabular-nums">
              {sprint.projectKey ? `${sprint.projectKey} · Sprint #${sprint.id}` : `Sprint #${sprint.id}`}
            </p>
          </div>
        </div>

        <p className="text-xs text-ink-muted leading-relaxed line-clamp-2 min-h-[2rem]">
          {sprint.goal?.trim() || 'No sprint goal recorded.'}
        </p>

        <div className="grid grid-cols-2 gap-2.5 text-xs">
          <div className="flex items-center gap-2 text-ink-muted min-w-0">
            <Calendar className="h-3.5 w-3.5 text-ink-faint shrink-0" aria-hidden="true" />
            <span className="truncate">{formatDateRange(sprint.startDate, sprint.endDate)}</span>
          </div>
          <div className="flex items-center gap-2 text-ink-muted">
            <CheckSquare className="h-3.5 w-3.5 text-ink-faint shrink-0" aria-hidden="true" />
            <span className="font-mono tabular-nums">
              {taskCount} {taskCount === 1 ? 'task' : 'tasks'}
            </span>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between gap-3 pt-3 border-t border-line">
        <div className="flex items-center gap-2 min-w-0">
          {sprint.createdBy ? (
            <>
              <Avatar
                name={sprint.createdBy.fullName}
                color={sprint.createdBy.avatarColor}
                size="xs"
              />
              <span className="text-[11px] text-ink-muted truncate">
                {sprint.createdBy.fullName}
              </span>
            </>
          ) : (
            <span className="inline-flex items-center gap-1.5 text-[11px] text-ink-faint">
              <Target className="h-3.5 w-3.5" aria-hidden="true" />
              No owner recorded
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          <Button
            variant="ghost"
            size="sm"
            onClick={(event) => {
              event.stopPropagation();
              open('report');
            }}
            leftIcon={<BarChart3 className="h-3.5 w-3.5" />}
            aria-label={`View report for ${sprint.name}`}
          >
            Report
          </Button>
          {canEdit && (
            <Button
              variant="ghost"
              size="sm"
              onClick={(event) => {
                event.stopPropagation();
                onEdit?.(sprint);
              }}
              leftIcon={<Pencil className="h-3.5 w-3.5" />}
              aria-label={`Edit ${sprint.name}`}
            >
              Edit
            </Button>
          )}
        </div>
      </div>
    </article>
  );
}
