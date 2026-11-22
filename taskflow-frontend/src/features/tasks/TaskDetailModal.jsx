import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Calendar,
  Clock,
  Edit3,
  ExternalLink,
  GitBranch,
  History,
  IterationCw,
  Plus,
  Trash2,
  UserCheck,
} from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { ConfirmDialog } from '../../components/common/ConfirmDialog';
import { Select } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { Modal } from '../../components/common/Modal';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { projectService } from '../../services/projectService';
import { taskService } from '../../services/taskService';
import { parseApiError } from '../../utils/errorHandler';
import { formatDate, formatDateTime } from '../../utils/formatters';
import {
  getPermittedTaskTransitions,
  getTaskEditCapabilities,
  TASK_STATUS_META,
} from './taskConstants';
import { TaskAssignee } from './components/TaskAssignee';
import { TaskPriorityBadge } from './components/TaskPriorityBadge';
import { TaskStatusBadge } from './components/TaskStatusBadge';
import { TaskComments } from './TaskComments';
import { TaskFormModal } from './TaskFormModal';
import { TaskHistory } from './TaskHistory';

/**
 * Interactive Task Detail Modal backed by GET /api/v1/tasks/{id}.
 * Supports permitted status transitions, quick assignee updates, subtasks, edit, and delete.
 */
