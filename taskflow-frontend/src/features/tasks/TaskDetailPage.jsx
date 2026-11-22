import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Calendar,
  Clock,
  Edit3,
  FolderKanban,
  GitBranch,
  History,
  IterationCw,
  Plus,
  RefreshCw,
  Trash2,
  UserCheck,
} from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { ConfirmDialog } from '../../components/common/ConfirmDialog';
import { Select } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { projectService } from '../../services/projectService';
import { taskService } from '../../services/taskService';
import { ROLES } from '../../utils/constants';
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
 * Full-page Task Detail view (/tasks/:id) backed by GET /api/v1/tasks/{id}.
 */
export function TaskDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user, hasRole } = useAuth();
  const { addToast } = useToast();

  const isAdmin = hasRole(ROLES.ADMIN);
  const isPmRole = hasRole(ROLES.PROJECT_MANAGER);

  const [task, setTask] = useState(null);
  const [project, setProject] = useState(null);
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const [transitionRemarks, setTransitionRemarks] = useState('');
  const [transitioningTo, setTransitioningTo] = useState(null);
  const [assigning, setAssigning] = useState(false);

  const [editOpen, setEditOpen] = useState(false);
  const [subtaskOpen, setSubtaskOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const loadTaskAndProject = useCallback(
    async ({ silent = false } = {}) => {
      if (!id) return;
      if (silent) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      try {
        const detail = await taskService.getTaskById(id);
        setTask(detail);

        const [projDetail, membersList] = await Promise.all([
          projectService.getProjectById(detail.projectId).catch(() => null),
          projectService.listProjectMembers(detail.projectId).catch(() => []),
        ]);
        setProject(projDetail);

        const map = new Map();
        if (projDetail?.manager?.id) map.set(projDetail.manager.id, projDetail.manager);
        if (Array.isArray(membersList)) {
          for (const item of membersList) {
            if (item?.user?.id) map.set(item.user.id, item.user);
          }
        }
        setMembers(Array.from(map.values()));
      } catch (err) {
        const parsed = parseApiError(err);
        setError({
          status: parsed.status,
          title:
            parsed.status === 403
              ? 'Access denied to this task'
              : parsed.status === 404
                ? 'Task not found'
                : 'Unable to load task details',
          message: parsed.message,
        });
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [id]
  );

  useEffect(() => {
    setTransitionRemarks('');
    loadTaskAndProject({ silent: false });
  }, [loadTaskAndProject]);

  const canManageProject = useMemo(() => {
    if (!project || !user) return false;
    if (isAdmin) return true;
    return isPmRole && project.manager?.id === user.id;
  }, [project, user, isAdmin, isPmRole]);

  const isArchived = project?.status === 'ARCHIVED';
  const isCompleted = project?.status === 'COMPLETED';

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
      navigate(`/projects/${task.projectId}?tab=tasks`, { replace: true });
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

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-9 w-2/3" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <Skeleton className="lg:col-span-2 h-80 rounded-xl" />
          <Skeleton className="h-80 rounded-xl" />
        </div>
      </div>
    );
  }

  if (error || !task) {
    return (
      <div className="space-y-5">
        <Link
          to="/tasks"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-ink-muted hover:text-ink transition-colors"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          Back to Tasks
        </Link>

        <ErrorState
          title={error?.title || 'Task unavailable'}
          description={error?.message || 'Unable to load this task.'}
          onRetry={error?.status === 403 || error?.status === 404 ? null : () => loadTaskAndProject()}
        />
      </div>
    );
  }

  const subtasks = Array.isArray(task.subtasks) ? task.subtasks : [];
  const canAddSubtask =
    canManageProject && !isArchived && !isCompleted && !task.parentTaskId;

  return (
    <div className="space-y-6">
      {/* Navigation & Header */}
      <div className="space-y-4 rounded-xl border border-line bg-surface p-5 shadow-card">
        <div className="flex flex-wrap items-center gap-3 text-xs">
          <Link
            to={`/projects/${task.projectId}?tab=tasks`}
            className="inline-flex items-center gap-1.5 font-medium text-ink-muted hover:text-ink transition-colors"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            <span>
              {task.projectKey} — {task.projectName}
            </span>
          </Link>
          <span className="text-ink-faint" aria-hidden="true">
            ·
          </span>
          <Link
            to={`/projects/${task.projectId}?tab=board`}
            className="inline-flex items-center gap-1 text-ink-muted hover:text-brand-600 transition-colors"
          >
            <FolderKanban className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Open Project Board</span>
          </Link>
        </div>

        <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4">
          <div className="min-w-0 space-y-2.5">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="cyan" mono>
                {task.taskKey}
              </Badge>
              <TaskStatusBadge status={task.status} />
              <TaskPriorityBadge priority={task.priority} />
              {task.overdue && (
                <Badge tone="red">
                  <AlertTriangle className="h-3 w-3" aria-hidden="true" />
                  Overdue
                </Badge>
              )}
              {task.parentTaskId && (
                <Link
                  to={`/tasks/${task.parentTaskId}`}
                  className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-mono bg-subtle text-ink-muted border border-line-strong hover:border-brand-500/50 hover:text-brand-600 transition-colors"
                >
                  <GitBranch className="h-3 w-3" aria-hidden="true" />
                  Parent: {task.parentTaskKey || `#${task.parentTaskId}`}
                </Link>
              )}
            </div>
            <h1 className="text-2xl font-semibold tracking-tight text-ink">{task.title}</h1>
          </div>

          <div className="flex flex-wrap items-center gap-2 shrink-0">
            <Button
              variant="secondary"
              size="sm"
              loading={refreshing}
              onClick={() => loadTaskAndProject({ silent: true })}
              leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
            >
              Refresh
            </Button>
            {editCaps.canEditAny && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setEditOpen(true)}
                leftIcon={<Edit3 className="h-3.5 w-3.5" />}
              >
                Edit
              </Button>
            )}
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
          </div>
        </div>
      </div>

      {/* Main Content Split */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          {/* Description */}
          <Card>
            <CardHeader title="Description" />
            <CardBody>
              {task.description?.trim() ? (
                <p className="text-sm text-ink leading-relaxed whitespace-pre-wrap">
                  {task.description}
                </p>
              ) : (
                <p className="text-sm text-ink-faint italic">No description provided.</p>
              )}
            </CardBody>
          </Card>

          {/* Workflow Transition Controls */}
          {permittedTransitions.length > 0 && (
            <Card>
              <CardHeader
                title="Workflow Transition"
                description="Available status transitions permitted for your role on this task."
              />
              <CardBody className="space-y-3">
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
              </CardBody>
            </Card>
          )}

          {/* Subtasks */}
          {!task.parentTaskId && (
            <Card>
              <CardHeader
                title={`Subtasks (${subtasks.length})`}
                description="Child tasks linked directly under this task (single-level nesting)."
                action={
                  canAddSubtask && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setSubtaskOpen(true)}
                      leftIcon={<Plus className="h-3.5 w-3.5" />}
                    >
                      Add Subtask
                    </Button>
                  )
                }
              />
              <CardBody>
                {subtasks.length === 0 ? (
                  <p className="text-xs text-ink-faint">No subtasks linked to this task yet.</p>
                ) : (
                  <ul className="divide-y divide-line rounded-lg border border-line bg-canvas overflow-hidden">
                    {subtasks.map((sub) => (
                      <li key={sub.id}>
                        <Link
                          to={`/tasks/${sub.id}`}
                          className="px-3.5 py-2.5 flex items-center justify-between gap-3 hover:bg-subtle transition-colors"
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
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>
          )}

          {/* Comments & Attachments metadata */}
          <Card>
            <CardHeader title="Collaboration" />
            <CardBody>
              <TaskComments
                commentCount={task.commentCount}
                attachmentCount={task.attachmentCount}
              />
            </CardBody>
          </Card>

          {/* Activity History */}
          <Card>
            <CardHeader
              title={`Activity History (${task.history?.length || 0})`}
              description="Audit trail of status changes, assignment updates, and field edits."
              action={<History className="h-4 w-4 text-ink-faint" aria-hidden="true" />}
            />
            <CardBody>
              <TaskHistory history={task.history} />
            </CardBody>
          </Card>
        </div>

        {/* Right Metadata Rail */}
        <div className="space-y-6">
          <Card>
            <CardHeader title="Details" />
            <CardBody className="space-y-4">
              <div className="space-y-1.5">
                <span className="text-[11px] font-medium text-ink-faint flex items-center gap-1">
                  <UserCheck className="h-3.5 w-3.5" aria-hidden="true" />
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
                  <TaskAssignee assignee={task.assignee} size="sm" />
                )}
              </div>

              <div className="space-y-1.5 pt-3 border-t border-line">
                <span className="text-[11px] font-medium text-ink-faint block">Reporter</span>
                <TaskAssignee assignee={task.reporter} size="sm" />
              </div>

              <div className="space-y-1 pt-3 border-t border-line">
                <span className="text-[11px] font-medium text-ink-faint flex items-center gap-1">
                  <IterationCw className="h-3.5 w-3.5" aria-hidden="true" />
                  Sprint
                </span>
                <p className="text-xs font-medium text-ink">
                  {task.sprintName || 'Backlog'}
                </p>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-3 border-t border-line">
                <div>
                  <span className="text-[11px] font-medium text-ink-faint flex items-center gap-1">
                    <Calendar className="h-3.5 w-3.5" aria-hidden="true" />
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
                    <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                    Estimate
                  </span>
                  <p className="text-xs font-mono tabular-nums text-ink mt-1">
                    {task.estimatedHours != null ? `${task.estimatedHours}h` : 'Not set'}
                  </p>
                </div>
              </div>

              <div className="space-y-1.5 pt-3 border-t border-line text-xs text-ink-muted">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-ink-faint">Created</span>
                  <span className="tabular-nums">{formatDateTime(task.createdAt)}</span>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-ink-faint">Updated</span>
                  <span className="tabular-nums">{formatDateTime(task.updatedAt)}</span>
                </div>
              </div>
            </CardBody>
          </Card>
        </div>
      </div>

      {/* Edit Modal */}
      <TaskFormModal
        isOpen={editOpen}
        onClose={() => setEditOpen(false)}
        mode="edit"
        project={project}
        task={task}
        canManageProject={canManageProject}
        onSaved={(updated) => setTask(updated)}
      />

      {/* Add Subtask Modal */}
      {canAddSubtask && (
        <TaskFormModal
          isOpen={subtaskOpen}
          onClose={() => setSubtaskOpen(false)}
          mode="create"
          project={project || { id: task.projectId, projectKey: task.projectKey, name: task.projectName }}
          lockedParentTask={task}
          canManageProject={canManageProject}
          onSaved={() => loadTaskAndProject({ silent: true })}
        />
      )}

      {/* Delete Confirmation */}
      <ConfirmDialog
        isOpen={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={handleConfirmDelete}
        title="Delete Task"
        message={`Permanently delete ${task.taskKey} — "${task.title}"? This removes the task, its history, and sprint links and cannot be undone.`}
        confirmLabel="Delete Task"
        variant="danger"
        loading={deleting}
      />
    </div>
  );
}
