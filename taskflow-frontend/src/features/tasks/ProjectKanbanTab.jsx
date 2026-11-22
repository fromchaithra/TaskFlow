import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  closestCorners,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import { arrayMove, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { Lock, Plus, RefreshCw } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { ErrorState } from '../../components/common/Loading';
import { useAuth } from '../../hooks/useAuth';
import { useDebounce } from '../../hooks/useDebounce';
import { useToast } from '../../hooks/useToast';
import { projectService } from '../../services/projectService';
import { taskService } from '../../services/taskService';
import { ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import {
  ALLOWED_TASK_TRANSITIONS,
  canDragTask,
  canReorderWithinColumn,
  getPermittedTaskTransitions,
  KANBAN_COLUMN_ORDER,
  TASK_STATUS,
  TASK_STATUS_META,
} from './taskConstants';
import { TaskCardSurface } from './components/TaskCard';
import { TaskColumn } from './components/TaskColumn';
import { TaskFilters } from './components/TaskFilters';
import { TaskDetailModal } from './TaskDetailModal';
import { TaskFormModal } from './TaskFormModal';

const LIMIT_PER_COLUMN = 25;

function cloneColumns(columns) {
  const next = {};
  for (const status of KANBAN_COLUMN_ORDER) {
    const col = columns?.[status];
    next[status] = col
      ? { ...col, tasks: Array.isArray(col.tasks) ? [...col.tasks] : [] }
      : { status, totalCount: 0, page: 0, limit: LIMIT_PER_COLUMN, hasMore: false, tasks: [] };
  }
  return next;
}

function findTaskLocation(columns, taskId) {
  for (const status of KANBAN_COLUMN_ORDER) {
    const list = columns?.[status]?.tasks || [];
    const index = list.findIndex((item) => item.id === taskId);
    if (index !== -1) {
      return { status, index, task: list[index] };
    }
  }
  return null;
}

function explainBlockedTransition(task, targetStatus, currentUser, canManageProject) {
  const allowedByWorkflow = ALLOWED_TASK_TRANSITIONS[task.status] || [];
  const fromLabel = TASK_STATUS_META[task.status]?.label || task.status;
  const toLabel = TASK_STATUS_META[targetStatus]?.label || targetStatus;

  if (!allowedByWorkflow.includes(targetStatus)) {
    return `Workflow does not allow moving a task directly from ${fromLabel} to ${toLabel}.`;
  }

  if (!canManageProject && currentUser?.role === ROLES.DEVELOPER) {
    if (task.assignee?.id !== currentUser.id) {
      return 'Developers can only change the status of tasks assigned to them.';
    }
    if (targetStatus === TASK_STATUS.DONE) {
      return 'Developers cannot mark tasks Done directly. Move to In Review for QA or Project Manager approval.';
    }
    if (task.status === TASK_STATUS.DONE) {
      return 'Developers cannot reopen completed tasks.';
    }
  }

  if (!canManageProject && currentUser?.role === ROLES.QA_TESTER) {
    return 'QA testers can review In Review tasks, reopen Done tasks, or transition tasks assigned to them.';
  }

  return `You are not permitted to move ${task.taskKey} from ${fromLabel} to ${toLabel}.`;
}

/**
 * Interactive drag-and-drop Kanban Board backed by:
 * - GET   /api/v1/projects/{projectId}/kanban
 * - PATCH /api/v1/tasks/{id}/status
 */
export function ProjectKanbanTab({ project, canManageProject = false, onProjectMutated }) {
  const { user } = useAuth();
  const { addToast } = useToast();

  const isArchived = project?.status === 'ARCHIVED';
  const isCompleted = project?.status === 'COMPLETED';
  const canCreateTask = canManageProject && !isArchived && !isCompleted;

  const [searchInput, setSearchInput] = useState('');
  const debouncedSearch = useDebounce(searchInput, 300);
  const [priorityFilter, setPriorityFilter] = useState('');
  const [assigneeFilter, setAssigneeFilter] = useState('');
  const [sprintFilter, setSprintFilter] = useState('');

  const [members, setMembers] = useState([]);
  const [sprints, setSprints] = useState([]);

  const [columns, setColumns] = useState(() => cloneColumns({}));
  const [totalTasks, setTotalTasks] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [loadingMoreStatus, setLoadingMoreStatus] = useState(null);

  const [activeDragTask, setActiveDragTask] = useState(null);
  const [savingTaskIds, setSavingTaskIds] = useState(() => new Set());
  const inFlightTaskIdsRef = useRef(new Set());

  const [createOpen, setCreateOpen] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState(null);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 6 },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  // Load project members and sprints for board filters
  useEffect(() => {
    if (!project?.id) return undefined;
    let cancelled = false;

    Promise.all([
      projectService.listProjectMembers(project.id).catch(() => []),
      taskService.listProjectSprints(project.id).catch(() => []),
    ]).then(([membersList, sprintsList]) => {
      if (cancelled) return;
      const map = new Map();
      if (project.manager?.id) map.set(project.manager.id, project.manager);
      if (Array.isArray(membersList)) {
        for (const item of membersList) {
          if (item?.user?.id) map.set(item.user.id, item.user);
        }
      }
      setMembers(Array.from(map.values()));
      setSprints(Array.isArray(sprintsList) ? sprintsList : []);
    });

    return () => {
      cancelled = true;
    };
  }, [project?.id, project?.manager]);

  const fetchBoard = useCallback(async () => {
    if (!project?.id) return;
    setLoading(true);
    setError(null);
    try {
      const board = await taskService.getProjectKanbanBoard(project.id, {
        search: debouncedSearch || undefined,
        priority: priorityFilter || undefined,
        assigneeId: assigneeFilter ? Number(assigneeFilter) : undefined,
        sprintId: sprintFilter ? Number(sprintFilter) : undefined,
        page: 0,
        limitPerColumn: LIMIT_PER_COLUMN,
      });
      setColumns(cloneColumns(board?.columns));
      setTotalTasks(Number(board?.totalTasks ?? 0));
    } catch (err) {
      const parsed = parseApiError(err);
      setError({ title: 'Unable to load Kanban board', message: parsed.message });
    } finally {
      setLoading(false);
    }
  }, [project?.id, debouncedSearch, priorityFilter, assigneeFilter, sprintFilter]);

  useEffect(() => {
    fetchBoard();
  }, [fetchBoard]);

  const handleLoadMoreColumn = async (status) => {
    const col = columns[status];
    if (!col?.hasMore || loadingMoreStatus) return;

    const nextPage = (col.page ?? 0) + 1;
    setLoadingMoreStatus(status);
    try {
      const board = await taskService.getProjectKanbanBoard(project.id, {
        search: debouncedSearch || undefined,
        priority: priorityFilter || undefined,
        assigneeId: assigneeFilter ? Number(assigneeFilter) : undefined,
        sprintId: sprintFilter ? Number(sprintFilter) : undefined,
        page: nextPage,
        limitPerColumn: LIMIT_PER_COLUMN,
      });
      const fetchedCol = board?.columns?.[status];
      if (fetchedCol) {
        setColumns((prev) => {
          const existing = prev[status]?.tasks || [];
          const existingIds = new Set(existing.map((t) => t.id));
          const appended = (fetchedCol.tasks || []).filter((t) => !existingIds.has(t.id));
          return {
            ...prev,
            [status]: {
              ...fetchedCol,
              tasks: [...existing, ...appended],
            },
          };
        });
      }
    } catch (err) {
      addToast({
        type: 'error',
        title: 'Unable to load more tasks',
        message: parseApiError(err).message,
      });
    } finally {
      setLoadingMoreStatus(null);
    }
  };

  const assigneeOptions = useMemo(() => {
    const opts = [{ value: '', label: 'All Assignees' }];
    for (const member of members) {
      opts.push({ value: String(member.id), label: member.fullName });
    }
    return opts;
  }, [members]);

  const sprintOptions = useMemo(() => {
    const opts = [{ value: '', label: 'All Sprints' }];
    for (const sprint of sprints) {
      opts.push({ value: String(sprint.id), label: `${sprint.name} (${sprint.status})` });
    }
    return opts;
  }, [sprints]);

  const hasActiveFilters = Boolean(
    searchInput.trim() || priorityFilter || assigneeFilter || sprintFilter
  );

  const handleClearFilters = () => {
    setSearchInput('');
    setPriorityFilter('');
    setAssigneeFilter('');
    setSprintFilter('');
  };

  const canDragCard = useCallback(
    (task) => canDragTask(task, user, canManageProject, isArchived),
    [user, canManageProject, isArchived]
  );

  const activeDragPermittedTargets = useMemo(() => {
    if (!activeDragTask) return new Set();
    return new Set(getPermittedTaskTransitions(activeDragTask, user, canManageProject, isArchived));
  }, [activeDragTask, user, canManageProject, isArchived]);

  const handleDragStart = (event) => {
    const draggedTask = event.active?.data?.current?.task;
    if (draggedTask) {
      setActiveDragTask(draggedTask);
    }
  };

  const handleDragCancel = () => {
    setActiveDragTask(null);
  };

  const handleDragEnd = async (event) => {
    const { active, over } = event;
    setActiveDragTask(null);

    if (!active || !over) return;

    const draggedTask = active.data?.current?.task;
    if (!draggedTask) return;

    // Prevent duplicate concurrent requests for the same card
    if (inFlightTaskIdsRef.current.has(draggedTask.id)) return;

    const sourceLoc = findTaskLocation(columns, draggedTask.id);
    if (!sourceLoc) return;

    const overData = over.data?.current;
    let targetStatus = null;
    let targetIndex = 0;

    if (overData?.type === 'column') {
      targetStatus = overData.status;
      targetIndex = (columns[targetStatus]?.tasks || []).length;
    } else if (overData?.type === 'task' && overData.task) {
      const overLoc = findTaskLocation(columns, overData.task.id);
      if (!overLoc) return;
      targetStatus = overLoc.status;
      targetIndex = overLoc.index;
    } else if (typeof over.id === 'string' && over.id.startsWith('column-')) {
      targetStatus = over.id.replace('column-', '');
      targetIndex = (columns[targetStatus]?.tasks || []).length;
    } else {
      return;
    }

    if (!targetStatus || !columns[targetStatus]) return;

    const sourceStatus = sourceLoc.status;
    const isSameColumn = sourceStatus === targetStatus;

    if (isSameColumn) {
      if (sourceLoc.index === targetIndex) return;
      if (!canReorderWithinColumn(draggedTask, user, canManageProject, isArchived)) {
        addToast({
          type: 'warning',
          title: 'Reorder not permitted',
          message: 'Only project managers, admins, or the task assignee can reorder cards.',
        });
        return;
      }

      const snapshot = cloneColumns(columns);
      const reordered = arrayMove(snapshot[sourceStatus].tasks, sourceLoc.index, targetIndex).map(
        (item, idx) => ({ ...item, boardOrder: idx })
      );

      setColumns((prev) => ({
        ...prev,
        [sourceStatus]: {
          ...prev[sourceStatus],
          tasks: reordered,
        },
      }));

      inFlightTaskIdsRef.current.add(draggedTask.id);
      setSavingTaskIds((prev) => new Set(prev).add(draggedTask.id));

      try {
        await taskService.updateTaskStatus(draggedTask.id, {
          status: sourceStatus,
          boardOrder: targetIndex,
        });
      } catch (err) {
        setColumns(snapshot);
        addToast({
          type: 'error',
          title: 'Failed to reorder task',
          message: parseApiError(err).message,
        });
      } finally {
        inFlightTaskIdsRef.current.delete(draggedTask.id);
        setSavingTaskIds((prev) => {
          const next = new Set(prev);
          next.delete(draggedTask.id);
          return next;
        });
      }
      return;
    }

    // Cross-column status transition
    const permittedTargets = getPermittedTaskTransitions(
      draggedTask,
      user,
      canManageProject,
      isArchived
    );

    if (!permittedTargets.includes(targetStatus)) {
      addToast({
        type: 'warning',
        title: 'Transition not allowed',
        message: explainBlockedTransition(draggedTask, targetStatus, user, canManageProject),
      });
      return;
    }

    const snapshot = cloneColumns(columns);
    const optimistic = cloneColumns(columns);

    const [movedCard] = optimistic[sourceStatus].tasks.splice(sourceLoc.index, 1);
    optimistic[sourceStatus].totalCount = Math.max(0, Number(optimistic[sourceStatus].totalCount) - 1);

    const updatedCard = {
      ...movedCard,
      status: targetStatus,
      boardOrder: targetIndex,
    };
    const clampedIndex = Math.min(targetIndex, optimistic[targetStatus].tasks.length);
    optimistic[targetStatus].tasks.splice(clampedIndex, 0, updatedCard);
    optimistic[targetStatus].totalCount = Number(optimistic[targetStatus].totalCount) + 1;

    setColumns(optimistic);
    inFlightTaskIdsRef.current.add(draggedTask.id);
    setSavingTaskIds((prev) => new Set(prev).add(draggedTask.id));

    try {
      const serverTask = await taskService.updateTaskStatus(draggedTask.id, {
        status: targetStatus,
        boardOrder: clampedIndex,
      });

      setColumns((prev) => {
        const next = cloneColumns(prev);
        const colTasks = next[targetStatus].tasks;
        const idx = colTasks.findIndex((t) => t.id === draggedTask.id);
        if (idx !== -1) {
          colTasks[idx] = {
            ...colTasks[idx],
            status: serverTask.status,
            boardOrder: serverTask.boardOrder,
            overdue: serverTask.overdue,
            version: serverTask.version,
          };
        }
        return next;
      });

      addToast({
        type: 'success',
        title: 'Task moved',
        message: `${draggedTask.taskKey} moved to ${TASK_STATUS_META[targetStatus]?.label || targetStatus}.`,
      });
      onProjectMutated?.();
    } catch (err) {
      // Roll back card cleanly to original column
      setColumns(snapshot);
      addToast({
        type: 'error',
        title: 'Status update failed',
        message: parseApiError(err).message,
      });
    } finally {
      inFlightTaskIdsRef.current.delete(draggedTask.id);
      setSavingTaskIds((prev) => {
        const next = new Set(prev);
        next.delete(draggedTask.id);
        return next;
      });
    }
  };

  return (
    <div className="space-y-4">
      {isArchived && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 flex items-center gap-2.5 text-xs text-amber-700">
          <Lock className="h-4 w-4 text-amber-600 shrink-0" aria-hidden="true" />
          <span>
            This project is archived. The Kanban board is read-only until an Administrator
            reactivates the project.
          </span>
        </div>
      )}

      {/* Board Toolbar */}
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="text-xs text-ink-muted">
            <span className="font-mono tabular-nums text-ink font-medium">{totalTasks}</span>{' '}
            {totalTasks === 1 ? 'task' : 'tasks'} across 5 workflow columns
            {!isArchived && (
              <span className="hidden sm:inline text-ink-faint">
                {' '}
                · Drag permitted cards to transition status, or click any card for details
              </span>
            )}
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={fetchBoard}
              loading={loading}
              leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
            >
              Refresh
            </Button>
            {canCreateTask && (
              <Button
                size="sm"
                onClick={() => setCreateOpen(true)}
                leftIcon={<Plus className="h-4 w-4" />}
              >
                Create Task
              </Button>
            )}
          </div>
        </div>

        <TaskFilters
          searchValue={searchInput}
          onSearchChange={setSearchInput}
          showStatus={false}
          priorityValue={priorityFilter}
          onPriorityChange={setPriorityFilter}
          assigneeValue={assigneeFilter}
          onAssigneeChange={setAssigneeFilter}
          assigneeOptions={assigneeOptions}
          sprintValue={sprintFilter}
          onSprintChange={setSprintFilter}
          sprintOptions={sprintOptions}
          onClear={handleClearFilters}
          hasActiveFilters={hasActiveFilters}
        />
      </div>

      {/* Board Surface */}
      {error && !loading ? (
        <ErrorState title={error.title} description={error.message} onRetry={fetchBoard} />
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCorners}
          onDragStart={handleDragStart}
          onDragCancel={handleDragCancel}
          onDragEnd={handleDragEnd}
        >
          <div className="overflow-x-auto pb-3 -mx-1 px-1">
            <div className="flex items-start gap-3.5 min-w-max">
              {KANBAN_COLUMN_ORDER.map((status) => (
                <TaskColumn
                  key={status}
                  status={status}
                  column={columns[status]}
                  loading={loading}
                  loadingMore={loadingMoreStatus === status}
                  onLoadMore={handleLoadMoreColumn}
                  activeDragTask={activeDragTask}
                  isValidDropTarget={activeDragPermittedTargets.has(status)}
                  isSameColumnTarget={
                    Boolean(activeDragTask) &&
                    activeDragTask.status === status &&
                    canReorderWithinColumn(activeDragTask, user, canManageProject, isArchived)
                  }
                  canDragCard={canDragCard}
                  savingTaskIds={savingTaskIds}
                  onOpenTask={(task) => setSelectedTaskId(task.id)}
                />
              ))}
            </div>
          </div>

          <DragOverlay dropAnimation={null}>
            {activeDragTask ? (
              <div className="w-[288px] sm:w-[296px] rotate-[1deg]">
                <TaskCardSurface task={activeDragTask} draggable isOverlay />
              </div>
            ) : null}
          </DragOverlay>
        </DndContext>
      )}

      {/* Create Task Modal */}
      {canCreateTask && (
        <TaskFormModal
          isOpen={createOpen}
          onClose={() => setCreateOpen(false)}
          mode="create"
          project={project}
          canManageProject={canManageProject}
          onSaved={() => {
            fetchBoard();
            onProjectMutated?.();
          }}
        />
      )}

      {/* Task Detail Modal */}
      <TaskDetailModal
        isOpen={Boolean(selectedTaskId)}
        onClose={() => setSelectedTaskId(null)}
        taskId={selectedTaskId}
        project={project}
        canManageProject={canManageProject}
        onSelectTask={(nextId) => setSelectedTaskId(nextId)}
        onTaskChanged={() => {
          fetchBoard();
          onProjectMutated?.();
        }}
      />
    </div>
  );
}
