import { useEffect, useMemo, useState } from 'react';
import { CheckSquare, Save } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Select, Textarea } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { projectService } from '../../services/projectService';
import { taskService } from '../../services/taskService';
import { parseApiError } from '../../utils/errorHandler';
import {
  ALL_PRIORITIES,
  getTaskEditCapabilities,
  PRIORITY_LEVEL,
  PRIORITY_META,
} from './taskConstants';

const PRIORITY_OPTIONS = ALL_PRIORITIES.map((level) => ({
  value: level,
  label: PRIORITY_META[level]?.label || level,
}));

const OPEN_SPRINT_STATUSES = new Set(['PLANNED', 'ACTIVE']);

function buildInitialForm({ task, projectId, lockedParentTask }) {
  if (task) {
    return {
      projectId: String(task.projectId || projectId || ''),
      title: task.title || '',
      description: task.description || '',
      priority: task.priority || PRIORITY_LEVEL.MEDIUM,
      assigneeId: task.assignee?.id ? String(task.assignee.id) : '',
      sprintId: task.sprintId ? String(task.sprintId) : '',
      parentTaskId: task.parentTaskId ? String(task.parentTaskId) : '',
      estimatedHours:
        task.estimatedHours !== null && task.estimatedHours !== undefined
          ? String(task.estimatedHours)
          : '',
      dueDate: task.dueDate || '',
      remarks: '',
    };
  }

  return {
    projectId: projectId ? String(projectId) : '',
    title: '',
    description: '',
    priority: PRIORITY_LEVEL.MEDIUM,
    assigneeId: '',
    sprintId: '',
    parentTaskId: lockedParentTask?.id ? String(lockedParentTask.id) : '',
    estimatedHours: '',
    dueDate: '',
    remarks: '',
  };
}

/**
 * Create & Edit Task Modal mapped strictly to:
 * - POST /api/v1/tasks            (CreateTaskRequest)
 * - PUT  /api/v1/tasks/{id}       (UpdateTaskRequest)
 * - PATCH /api/v1/tasks/{id}/assignee (used when manager unassigns an existing assignee during edit)
 */
