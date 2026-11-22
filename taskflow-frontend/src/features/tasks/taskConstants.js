import { ROLES } from '../../utils/constants';

/**
 * Exact backend TaskStatus enum values (com.taskflow.enums.TaskStatus).
 */
export const TASK_STATUS = Object.freeze({
  TODO: 'TODO',
  IN_PROGRESS: 'IN_PROGRESS',
  IN_REVIEW: 'IN_REVIEW',
  BLOCKED: 'BLOCKED',
  DONE: 'DONE',
});

export const KANBAN_COLUMN_ORDER = Object.freeze([
  TASK_STATUS.TODO,
  TASK_STATUS.IN_PROGRESS,
  TASK_STATUS.IN_REVIEW,
  TASK_STATUS.BLOCKED,
  TASK_STATUS.DONE,
]);

export const TASK_STATUS_META = Object.freeze({
  [TASK_STATUS.TODO]: {
    label: 'To Do',
    shortLabel: 'To Do',
    tone: 'neutral',
    accentDot: 'bg-ink-faint',
    description: 'Queued work not yet started.',
  },
  [TASK_STATUS.IN_PROGRESS]: {
    label: 'In Progress',
    shortLabel: 'In Progress',
    tone: 'cyan',
    accentDot: 'bg-brand-500',
    description: 'Actively being implemented.',
  },
  [TASK_STATUS.IN_REVIEW]: {
    label: 'In Review',
    shortLabel: 'In Review',
    tone: 'steel',
    accentDot: 'bg-sky-500',
    description: 'Submitted for QA or Project Manager verification.',
  },
  [TASK_STATUS.BLOCKED]: {
    label: 'Blocked',
    shortLabel: 'Blocked',
    tone: 'red',
    accentDot: 'bg-red-400',
    description: 'Impeded pending dependency or clarification.',
  },
  [TASK_STATUS.DONE]: {
    label: 'Done',
    shortLabel: 'Done',
    tone: 'emerald',
    accentDot: 'bg-emerald-400',
    description: 'Verified and completed.',
  },
});

/**
 * Exact backend PriorityLevel enum values (com.taskflow.enums.PriorityLevel).
 */
export const PRIORITY_LEVEL = Object.freeze({
  LOW: 'LOW',
  MEDIUM: 'MEDIUM',
  HIGH: 'HIGH',
  CRITICAL: 'CRITICAL',
});

export const ALL_PRIORITIES = Object.freeze([
  PRIORITY_LEVEL.CRITICAL,
  PRIORITY_LEVEL.HIGH,
  PRIORITY_LEVEL.MEDIUM,
  PRIORITY_LEVEL.LOW,
]);

export const PRIORITY_META = Object.freeze({
  [PRIORITY_LEVEL.LOW]: {
    label: 'Low',
    tone: 'neutral',
  },
  [PRIORITY_LEVEL.MEDIUM]: {
    label: 'Medium',
    tone: 'cyan',
  },
  [PRIORITY_LEVEL.HIGH]: {
    label: 'High',
    tone: 'amber',
  },
  [PRIORITY_LEVEL.CRITICAL]: {
    label: 'Critical',
    tone: 'red',
  },
});

/**
 * Exact backend workflow transitions from TaskServiceImpl.buildStatusTransitions().
 */
export const ALLOWED_TASK_TRANSITIONS = Object.freeze({
  [TASK_STATUS.TODO]: [TASK_STATUS.IN_PROGRESS, TASK_STATUS.BLOCKED],
  [TASK_STATUS.IN_PROGRESS]: [TASK_STATUS.IN_REVIEW, TASK_STATUS.BLOCKED, TASK_STATUS.TODO],
  [TASK_STATUS.IN_REVIEW]: [TASK_STATUS.DONE, TASK_STATUS.IN_PROGRESS, TASK_STATUS.BLOCKED],
  [TASK_STATUS.BLOCKED]: [TASK_STATUS.TODO, TASK_STATUS.IN_PROGRESS],
  [TASK_STATUS.DONE]: [TASK_STATUS.IN_PROGRESS],
});

/**
 * Exact client-side mirror of TaskServiceImpl.enforceStatusTransitionPermissions()
 * combined with TaskServiceImpl.buildStatusTransitions().
 * Backend remains the final authority.
 */
