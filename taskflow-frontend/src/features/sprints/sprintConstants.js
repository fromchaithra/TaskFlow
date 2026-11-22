import { getTodayIsoDate } from '../../utils/formatters';

/**
 * Exact backend SprintStatus enum values (com.taskflow.enums.SprintStatus).
 */
export const SPRINT_STATUS = Object.freeze({
  PLANNED: 'PLANNED',
  ACTIVE: 'ACTIVE',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
});

export const ALL_SPRINT_STATUSES = Object.freeze([
  SPRINT_STATUS.PLANNED,
  SPRINT_STATUS.ACTIVE,
  SPRINT_STATUS.COMPLETED,
  SPRINT_STATUS.CANCELLED,
]);

export const OPEN_SPRINT_STATUSES = Object.freeze([
  SPRINT_STATUS.PLANNED,
  SPRINT_STATUS.ACTIVE,
]);

export const SPRINT_STATUS_META = Object.freeze({
  [SPRINT_STATUS.PLANNED]: {
    label: 'Planned',
    tone: 'steel',
    description: 'Scope is being prepared. Tasks can still be added or removed.',
  },
  [SPRINT_STATUS.ACTIVE]: {
    label: 'Active',
    tone: 'cyan',
    description: 'The team is executing the sprint. Tasks can still be adjusted.',
  },
  [SPRINT_STATUS.COMPLETED]: {
    label: 'Completed',
    tone: 'emerald',
    description: 'Sprint closed normally. The report is a read-only snapshot.',
  },
  [SPRINT_STATUS.CANCELLED]: {
    label: 'Cancelled',
    tone: 'red',
    description: 'Sprint was cancelled. Remaining tasks were returned to the backlog.',
  },
});

export const SPRINT_NAME_MIN_LENGTH = 2;
export const SPRINT_NAME_MAX_LENGTH = 120;
export const SPRINT_GOAL_MAX_LENGTH = 4000;

export function isSprintOpen(status) {
  return OPEN_SPRINT_STATUSES.includes(status);
}

export function isSprintTerminal(status) {
  return status === SPRINT_STATUS.COMPLETED || status === SPRINT_STATUS.CANCELLED;
}

export function formatSprintStatus(status) {
  return SPRINT_STATUS_META[status]?.label || status || 'Unknown';
}

/** Sprint creation is blocked by the backend for ARCHIVED and COMPLETED projects. */
export function canCreateSprintInProject(projectStatus) {
  return projectStatus !== 'ARCHIVED' && projectStatus !== 'COMPLETED';
}

/**
 * Mirrors SprintServiceImpl.ensureProjectActiveForMutation() and ensureSprintEditable():
 * update and task changes are rejected for archived projects and terminal sprints.
 */
export function isSprintEditable(sprint, projectStatus) {
  return Boolean(sprint) && isSprintOpen(sprint.status) && projectStatus !== 'ARCHIVED';
}

/**
 * Derives the exact actions allowed by SprintServiceImpl for the current user and state.
 * `canManage` must already reflect ProjectSecurityEvaluator.canManageProject().
 */
export function getSprintCapabilities({ sprint, projectStatus, canManage }) {
  const status = sprint?.status;
  const editable = Boolean(canManage) && isSprintEditable(sprint, projectStatus);

  return {
    canEdit: editable,
    canManageTasks: editable,
    canStart:
      Boolean(canManage) &&
      status === SPRINT_STATUS.PLANNED &&
      projectStatus !== 'ARCHIVED' &&
      projectStatus !== 'COMPLETED',
    canComplete: Boolean(canManage) && status === SPRINT_STATUS.ACTIVE,
    canCancel:
      Boolean(canManage) &&
      (status === SPRINT_STATUS.PLANNED || status === SPRINT_STATUS.ACTIVE),
    canViewReport: Boolean(sprint),
  };
}

/** Explains why a lifecycle action is unavailable, for read-only UI hints. */
export function getSprintStateHint({ sprint, projectStatus, canManage }) {
  if (!sprint) return '';
  if (!canManage) {
    return 'Only an organization admin or the assigned project manager can change sprint lifecycle state.';
  }
  if (projectStatus === 'ARCHIVED') {
    return 'This project is archived. Sprint planning and task changes are read-only.';
  }
  if (sprint.status === SPRINT_STATUS.PLANNED && projectStatus === 'COMPLETED') {
    return 'This project is completed, so this planned sprint cannot be started.';
  }
  if (sprint.status === SPRINT_STATUS.PLANNED) {
    return 'Start the sprint when the scope is ready. Only one sprint can be active per project.';
  }
  if (sprint.status === SPRINT_STATUS.ACTIVE) {
    return 'Complete the sprint to publish its report, or cancel it to return unfinished work to the backlog.';
  }
  if (sprint.status === SPRINT_STATUS.COMPLETED) {
    return 'This sprint is completed and read-only. Its report reflects the state at completion.';
  }
  return 'This sprint was cancelled and is read-only. Remaining tasks were returned to the backlog.';
}

export function addDays(isoDate, days) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(isoDate || ''));
  if (!match) return '';
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  date.setDate(date.getDate() + Number(days));
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Sensible default window: today (clamped to the project start) through 13 days later
 * (clamped to the project end). The backend still validates the final submitted values.
 */
export function buildDefaultSprintDates(project) {
  const today = getTodayIsoDate();
  let startDate = today;
  if (project?.startDate && startDate < project.startDate) {
    startDate = project.startDate;
  }
  let endDate = addDays(startDate, 13);
  if (project?.endDate && endDate > project.endDate) {
    endDate = project.endDate;
  }
  return { startDate, endDate };
}

/**
 * Client-side mirror of SprintServiceImpl.validateSprintDates() plus DTO constraints.
 * Returns a map of field -> message (empty when valid).
 */
export function validateSprintForm(values, project) {
  const errors = {};
  const name = (values.name || '').trim();
  const goal = values.goal || '';
  const { startDate, endDate } = values;

  if (!name) {
    errors.name = 'Sprint name is required.';
  } else if (name.length < SPRINT_NAME_MIN_LENGTH) {
    errors.name = `Sprint name must be at least ${SPRINT_NAME_MIN_LENGTH} characters.`;
  } else if (name.length > SPRINT_NAME_MAX_LENGTH) {
    errors.name = `Sprint name must be at most ${SPRINT_NAME_MAX_LENGTH} characters.`;
  }

  if (goal.length > SPRINT_GOAL_MAX_LENGTH) {
    errors.goal = `Sprint goal must not exceed ${SPRINT_GOAL_MAX_LENGTH} characters.`;
  }

  if (!startDate) {
    errors.startDate = 'Sprint start date is required.';
  }
  if (!endDate) {
    errors.endDate = 'Sprint end date is required.';
  }

  if (startDate && endDate && endDate <= startDate) {
    errors.endDate = 'Sprint end date must be after the start date.';
  }
  if (startDate && project?.startDate && startDate < project.startDate) {
    errors.startDate = 'Sprint start date cannot be earlier than the project start date.';
  }
  if (endDate && project?.endDate && endDate > project.endDate) {
    errors.endDate = 'Sprint end date cannot be later than the project end date.';
  }

  return errors;
}
