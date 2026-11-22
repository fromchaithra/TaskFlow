import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { AlertTriangle, Calendar, Clock, GitBranch, GripVertical } from 'lucide-react';
import { formatDate } from '../../../utils/formatters';
import { TaskAssignee } from './TaskAssignee';
import { TaskPriorityBadge } from './TaskPriorityBadge';

/**
 * Presentational card surface shared between Sortable TaskCard and DragOverlay.
 * Consumes exact TaskSummaryResponse fields.
 */
export function TaskCardSurface({
  task,
  draggable = false,
  isDragging = false,
  isOverlay = false,
  isSaving = false,
  onOpen,
  dragHandleProps = {},
}) {
  return (
    <article
      onClick={() => {
        if (!isDragging && !isOverlay) {
          onOpen?.(task);
        }
      }}
      onKeyDown={(event) => {
        if ((event.key === 'Enter' || event.key === ' ') && !isOverlay) {
          event.preventDefault();
          onOpen?.(task);
        }
      }}
      role="button"
      tabIndex={isOverlay ? -1 : 0}
      aria-label={`${task.taskKey}: ${task.title}`}
      className={[
        'group rounded-lg bg-subtle border p-3.5 space-y-2.5 transition-colors duration-150 select-none',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/50',
        isOverlay
          ? 'border-brand-500/60 shadow-2xl shadow-black/60 cursor-grabbing'
          : isDragging
            ? 'border-dashed border-line-strong opacity-35'
            : 'border-line hover:border-line-strong cursor-pointer',
        isSaving ? 'opacity-60 pointer-events-none' : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {/* Top row: Task Key + Priority + Drag Handle */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-[11px] font-mono font-medium text-brand-600 tracking-wide truncate">
            {task.taskKey}
          </span>
          {task.parentTaskId && (
            <span
              className="inline-flex items-center text-ink-faint"
              title="Subtask"
              aria-label="Subtask"
            >
              <GitBranch className="h-3 w-3" aria-hidden="true" />
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          <TaskPriorityBadge priority={task.priority} />
          {draggable && (
            <button
              type="button"
              aria-label={`Drag ${task.taskKey}`}
              onClick={(e) => e.stopPropagation()}
              className="p-0.5 -mr-1 rounded text-ink-faint hover:text-ink cursor-grab active:cursor-grabbing touch-none"
              {...dragHandleProps}
            >
              <GripVertical className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          )}
        </div>
      </div>

      {/* Title */}
      <h4 className="text-[13px] font-medium text-ink leading-snug line-clamp-2 transition-colors group-hover:text-brand-700">
        {task.title}
      </h4>

      {/* Footer row: Due date / estimate + Assignee */}
      <div className="flex items-center justify-between gap-2 pt-2 border-t border-line">
        <div className="flex items-center gap-2.5 min-w-0 text-[11px] text-ink-muted">
          {task.dueDate ? (
            <span
              className={[
                'inline-flex items-center gap-1 tabular-nums whitespace-nowrap',
                task.overdue ? 'text-red-600 font-medium' : 'text-ink-muted',
              ].join(' ')}
            >
              {task.overdue ? (
                <AlertTriangle className="h-3 w-3 shrink-0" aria-hidden="true" />
              ) : (
                <Calendar className="h-3 w-3 text-ink-faint shrink-0" aria-hidden="true" />
              )}
              <span>{formatDate(task.dueDate)}</span>
            </span>
          ) : (
            <span className="text-ink-faint">No due date</span>
          )}

          {task.estimatedHours != null && (
            <span className="inline-flex items-center gap-0.5 font-mono tabular-nums text-ink-faint whitespace-nowrap">
              <Clock className="h-3 w-3 shrink-0" aria-hidden="true" />
              {task.estimatedHours}h
            </span>
          )}
        </div>

        <TaskAssignee assignee={task.assignee} compact />
      </div>
    </article>
  );
}

export function TaskCard({ task, draggable = false, isSaving = false, onOpen }) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: `task-${task.id}`,
    data: {
      type: 'task',
      task,
      status: task.status,
    },
    disabled: !draggable || isSaving,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div ref={setNodeRef} style={style}>
      <TaskCardSurface
        task={task}
        draggable={draggable}
        isDragging={isDragging}
        isSaving={isSaving}
        onOpen={onOpen}
        dragHandleProps={{ ...attributes, ...listeners }}
      />
    </div>
  );
}