export function getPermittedTaskTransitions(
  task,
  currentUser,
  canManageProject = false,
  isProjectArchived = false
) {
  if (!task || !currentUser || isProjectArchived) return [];

  const current = task.status;
  const workflowTargets = ALLOWED_TASK_TRANSITIONS[current] || [];
  if (workflowTargets.length === 0) return [];

  if (canManageProject) {
    return workflowTargets;
  }

  const role = currentUser.role;
  const isAssignee = task.assignee?.id != null && task.assignee.id === currentUser.id;

  if (role === ROLES.DEVELOPER) {
    if (!isAssignee || current === TASK_STATUS.DONE) {
      return [];
    }
    return workflowTargets.filter((target) => target !== TASK_STATUS.DONE);
  }

  if (role === ROLES.QA_TESTER) {
    return workflowTargets.filter((target) => {
      const isReviewAction =
        current === TASK_STATUS.IN_REVIEW &&
        (target === TASK_STATUS.DONE ||
          target === TASK_STATUS.IN_PROGRESS ||
          target === TASK_STATUS.BLOCKED);
      const isReopenAction = current === TASK_STATUS.DONE && target === TASK_STATUS.IN_PROGRESS;
      return isReviewAction || isReopenAction || isAssignee;
    });
  }

  // Non-managing PROJECT_MANAGER acting as a regular member on another PM's project
  if (!isAssignee || current === TASK_STATUS.DONE) {
    return [];
  }
  return workflowTargets.filter((target) => target !== TASK_STATUS.DONE);
}

/**
 * Mirrors TaskServiceImpl.updateTaskStatus() same-column reorder check:
 * canManageTaskProject OR isTaskAssignee.
 */
export function canReorderWithinColumn(
  task,
  currentUser,
  canManageProject = false,
  isProjectArchived = false
) {
  if (!task || !currentUser || isProjectArchived) return false;
  if (canManageProject) return true;
  return task.assignee?.id != null && task.assignee.id === currentUser.id;
}

/**
 * A card is draggable if the user can either transition it to at least one other status
 * OR reorder it within its current column.
 */
export function canDragTask(
  task,
  currentUser,
  canManageProject = false,
  isProjectArchived = false
) {
  if (!task || !currentUser || isProjectArchived) return false;
  if (canReorderWithinColumn(task, currentUser, canManageProject, isProjectArchived)) {
    return true;
  }
  return getPermittedTaskTransitions(task, currentUser, canManageProject, isProjectArchived).length > 0;
}

/**
 * Mirrors TaskServiceImpl.updateTask() and enforceNonManagerFieldRestrictions().
 */
export function getTaskEditCapabilities(
  task,
  currentUser,
  canManageProject = false,
  isProjectArchived = false
) {
  if (!task || !currentUser || isProjectArchived) {
    return {
      canEditAny: false,
      isFullEdit: false,
      canEditDescription: false,
      canEditEstimatedHours: false,
    };
  }

  if (canManageProject) {
    return {
      canEditAny: true,
      isFullEdit: true,
      canEditDescription: true,
      canEditEstimatedHours: true,
    };
  }

  const isAssignee = task.assignee?.id != null && task.assignee.id === currentUser.id;
  if (!isAssignee) {
    return {
      canEditAny: false,
      isFullEdit: false,
      canEditDescription: false,
      canEditEstimatedHours: false,
    };
  }

  const isQa = currentUser.role === ROLES.QA_TESTER;
  return {
    canEditAny: true,
    isFullEdit: false,
    canEditDescription: true,
    canEditEstimatedHours: !isQa,
  };
}

/**
 * Human-readable labels for TaskHistoryResponse.fieldName values produced by TaskServiceImpl.
 */
export const TASK_HISTORY_FIELD_LABELS = Object.freeze({
  CREATED: 'Created',
  title: 'Title',
  description: 'Description',
  priority: 'Priority',
  status: 'Status',
  assignee: 'Assignee',
  due_date: 'Due Date',
  sprint: 'Sprint',
  parent_task: 'Parent Task',
  estimated_hours: 'Estimated Hours',
});
