import { useDroppable } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { Ban, ChevronDown, Inbox } from 'lucide-react';
import { Button } from '../../../components/common/Button';
import { Skeleton } from '../../../components/common/Loading';
import { TASK_STATUS_META } from '../taskConstants';
import { TaskCard } from './TaskCard';

/**
 * Droppable Kanban Column mapped strictly to KanbanColumnResponse:
 * { status, totalCount, page, limit, hasMore, tasks: TaskSummaryResponse[] }
 */
export function TaskColumn({
  status,
  column,
  loading = false,
  loadingMore = false,
  onLoadMore = null,
  activeDragTask = null,
  isValidDropTarget = false,
  isSameColumnTarget = false,
  canDragCard,
  savingTaskIds,
  onOpenTask,
}) {
  const { setNodeRef, isOver } = useDroppable({
    id: `column-${status}`,
    data: {
      type: 'column',
      status,
    },
  });

  const meta = TASK_STATUS_META[status] || {
    label: status,
    accentDot: 'bg-ink-faint',
  };

  const tasks = Array.isArray(column?.tasks) ? column.tasks : [];
  const totalCount = Number(column?.totalCount ?? tasks.length);
  const hasMore = Boolean(column?.hasMore);

  const isDraggingActive = Boolean(activeDragTask);
  const isBlockedDrop = isDraggingActive && !isValidDropTarget && !isSameColumnTarget;

  const containerBorderClass =
    isOver && (isValidDropTarget || isSameColumnTarget)
      ? 'border-brand-500 bg-surface'
      : isOver && isBlockedDrop
        ? 'border-red-200 bg-red-50'
        : isDraggingActive && isValidDropTarget
          ? 'border-brand-500/40 bg-surface'
          : 'border-line bg-surface';

  return (
    <section
      ref={setNodeRef}
      aria-label={`${meta.label} column (${totalCount} tasks)`}
      className={[
        'w-[296px] sm:w-[308px] shrink-0 rounded-xl border flex flex-col max-h-[calc(100vh-290px)] min-h-[420px] transition-colors duration-150',
        containerBorderClass,
      ].join(' ')}
    >
      {/* Column Header */}
      <header className="px-3.5 py-3 border-b border-line flex items-center justify-between gap-2 shrink-0">
        <div className="flex items-center gap-2 min-w-0">
          <span
            className={`h-2 w-2 rounded-full shrink-0 ${meta.accentDot}`}
            aria-hidden="true"
          />
          <h3 className="text-xs font-semibold uppercase tracking-wider text-ink truncate">
            {meta.label}
          </h3>
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          {isOver && isBlockedDrop && (
            <span
              className="inline-flex items-center gap-1 text-[10px] font-medium text-red-600"
              title="Transition not allowed by workflow or role"
            >
              <Ban className="h-3 w-3" aria-hidden="true" />
              Restricted
            </span>
          )}
          <span className="px-2 py-0.5 rounded bg-subtle border border-line text-[11px] font-mono tabular-nums text-ink-muted">
            {totalCount}
          </span>
        </div>
      </header>

      {/* Column Card List */}
      <div className="flex-1 overflow-y-auto p-2.5 space-y-2.5">
        {loading ? (
          Array.from({ length: 3 }).map((_, idx) => (
            <div
              key={`col-skel-${status}-${idx}`}
              className="rounded-lg bg-subtle border border-line p-3.5 space-y-2.5"
            >
              <div className="flex items-center justify-between">
                <Skeleton className="h-4 w-16" />
                <Skeleton className="h-4 w-14" />
              </div>
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-3.5 w-2/3" />
            </div>
          ))
        ) : tasks.length === 0 ? (
          <div className="h-32 rounded-lg border border-dashed border-line bg-subtle/40 flex flex-col items-center justify-center text-center px-4">
            <Inbox className="h-4 w-4 text-ink-faint mb-1.5" aria-hidden="true" />
            <p className="text-xs text-ink-faint">
              {isDraggingActive && isValidDropTarget
                ? `Drop here to move to ${meta.label}`
                : `No tasks in ${meta.label}`}
            </p>
          </div>
        ) : (
          <SortableContext
            items={tasks.map((t) => `task-${t.id}`)}
            strategy={verticalListSortingStrategy}
          >
            {tasks.map((task) => (
              <TaskCard
                key={task.id}
                task={task}
                draggable={canDragCard ? canDragCard(task) : false}
                isSaving={savingTaskIds ? savingTaskIds.has(task.id) : false}
                onOpen={onOpenTask}
              />
            ))}
          </SortableContext>
        )}

        {hasMore && !loading && (
          <div className="pt-1">
            <Button
              variant="ghost"
              size="sm"
              fullWidth
              loading={loadingMore}
              onClick={() => onLoadMore?.(status)}
              leftIcon={<ChevronDown className="h-3.5 w-3.5" />}
            >
              Load more ({totalCount - tasks.length} remaining)
            </Button>
          </div>
        )}
      </div>
    </section>
  );
}
