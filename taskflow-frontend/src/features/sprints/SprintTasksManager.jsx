import { useCallback, useEffect, useMemo, useState } from 'react';
import { ListPlus, RefreshCw, Search, Trash2, UserPlus } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { useDebounce } from '../../hooks/useDebounce';
import { useToast } from '../../hooks/useToast';
import { sprintService } from '../../services/sprintService';
import { taskService } from '../../services/taskService';
import { parseApiError } from '../../utils/errorHandler';
import { TaskAssignee } from '../tasks/components/TaskAssignee';
import { TaskStatusBadge } from '../tasks/components/TaskStatusBadge';

const ELIGIBLE_PAGE_SIZE = 100;

/**
 * Inline backlog/scope editor for a sprint.
 * Eligible tasks are project tasks not currently assigned to any sprint. The backend
 * additionally rejects tasks assigned to another PLANNED/ACTIVE sprint.
 */
export function SprintTasksManager({ sprint, canManageTasks, onSprintUpdated, onReload }) {
  const toast = useToast();
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 300);
  const [reloadToken, setReloadToken] = useState(0);

  const [eligibleTasks, setEligibleTasks] = useState([]);
  const [loadingEligible, setLoadingEligible] = useState(false);
  const [eligibleError, setEligibleError] = useState(null);

  const [selectedIds, setSelectedIds] = useState([]);
  const [adding, setAdding] = useState(false);
  const [removingId, setRemovingId] = useState(null);
  const [pendingRemoveId, setPendingRemoveId] = useState(null);

  const assignedTasks = useMemo(
    () => (Array.isArray(sprint?.tasks) ? sprint.tasks : []),
    [sprint?.tasks]
  );

  const assignedIds = useMemo(
    () => new Set(assignedTasks.map((task) => task.id)),
    [assignedTasks]
  );

  const loadEligibleTasks = useCallback(async () => {
    if (!canManageTasks || !sprint?.projectId) return;
    setLoadingEligible(true);
    setEligibleError(null);

    try {
      const response = await taskService.listTasks({
        projectId: sprint.projectId,
        search: debouncedSearch || undefined,
        page: 0,
        size: ELIGIBLE_PAGE_SIZE,
        sortBy: 'createdAt',
        sortDir: 'desc',
      });
      const content = Array.isArray(response?.content) ? response.content : [];
      setEligibleTasks(
        content.filter((task) => task.sprintId == null && !assignedIds.has(task.id))
      );
    } catch (err) {
      setEligibleError(parseApiError(err).message);
    } finally {
      setLoadingEligible(false);
    }
  }, [canManageTasks, sprint?.projectId, debouncedSearch, assignedIds]);

  useEffect(() => {
    loadEligibleTasks();
  }, [loadEligibleTasks, reloadToken]);

  useEffect(() => {
    setSelectedIds([]);
  }, [debouncedSearch]);

  useEffect(() => {
    setSearch('');
    setSelectedIds([]);
    setPendingRemoveId(null);
  }, [sprint?.id]);

  useEffect(() => {
    setSelectedIds((prev) => prev.filter((id) => !assignedIds.has(id)));
  }, [assignedIds]);

  const toggleSelection = (taskId) => {
    setSelectedIds((prev) =>
      prev.includes(taskId) ? prev.filter((id) => id !== taskId) : [...prev, taskId]
    );
  };

  const handleAddSelected = async () => {
    if (adding || selectedIds.length === 0) return;
    setAdding(true);

    try {
      const updated = await sprintService.addTasksToSprint(
        sprint.projectId,
        sprint.id,
        selectedIds
      );
      toast.success(
        `${selectedIds.length} ${selectedIds.length === 1 ? 'task was' : 'tasks were'} added to “${sprint.name}”.`,
        'Sprint scope updated'
      );
      setSelectedIds([]);
      setReloadToken((prev) => prev + 1);
      onSprintUpdated?.(updated);
    } catch (err) {
      toast.error(parseApiError(err).message, 'Unable to add tasks');
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (task) => {
    if (removingId) return;
    setRemovingId(task.id);

    try {
      await sprintService.removeTaskFromSprint(sprint.projectId, sprint.id, task.id);
      toast.success(
        `${task.taskKey || 'Task'} was returned to the backlog.`,
        'Sprint scope updated'
      );
      setPendingRemoveId(null);
      setReloadToken((prev) => prev + 1);
      await onReload?.();
    } catch (err) {
      toast.error(parseApiError(err).message, 'Unable to remove task');
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <div className="space-y-5">
      <section className="space-y-2.5">
        <div className="flex items-center justify-between gap-2">
          <h4 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
            Tasks in this sprint
          </h4>
          <span className="text-[11px] font-mono text-ink-faint tabular-nums">
            {assignedTasks.length}
          </span>
        </div>

        {assignedTasks.length === 0 ? (
          <p className="text-xs text-ink-muted rounded-lg border border-dashed border-line bg-subtle/40 px-3.5 py-3">
            No tasks are assigned to this sprint yet. Add backlog tasks below.
          </p>
        ) : (
          <ul className="divide-y divide-line rounded-lg border border-line overflow-hidden">
            {assignedTasks.map((task) => (
              <li
                key={task.id}
                className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 px-3.5 py-2.5 bg-subtle/40"
              >
                <div className="min-w-0 flex items-center gap-2.5">
                  <span className="font-mono text-[11px] text-ink-faint tabular-nums shrink-0">
                    {task.taskKey}
                  </span>
                  <span className="text-xs text-ink truncate">{task.title}</span>
                  <TaskStatusBadge status={task.status} className="shrink-0" />
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <TaskAssignee assignee={task.assignee} compact />
                  {canManageTasks && (pendingRemoveId === task.id ? (
                    <span className="inline-flex items-center gap-1.5">
                      <Button
                        variant="danger"
                        size="sm"
                        loading={removingId === task.id}
                        onClick={() => handleRemove(task)}
                      >
                        Confirm
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setPendingRemoveId(null)}
                        disabled={removingId === task.id}
                      >
                        Keep
                      </Button>
                    </span>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      leftIcon={<Trash2 className="h-3.5 w-3.5" />}
                      onClick={() => setPendingRemoveId(task.id)}
                      aria-label={`Remove ${task.taskKey} from sprint`}
                    >
                      Remove
                    </Button>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {canManageTasks ? (
        <>
          <section className="space-y-2.5">
            <div className="flex items-center justify-between gap-2">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
                Eligible backlog tasks
              </h4>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setReloadToken((prev) => prev + 1)}
            loading={loadingEligible}
            leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
          >
            Refresh
          </Button>
        </div>

        <Input
          aria-label="Search backlog tasks"
          placeholder="Search project tasks by title or key..."
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          leftIcon={<Search className="h-4 w-4" />}
        />

        {loadingEligible ? (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, idx) => (
              <Skeleton key={`eligible-skel-${idx}`} className="h-11 w-full rounded-lg" />
            ))}
          </div>
        ) : eligibleError ? (
          <ErrorState
            title="Unable to load backlog tasks"
            description={eligibleError}
            onRetry={loadEligibleTasks}
          />
        ) : eligibleTasks.length === 0 ? (
          <p className="text-xs text-ink-muted rounded-lg border border-dashed border-line bg-subtle/40 px-3.5 py-3">
            {debouncedSearch
              ? 'No matching backlog tasks were found. Clear the search to see more.'
              : 'No eligible backlog tasks are available. Tasks already assigned to another sprint must be removed from that sprint first.'}
          </p>
        ) : (
          <>
            <ul className="max-h-64 overflow-y-auto divide-y divide-line rounded-lg border border-line">
              {eligibleTasks.map((task) => {
                const checked = selectedIds.includes(task.id);
                return (
                  <li key={task.id}>
                    <label className="flex items-start gap-2.5 px-3.5 py-2.5 bg-subtle/40 hover:bg-subtle/60 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleSelection(task.id)}
                        className="mt-0.5 h-4 w-4 rounded border-line-strong bg-canvas accent-brand-500"
                      />
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="font-mono text-[11px] text-ink-faint tabular-nums">
                            {task.taskKey}
                          </span>
                          <span className="text-xs text-ink">{task.title}</span>
                          <TaskStatusBadge status={task.status} />
                        </span>
                        <span className="mt-1 flex flex-wrap items-center gap-3 text-[11px] text-ink-faint">
                          <TaskAssignee assignee={task.assignee} compact />
                          {task.dueDate && <span>Due {task.dueDate}</span>}
                          {task.overdue && <span className="text-red-600">Overdue</span>}
                        </span>
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>

            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-[11px] text-ink-faint">
                Showing up to {ELIGIBLE_PAGE_SIZE} matching backlog tasks. Use search to narrow
                the list.
              </p>
              <Button
                size="sm"
                onClick={handleAddSelected}
                loading={adding}
                disabled={selectedIds.length === 0}
                leftIcon={<UserPlus className="h-3.5 w-3.5" />}
              >
                {selectedIds.length > 0
                  ? `Add ${selectedIds.length} selected`
                  : 'Add selected tasks'}
              </Button>
            </div>
          </>
        )}
          </section>

          <p className="flex items-start gap-2 text-[11px] text-ink-faint leading-relaxed">
            <ListPlus className="h-3.5 w-3.5 shrink-0 mt-0.5" aria-hidden="true" />
            Tasks assigned to another planned or active sprint cannot be added until they are
            removed from that sprint. Removing a task here returns it to the project backlog.
          </p>
        </>
      ) : (
        <p className="text-xs text-ink-muted rounded-lg border border-line bg-subtle/60 px-3.5 py-3">
          This sprint is read-only. Only an admin or the assigned project manager can change
          sprint scope while it is planned or active.
        </p>
      )}
    </div>
  );
}