export function TaskFormModal({
  isOpen,
  onClose,
  mode = 'create',
  project = null,
  availableProjects = [],
  task = null,
  lockedParentTask = null,
  canManageProject = true,
  onSaved,
}) {
  const { user } = useAuth();
  const { addToast } = useToast();
  const isEdit = mode === 'edit' && Boolean(task);

  const editCaps = useMemo(() => {
    if (!isEdit) {
      return {
        canEditAny: true,
        isFullEdit: true,
        canEditDescription: true,
        canEditEstimatedHours: true,
      };
    }
    return getTaskEditCapabilities(task, user, canManageProject, project?.status === 'ARCHIVED');
  }, [isEdit, task, user, canManageProject, project?.status]);

  const [form, setForm] = useState(() =>
    buildInitialForm({ task, projectId: project?.id, lockedParentTask })
  );
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [projectDetail, setProjectDetail] = useState(project);
  const [members, setMembers] = useState([]);
  const [sprints, setSprints] = useState([]);
  const [parentCandidates, setParentCandidates] = useState([]);
  const [loadingContext, setLoadingContext] = useState(false);

  // Reset form when modal opens
  useEffect(() => {
    if (!isOpen) return;
    const defaultProjId =
      task?.projectId ||
      project?.id ||
      (availableProjects.length > 0 ? availableProjects[0].id : '');
    setForm(buildInitialForm({ task, projectId: defaultProjId, lockedParentTask }));
    setErrors({});
    setFormError('');
  // `availableProjects` is intentionally excluded: callers pass a fresh array on
  // each render, which would otherwise re-trigger this reset effect forever.
  // It is only read when the modal opens to pick a default project.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, task, project?.id, lockedParentTask]);

  const activeProjectId = form.projectId ? Number(form.projectId) : null;

  // Load project members, sprints, and top-level tasks for parent selection
  useEffect(() => {
    if (!isOpen || !activeProjectId) return undefined;

    let cancelled = false;
    setLoadingContext(true);

    const needsProjectFetch = !project || Number(project.id) !== activeProjectId;

    Promise.all([
      needsProjectFetch
        ? projectService.getProjectById(activeProjectId).catch(() => null)
        : Promise.resolve(project),
      projectService.listProjectMembers(activeProjectId).catch(() => []),
      taskService.listProjectSprints(activeProjectId).catch(() => []),
      taskService
        .listTasks({ projectId: activeProjectId, page: 0, size: 100, sortBy: 'createdAt', sortDir: 'desc' })
        .catch(() => ({ content: [] })),
    ])
      .then(([projData, membersList, sprintsList, tasksPage]) => {
        if (cancelled) return;
        setProjectDetail(projData || project || null);

        // Combine project manager + members cleanly without duplicates
        const memberMap = new Map();
        if (projData?.manager?.id) {
          memberMap.set(projData.manager.id, projData.manager);
        }
        if (Array.isArray(membersList)) {
          for (const item of membersList) {
            if (item?.user?.id) {
              memberMap.set(item.user.id, item.user);
            }
          }
        }
        setMembers(Array.from(memberMap.values()));

        const openSprints = Array.isArray(sprintsList)
          ? sprintsList.filter((s) => OPEN_SPRINT_STATUSES.has(s.status))
          : [];
        setSprints(openSprints);

        // Only top-level tasks (parentTaskId == null) in same project can be parent tasks
        const topLevelTasks = Array.isArray(tasksPage?.content)
          ? tasksPage.content.filter(
              (candidate) =>
                candidate.parentTaskId == null &&
                (!task || Number(candidate.id) !== Number(task.id))
            )
          : [];
        setParentCandidates(topLevelTasks);
      })
      .finally(() => {
        if (!cancelled) setLoadingContext(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isOpen, activeProjectId, project, task]);

  const projectOptions = useMemo(() => {
    return availableProjects.map((proj) => ({
      value: String(proj.id),
      label: `${proj.projectKey} — ${proj.name}`,
    }));
  }, [availableProjects]);

  const assigneeOptions = useMemo(() => {
    const opts = [{ value: '', label: 'Unassigned' }];
    for (const member of members) {
      opts.push({
        value: String(member.id),
        label: `${member.fullName}${member.email ? ` (${member.email})` : ''}`,
      });
    }
    if (task?.assignee?.id && !members.some((m) => m.id === task.assignee.id)) {
      opts.push({
        value: String(task.assignee.id),
        label: `${task.assignee.fullName} (Current)`,
      });
    }
    return opts;
  }, [members, task?.assignee]);

  const sprintOptions = useMemo(() => {
    const opts = [{ value: '', label: 'Backlog (No sprint)' }];
    for (const sprint of sprints) {
      opts.push({
        value: String(sprint.id),
        label: `${sprint.name} (${sprint.status})`,
      });
    }
    if (task?.sprintId && !sprints.some((s) => Number(s.id) === Number(task.sprintId))) {
      opts.push({
        value: String(task.sprintId),
        label: `${task.sprintName || `Sprint #${task.sprintId}`} (Current)`,
      });
    }
    return opts;
  }, [sprints, task?.sprintId, task?.sprintName]);

  const parentTaskOptions = useMemo(() => {
    if (lockedParentTask) {
      return [
        {
          value: String(lockedParentTask.id),
          label: `${lockedParentTask.taskKey} — ${lockedParentTask.title}`,
        },
      ];
    }
    const opts = [{ value: '', label: 'None (Top-level task)' }];
    for (const parent of parentCandidates) {
      opts.push({
        value: String(parent.id),
        label: `${parent.taskKey} — ${parent.title}`,
      });
    }
    if (
      task?.parentTaskId &&
      !parentCandidates.some((p) => Number(p.id) === Number(task.parentTaskId))
    ) {
      opts.push({
        value: String(task.parentTaskId),
        label: task.parentTaskKey || `Task #${task.parentTaskId}`,
      });
    }
    return opts;
  }, [parentCandidates, lockedParentTask, task?.parentTaskId, task?.parentTaskKey]);

  const updateField = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
    if (formError) setFormError('');
  };

  const validate = () => {
    const nextErrors = {};

    if (!isEdit && !form.projectId) {
      nextErrors.projectId = 'Select a target project.';
    }

    if (editCaps.isFullEdit) {
      const trimmedTitle = form.title.trim();
      if (!trimmedTitle) {
        nextErrors.title = 'Task title is required.';
      } else if (trimmedTitle.length < 3 || trimmedTitle.length > 200) {
        nextErrors.title = 'Task title must be between 3 and 200 characters.';
      }

      if (form.dueDate && projectDetail) {
        if (projectDetail.startDate && form.dueDate < projectDetail.startDate) {
          nextErrors.dueDate = `Due date cannot be earlier than project start (${projectDetail.startDate}).`;
        } else if (projectDetail.endDate && form.dueDate > projectDetail.endDate) {
          nextErrors.dueDate = `Due date cannot be later than project end (${projectDetail.endDate}).`;
        }
      }
    }

    if (form.description && form.description.length > 8000) {
      nextErrors.description = 'Description must not exceed 8000 characters.';
    }

    if (editCaps.canEditEstimatedHours && form.estimatedHours !== '') {
      const parsedHours = Number(form.estimatedHours);
      if (Number.isNaN(parsedHours) || parsedHours < 0.25 || parsedHours > 9999.99) {
        nextErrors.estimatedHours = 'Estimated hours must be between 0.25 and 9999.99.';
      }
    }

    if (isEdit && form.remarks && form.remarks.length > 500) {
      nextErrors.remarks = 'Remarks must not exceed 500 characters.';
    }

    return nextErrors;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;

    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setSubmitting(true);
    setFormError('');

    try {
      if (!isEdit) {
        const created = await taskService.createTask({
          projectId: Number(form.projectId),
          title: form.title,
          description: form.description,
          priority: form.priority,
          assigneeId: form.assigneeId ? Number(form.assigneeId) : null,
          sprintId: form.sprintId ? Number(form.sprintId) : null,
          parentTaskId: form.parentTaskId ? Number(form.parentTaskId) : null,
          estimatedHours: form.estimatedHours !== '' ? Number(form.estimatedHours) : null,
          dueDate: form.dueDate || null,
        });
        addToast({
          type: 'success',
          title: lockedParentTask ? 'Subtask created' : 'Task created',
          message: `${created.taskKey} — ${created.title}`,
        });
        onClose?.();
        onSaved?.(created);
      } else {
        let updated = null;
        const payload = {
          expectedVersion: task.version,
          remarks: form.remarks,
        };

        if (editCaps.canEditDescription) {
          payload.description = form.description;
        }
        if (editCaps.canEditEstimatedHours && form.estimatedHours !== '') {
          payload.estimatedHours = Number(form.estimatedHours);
        }

        let shouldUnassign = false;
        if (editCaps.isFullEdit) {
          payload.title = form.title;
          payload.priority = form.priority;
          if (form.dueDate) {
            payload.dueDate = form.dueDate;
          }
          if (form.sprintId) {
            payload.sprintId = Number(form.sprintId);
          }
          if (form.parentTaskId) {
            payload.parentTaskId = Number(form.parentTaskId);
          }
          if (form.assigneeId) {
            payload.assigneeId = Number(form.assigneeId);
          } else if (task.assignee?.id) {
            // Backend PUT ignores null assigneeId; PATCH /assignee handles unassignment
            shouldUnassign = true;
          }
        }

        updated = await taskService.updateTask(task.id, payload);

        if (shouldUnassign) {
          updated = await taskService.assignTask(task.id, {
            assigneeId: null,
            remarks: form.remarks,
          });
        }

        addToast({
          type: 'success',
          title: 'Task updated',
          message: `${updated.taskKey} changes saved.`,
        });
        onClose?.();
        onSaved?.(updated);
      }
    } catch (err) {
      const parsed = parseApiError(err);
      if (parsed.fieldErrors && Object.keys(parsed.fieldErrors).length > 0) {
        setErrors((prev) => ({ ...prev, ...parsed.fieldErrors }));
      }
      setFormError(
        parsed.status === 409
          ? 'This task was modified by another user. Close and refresh before saving again.'
          : parsed.message
      );
    } finally {
      setSubmitting(false);
    }
  };

  const modalTitle = isEdit
    ? `Edit ${task.taskKey}`
    : lockedParentTask
      ? `Add Subtask to ${lockedParentTask.taskKey}`
      : 'Create Task';

  const modalDescription = isEdit
    ? editCaps.isFullEdit
      ? 'Update task specification, priority, schedule, or assignment.'
      : editCaps.canEditEstimatedHours
        ? 'As the assignee, you can update the description, estimated hours, and workflow remarks.'
        : 'As the assigned QA tester, you can update the description and workflow remarks.'
    : 'Define a new deliverable inside the project workspace. New tasks begin in To Do status.';

  const hasSubtasks = isEdit && Array.isArray(task?.subtasks) && task.subtasks.length > 0;

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? undefined : onClose}
      closeOnBackdrop={!submitting}
      title={modalTitle}
      description={modalDescription}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="task-form"
            loading={submitting}
            leftIcon={isEdit ? <Save className="h-4 w-4" /> : <CheckSquare className="h-4 w-4" />}
          >
            {isEdit ? 'Save Changes' : lockedParentTask ? 'Create Subtask' : 'Create Task'}
          </Button>
        </>
      }
    >
      <form id="task-form" onSubmit={handleSubmit} className="space-y-4" noValidate aria-busy={submitting}>
        {formError && (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-600"
          >
            {formError}
          </div>
        )}

        {!isEdit && !project && projectOptions.length > 0 && (
          <Select
            label="Project"
            required
            value={form.projectId}
            onChange={(e) => {
              updateField('projectId', e.target.value);
              updateField('assigneeId', '');
              updateField('sprintId', '');
              updateField('parentTaskId', '');
            }}
            options={projectOptions}
            error={errors.projectId}
            disabled={submitting}
          />
        )}

        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-faint">Core details</p>
        <Input
          label="Title"
          required={editCaps.isFullEdit}
          value={form.title}
          onChange={(e) => updateField('title', e.target.value)}
          placeholder="Concise summary of the deliverable..."
          maxLength={200}
          error={errors.title}
          disabled={submitting || !editCaps.isFullEdit}
        />

        <Textarea
          label="Description"
          value={form.description}
          onChange={(e) => updateField('description', e.target.value)}
          placeholder="Acceptance criteria, technical context, and implementation notes..."
          rows={4}
          maxLength={8000}
          error={errors.description}
          disabled={submitting || !editCaps.canEditDescription}
        />

        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-faint">Planning</p>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Select
            label="Priority"
            value={form.priority}
            onChange={(e) => updateField('priority', e.target.value)}
            options={PRIORITY_OPTIONS}
            error={errors.priority}
            disabled={submitting || !editCaps.isFullEdit}
          />

          <Input
            label="Due Date"
            type="date"
            value={form.dueDate}
            min={projectDetail?.startDate || undefined}
            max={projectDetail?.endDate || undefined}
            onChange={(e) => updateField('dueDate', e.target.value)}
            error={errors.dueDate}
            helperText={
              projectDetail?.startDate
                ? `Project window: ${projectDetail.startDate}${projectDetail.endDate ? ` to ${projectDetail.endDate}` : '+'}`
                : 'Optional target date'
            }
            disabled={submitting || !editCaps.isFullEdit}
          />

          <Input
            label="Estimated Hours"
            type="number"
            step="0.25"
            min="0.25"
            max="9999.99"
            value={form.estimatedHours}
            onChange={(e) => updateField('estimatedHours', e.target.value)}
            placeholder="e.g., 4.5"
            error={errors.estimatedHours}
            helperText="0.25 – 9999.99 hrs"
            disabled={submitting || !editCaps.canEditEstimatedHours}
          />
        </div>

        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-faint">Assignment &amp; schedule</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1 border-t border-line">
          <Select
            label="Assignee"
            value={form.assigneeId}
            onChange={(e) => updateField('assigneeId', e.target.value)}
            options={assigneeOptions}
            error={errors.assigneeId}
            helperText="Active project members only."
            disabled={submitting || !editCaps.isFullEdit || loadingContext}
          />

          <Select
            label="Sprint (Optional)"
            value={form.sprintId}
            onChange={(e) => updateField('sprintId', e.target.value)}
            options={sprintOptions}
            error={errors.sprintId}
            helperText="Only Planned or Active project sprints."
            disabled={submitting || !editCaps.isFullEdit || loadingContext}
          />
        </div>

        {!hasSubtasks && (
          <Select
            label="Parent Task (Optional)"
            value={form.parentTaskId}
            onChange={(e) => updateField('parentTaskId', e.target.value)}
            options={parentTaskOptions}
            error={errors.parentTaskId}
            helperText={
              lockedParentTask
                ? `Creating subtask directly under ${lockedParentTask.taskKey}.`
                : 'Only top-level project tasks can be selected (max 1 subtask level).'
            }
            disabled={
              submitting || !editCaps.isFullEdit || Boolean(lockedParentTask) || loadingContext
            }
          />
        )}

        {isEdit && (
          <Input
            label="Change Remarks (Optional)"
            value={form.remarks}
            onChange={(e) => updateField('remarks', e.target.value)}
            placeholder="Briefly note why this update was made (saved in Activity History)..."
            maxLength={500}
            error={errors.remarks}
            disabled={submitting}
          />
        )}
      </form>
    </Modal>
  );
}
