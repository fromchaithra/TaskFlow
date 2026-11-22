import { ArrowRight, History, MessageSquare } from 'lucide-react';
import { Avatar } from '../../components/common/Avatar';
import { formatDateTime } from '../../utils/formatters';
import {
  PRIORITY_META,
  TASK_HISTORY_FIELD_LABELS,
  TASK_STATUS_META,
} from './taskConstants';
import { TaskPriorityBadge } from './components/TaskPriorityBadge';
import { TaskStatusBadge } from './components/TaskStatusBadge';

function renderFieldValue(fieldName, value) {
  if (value === null || value === undefined || value === '') {
    return <span className="text-ink-faint italic">None</span>;
  }

  if ((fieldName === 'status' || fieldName === 'CREATED') && TASK_STATUS_META[value]) {
    return <TaskStatusBadge status={value} />;
  }

  if (fieldName === 'priority' && PRIORITY_META[value]) {
    return <TaskPriorityBadge priority={value} showIcon={false} />;
  }

  if (fieldName === 'estimated_hours') {
    return <span className="font-mono tabular-nums text-ink">{value}h</span>;
  }

  return <span className="text-ink break-words">{value}</span>;
}

/**
 * Renders chronological audit history from TaskDetailResponse.history (TaskHistoryResponse[]).
 */
export function TaskHistory({ history = [] }) {
  if (!Array.isArray(history) || history.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-line bg-subtle/40 px-4 py-6 text-center">
        <History className="h-4 w-4 text-ink-faint mx-auto mb-2" aria-hidden="true" />
        <p className="text-xs text-ink-muted">No activity recorded for this task yet.</p>
      </div>
    );
  }

  return (
    <ol className="space-y-3">
      {history.map((entry) => {
        const actor = entry.changedBy;
        const fieldLabel = TASK_HISTORY_FIELD_LABELS[entry.fieldName] || entry.fieldName;
        const isCreated = entry.fieldName === 'CREATED';

        return (
          <li
            key={entry.id}
            className="rounded-lg bg-canvas border border-line px-3.5 py-3 space-y-2"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-2 min-w-0">
                {actor ? (
                  <Avatar name={actor.fullName} color={actor.avatarColor} size="xs" />
                ) : (
                  <span className="h-6 w-6 rounded bg-subtle inline-flex items-center justify-center text-[10px] text-ink-muted shrink-0">
                    SY
                  </span>
                )}
                <div className="min-w-0">
                  <span className="text-xs font-medium text-ink">
                    {actor?.fullName || 'System'}
                  </span>{' '}
                  <span className="text-xs text-ink-muted">
                    {isCreated ? 'created this task' : `updated ${fieldLabel.toLowerCase()}`}
                  </span>
                </div>
              </div>

              <time className="text-[11px] text-ink-faint whitespace-nowrap tabular-nums shrink-0">
                {formatDateTime(entry.createdAt)}
              </time>
            </div>

            {isCreated ? (
              <div className="flex items-center gap-2 flex-wrap text-xs pl-8">
                <span className="text-ink-faint">Initial status:</span>
                {renderFieldValue('status', entry.newValue)}
              </div>
            ) : (
              <div className="flex items-center gap-2 flex-wrap text-xs pl-8">
                {renderFieldValue(entry.fieldName, entry.oldValue)}
                <ArrowRight className="h-3 w-3 text-ink-faint shrink-0" aria-hidden="true" />
                {renderFieldValue(entry.fieldName, entry.newValue)}
              </div>
            )}

            {entry.remarks && (
              <div className="pl-8 pt-0.5 flex items-start gap-1.5 text-xs text-ink-muted">
                <MessageSquare className="h-3.5 w-3.5 text-ink-faint shrink-0 mt-0.5" aria-hidden="true" />
                <span className="leading-relaxed">{entry.remarks}</span>
              </div>
            )}
          </li>
        );
      })}
    </ol>
  );
}