export function TaskDetailModal({
  isOpen,
  onClose,
  taskId,
  project = null,
  canManageProject = false,
  onTaskChanged,
  onSelectTask,
}) {
  const { user } = useAuth();
  const { addToast } = useToast();

  const [task, setTask] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const [members, setMembers] = useState([]);
  const [transitionRemarks, setTransitionRemarks] = useState('');
  const [transitioningTo, setTransitioningTo] = useState(null);
  const [assigning, setAssigning] = useState(false);

  const [editOpen, setEditOpen] = useState(false);
  const [subtaskOpen, setSubtaskOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const isArchived = project?.status === 'ARCHIVED';
  const isCompleted = project?.status === 'COMPLETED';

  const loadTask = useCallback(async () => {
    if (!taskId) return;
    setLoading(true);
    setError(null);
    try {
      const detail = await taskService.getTaskById(taskId);
      setTask(detail);
    } catch (err) {
      const parsed = parseApiError(err);
      setError({ title: 'Unable to load task details', message: parsed.message });
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    if (isOpen && taskId) {
      setTransitionRemarks('');
      loadTask();
    } else if (!isOpen) {
      setTask(null);
      setError(null);
    }
  }, [isOpen, taskId, loadTask]);

  useEffect(() => {
    const projId = task?.projectId || project?.id;
    if (!isOpen || !projId || !canManageProject) return undefined;

    let cancelled = false;
    projectService
      .listProjectMembers(projId)
      .then((list) => {
        if (cancelled) return;
        const map = new Map();
        if (project?.manager?.id) map.set(project.manager.id, project.manager);
        if (Array.isArray(list)) {
          for (const item of list) {
            if (item?.user?.id) map.set(item.user.id, item.user);
          }
        }
        setMembers(Array.from(map.values()));
      })
      .catch(() => {
        if (!cancelled) setMembers([]);
      });

    return () => {
      cancelled = true;
    };
  }, [isOpen, task?.projectId, project, canManageProject]);

  const permittedTransitions = useMemo(
    () => getPermittedTaskTransitions(task, user, canManageProject, isArchived),
    [task, user, canManageProject, isArchived]
  );

  const editCaps = useMemo(
    () => getTaskEditCapabilities(task, user, canManageProject, isArchived),
    [task, user, canManageProject, isArchived]
  );

  const assigneeOptions = useMemo(() => {
    const opts = [{ value: '', label: 'Unassigned' }];
    for (const member of members) {
      opts.push({ value: String(member.id), label: member.fullName });
    }
    if (task?.assignee?.id && !members.some((m) => m.id === task.assignee.id)) {
      opts.push({ value: String(task.assignee.id), label: task.assignee.fullName });
    }
    return opts;
  }, [members, task?.assignee]);

  const handleStatusTransition = async (targetStatus) => {
    if (!task || transitioningTo) return;
    setTransitioningTo(targetStatus);
    try {
      const updated = await taskService.updateTaskStatus(task.id, {
        status: targetStatus,
        remarks: transitionRemarks,
      });
      setTask(updated);
      setTransitionRemarks('');
      addToast({
        type: 'success',
        title: 'Status updated',
        message: `${updated.taskKey} moved to ${TASK_STATUS_META[targetStatus]?.label || targetStatus}.`,
      });
      onTaskChanged?.(updated);
    } catch (err) {
      addToast({
        type: 'error',
        title: 'Transition blocked',
        message: parseApiError(err).message,
      });
    } finally {
      setTransitioningTo(null);
    }
  };

  const handleQuickAssign = async (nextAssigneeId) => {
    if (!task || assigning) return;
    const currentId = task.assignee?.id ? String(task.assignee.id) : '';
    if (nextAssigneeId === currentId) return;

    setAssigning(true);
    try {
      const updated = await taskService.assignTask(task.id, {
        assigneeId: nextAssigneeId ? Number(nextAssigneeId) : null,
      });
      setTask(updated);
      addToast({
        type: 'success',
        title: 'Assignee updated',
        message: updated.assignee
          ? `${updated.taskKey} assigned to ${updated.assignee.fullName}.`
          : `${updated.taskKey} is now unassigned.`,
      });
      onTaskChanged?.(updated);
    } catch (err) {
      addToast({
        type: 'error',
        title: 'Assignment failed',
        message: parseApiError(err).message,
      });
    } finally {
      setAssigning(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!task || deleting) return;
    setDeleting(true);
    try {
      await taskService.deleteTask(task.id);
      addToast({
        type: 'success',
        title: 'Task deleted',
        message: `${task.taskKey} has been permanently deleted.`,
      });
      setDeleteOpen(false);
      onClose?.();
      onTaskChanged?.({ deletedId: task.id });
    } catch (err) {
      addToast({
        type: 'error',
        title: 'Unable to delete task',
        message: parseApiError(err).message,
      });
      setDeleteOpen(false);
    } finally {
      setDeleting(false);
    }
  };

  const subtasks = Array.isArray(task?.subtasks) ? task.subtasks : [];
  const canAddSubtask =
    canManageProject && !isArchived && !isCompleted && task && !task.parentTaskId;

  return (
    <>
      <Modal
        isOpen={isOpen}
        onClose={onClose}
        title={task ? `${task.taskKey} · ${task.title}` : 'Task Details'}
        description={task ? `${task.projectKey} — ${task.projectName}` : undefined}
        size="xl"
        footer={
          task ? (
            <div className="w-full flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Link
                  to={`/tasks/${task.id}`}
                  onClick={onClose}
                  className="inline-flex items-center gap-1.5 text-xs font-medium text-brand-600 hover:text-brand-700 transition-colors"
                >
                  <span>Open Full Page</span>
                  <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
                </Link>
              </div>

              <div className="flex items-center gap-2">
                {canManageProject && !isArchived && (
                  <Button
                    variant="danger"
                    size="sm"
                    disabled={subtasks.length > 0}
                    title={
                      subtasks.length > 0
                        ? 'Delete all subtasks before deleting this parent task.'
                        : 'Delete task'
                    }
                    onClick={() => setDeleteOpen(true)}
                    leftIcon={<Trash2 className="h-3.5 w-3.5" />}
                  >
                    Delete
                  </Button>
                )}
                {editCaps.canEditAny && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => setEditOpen(true)}
                    leftIcon={<Edit3 className="h-3.5 w-3.5" />}
                  >
                    Edit Task
                  </Button>
                )}
                <Button variant="ghost" size="sm" onClick={onClose}>
                  Close
                </Button>
              </div>
            </div>
          ) : (
            <Button variant="ghost" size="sm" onClick={onClose}>
              Close
            </Button>
          )
        }
      >
        {loading ? (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <Skeleton className="h-6 w-20" />
              <Skeleton className="h-6 w-24" />
            </div>
            <Skeleton className="h-24 w-full" />
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Skeleton className="h-32 w-full" />
              <Skeleton className="h-32 w-full" />
            </div>
          </div>
        ) : error ? (
          <ErrorState
            title={error.title}
            description={error.message}
            onRetry={loadTask}
          />
        ) : task ? (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Left 2 Columns: Description, Workflow Actions, Subtasks, Comments & History */}
            <div className="lg:col-span-2 space-y-5">
              {/* Status & Priority Header Row */}
              <div className="flex flex-wrap items-center gap-2">
                <TaskStatusBadge status={task.status} />
                <TaskPriorityBadge priority={task.priority} />
                {task.overdue && (
                  <Badge tone="red">
                    <AlertTriangle className="h-3 w-3" aria-hidden="true" />
                    Overdue
                  </Badge>
                )}
                {task.parentTaskId && (
                  <button
                    type="button"
                    onClick={() => onSelectTask?.(task.parentTaskId)}
                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-mono bg-subtle text-ink-muted border border-line-strong hover:border-brand-500/50 hover:text-brand-600 transition-colors"
                  >
                    <GitBranch className="h-3 w-3" aria-hidden="true" />
                    Parent: {task.parentTaskKey || `#${task.parentTaskId}`}
                  </button>
                )}
              </div>

              {/* Description */}
              <div className="space-y-1.5">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
                  Description
                </h3>
                <div className="rounded-lg bg-canvas border border-line p-3.5">
                  {task.description?.trim() ? (
                    <p className="text-sm text-ink leading-relaxed whitespace-pre-wrap">
                      {task.description}
                    </p>
                  ) : (
                    <p className="text-xs text-ink-faint italic">No description provided.</p>
                  )}
                </div>
              </div>

              {/* Workflow Transitions */}
              {permittedTransitions.length > 0 && (
                <div className="rounded-lg bg-canvas border border-line p-4 space-y-3">
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
                    Workflow Transition
                  </h3>
                  <Input
                    value={transitionRemarks}
                    onChange={(e) => setTransitionRemarks(e.target.value)}
                    placeholder="Optional transition remarks (recorded in task history)..."
                    maxLength={500}
                    disabled={Boolean(transitioningTo)}
                  />
                  <div className="flex flex-wrap items-center gap-2">
                    {permittedTransitions.map((nextStatus) => {
                      const meta = TASK_STATUS_META[nextStatus];
                      return (
                        <Button
                          key={nextStatus}
                          variant={nextStatus === 'DONE' ? 'primary' : 'secondary'}
                          size="sm"
                          loading={transitioningTo === nextStatus}
                          disabled={Boolean(transitioningTo)}
                          onClick={() => handleStatusTransition(nextStatus)}
                          leftIcon={<ArrowRight className="h-3.5 w-3.5" />}
                        >
                          Move to {meta?.label || nextStatus}
                        </Button>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Subtasks */}
              {!task.parentTaskId && (
                <div className="space-y-2.5">
                  <div className="flex items-center justify-between gap-2">
                    <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted flex items-center gap-1.5">
                      <GitBranch className="h-3.5 w-3.5" aria-hidden="true" />
                      <span>Subtasks ({subtasks.length})</span>
                    </h3>
                    {canAddSubtask && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setSubtaskOpen(true)}
                        leftIcon={<Plus className="h-3.5 w-3.5" />}
                      >
                        Add Subtask
                      </Button>
                    )}
                  </div>

                  {subtasks.length === 0 ? (
                    <p className="text-xs text-ink-faint rounded-lg border border-dashed border-line px-3.5 py-3">
                      No subtasks linked to this task.
                    </p>
                  ) : (
                    <ul className="divide-y divide-line rounded-lg border border-line bg-canvas overflow-hidden">
                      {subtasks.map((sub) => (
                        <li key={sub.id}>
                          <button
                            type="button"
                            onClick={() => onSelectTask?.(sub.id)}
                            className="w-full px-3.5 py-2.5 text-left flex items-center justify-between gap-3 hover:bg-subtle transition-colors"
                          >
                            <div className="min-w-0 flex items-center gap-2.5">
                              <span className="text-xs font-mono text-brand-600 shrink-0">
                                {sub.taskKey}
                              </span>
                              <span className="text-xs font-medium text-ink truncate">
                                {sub.title}
                              </span>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                              <TaskStatusBadge status={sub.status} />
                              <TaskAssignee assignee={sub.assignee} compact />
                            </div>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}

              {/* Comments & Attachments metadata */}
              <div className="space-y-2">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
                  Collaboration
                </h3>
                <TaskComments
                  commentCount={task.commentCount}
                  attachmentCount={task.attachmentCount}
                />
              </div>

              {/* Activity History */}
              <div className="space-y-2.5">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted flex items-center gap-1.5">
                  <History className="h-3.5 w-3.5" aria-hidden="true" />
                  <span>Activity History ({task.history?.length || 0})</span>
                </h3>
                <TaskHistory history={task.history} />
              </div>
            </div>

            {/* Right Metadata Column */}
            <div className="space-y-4">
              <div className="rounded-lg bg-canvas border border-line p-4 space-y-4">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
                  Task Metadata
                </h3>

                {/* Assignee */}
                <div className="space-y-1.5">
                  <span className="text-[11px] font-medium text-ink-faint flex items-center gap-1">
                    <UserCheck className="h-3 w-3" aria-hidden="true" />
                    Assignee
                  </span>
                  {canManageProject && !isArchived ? (
                    <Select
                      aria-label="Reassign task"
                      value={task.assignee?.id ? String(task.assignee.id) : ''}
                      onChange={(e) => handleQuickAssign(e.target.value)}
                      options={assigneeOptions}
                      disabled={assigning}
                    />
                  ) : (
                    <div className="pt-0.5">
                      <TaskAssignee assignee={task.assignee} size="sm" />
                    </div>
                  )}
                </div>

                {/* Reporter */}
                <div className="space-y-1.5 pt-3 border-t border-line">
                  <span className="text-[11px] font-medium text-ink-faint block">Reporter</span>
                  <TaskAssignee assignee={task.reporter} size="sm" />
                </div>

                {/* Sprint */}
                <div className="space-y-1 pt-3 border-t border-line">
                  <span className="text-[11px] font-medium text-ink-faint flex items-center gap-1">
                    <IterationCw className="h-3 w-3" aria-hidden="true" />
                    Sprint
                  </span>
                  <p className="text-xs font-medium text-ink">
                    {task.sprintName || 'Backlog'}
                  </p>
                </div>

                {/* Due Date & Estimate */}
                <div className="grid grid-cols-2 gap-3 pt-3 border-t border-line">
                  <div>
                    <span className="text-[11px] font-medium text-ink-faint flex items-center gap-1">
                      <Calendar className="h-3 w-3" aria-hidden="true" />
                      Due Date
                    </span>
                    <p
                      className={`text-xs font-medium mt-1 tabular-nums ${
                        task.overdue ? 'text-red-600' : 'text-ink'
                      }`}
                    >
                      {formatDate(task.dueDate)}
                    </p>
                  </div>
                  <div>
                    <span className="text-[11px] font-medium text-ink-faint flex items-center gap-1">
                      <Clock className="h-3 w-3" aria-hidden="true" />
                      Estimate
                    </span>
                    <p className="text-xs font-mono tabular-nums text-ink mt-1">
                      {task.estimatedHours != null ? `${task.estimatedHours}h` : 'Not set'}
                    </p>
                  </div>
                </div>

                {/* Audit Timestamps */}
                <div className="space-y-1.5 pt-3 border-t border-line text-[11px] text-ink-muted">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-ink-faint">Created</span>
                    <span className="tabular-nums">{formatDateTime(task.createdAt)}</span>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-ink-faint">Updated</span>
                    <span className="tabular-nums">{formatDateTime(task.updatedAt)}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        ) : null}
      </Modal>

      {/* Edit Task Modal */}
      {task && (
        <TaskFormModal
          isOpen={editOpen}
          onClose={() => setEditOpen(false)}
          mode="edit"
          project={project}
          task={task}
          canManageProject={canManageProject}
          onSaved={(updated) => {
            setTask(updated);
            onTaskChanged?.(updated);
          }}
        />
      )}

      {/* Add Subtask Modal */}
      {task && canAddSubtask && (
        <TaskFormModal
          isOpen={subtaskOpen}
          onClose={() => setSubtaskOpen(false)}
          mode="create"
          project={project || { id: task.projectId, projectKey: task.projectKey, name: task.projectName }}
          lockedParentTask={task}
          canManageProject={canManageProject}
          onSaved={() => {
            loadTask();
            onTaskChanged?.();
          }}
        />
      )}

      {/* Delete Confirmation */}
      <ConfirmDialog
        isOpen={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={handleConfirmDelete}
        title="Delete Task"
        message={
          task
            ? `Permanently delete ${task.taskKey} — "${task.title}"? This removes the task, its history, and sprint links and cannot be undone.`
            : ''
        }
        confirmLabel="Delete Task"
        variant="danger"
        loading={deleting}
      />
    </>
  );
}
