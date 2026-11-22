/**
 * Exact ProjectStatus enum values and allowed transitions from
 * com.taskflow.enums.ProjectStatus and ProjectServiceImpl.buildStatusTransitions().
 */
export const PROJECT_STATUS = Object.freeze({
  PLANNING: 'PLANNING',
  ACTIVE: 'ACTIVE',
  ON_HOLD: 'ON_HOLD',
  COMPLETED: 'COMPLETED',
  ARCHIVED: 'ARCHIVED',
});

export const ALL_PROJECT_STATUSES = Object.freeze([
  PROJECT_STATUS.PLANNING,
  PROJECT_STATUS.ACTIVE,
  PROJECT_STATUS.ON_HOLD,
  PROJECT_STATUS.COMPLETED,
  PROJECT_STATUS.ARCHIVED,
]);

export const INITIAL_PROJECT_STATUSES = Object.freeze([
  PROJECT_STATUS.ACTIVE,
  PROJECT_STATUS.PLANNING,
]);

export const PROJECT_STATUS_META = Object.freeze({
  [PROJECT_STATUS.PLANNING]: {
    label: 'Planning',
    tone: 'steel',
    description: 'Initial scope definition and team assembly before active execution.',
  },
  [PROJECT_STATUS.ACTIVE]: {
    label: 'Active',
    tone: 'emerald',
    description: 'In active engineering delivery with open task and sprint workflows.',
  },
  [PROJECT_STATUS.ON_HOLD]: {
    label: 'On Hold',
    tone: 'amber',
    description: 'Temporarily paused pending external decisions, budget, or dependencies.',
  },
  [PROJECT_STATUS.COMPLETED]: {
    label: 'Completed',
    tone: 'cyan',
    description: 'Planned deliverables are finished; can be reopened to Active or archived.',
  },
  [PROJECT_STATUS.ARCHIVED]: {
    label: 'Archived',
    tone: 'neutral',
    description: 'Read-only archived state. Only an Administrator can reactivate back to Active.',
  },
});

/**
 * Exact backend transition graph from ProjectServiceImpl.buildStatusTransitions().
 */
export const ALLOWED_PROJECT_TRANSITIONS = Object.freeze({
  [PROJECT_STATUS.PLANNING]: [
    PROJECT_STATUS.ACTIVE,
    PROJECT_STATUS.ON_HOLD,
    PROJECT_STATUS.ARCHIVED,
  ],
  [PROJECT_STATUS.ACTIVE]: [
    PROJECT_STATUS.ON_HOLD,
    PROJECT_STATUS.COMPLETED,
    PROJECT_STATUS.ARCHIVED,
  ],
  [PROJECT_STATUS.ON_HOLD]: [PROJECT_STATUS.ACTIVE, PROJECT_STATUS.ARCHIVED],
  [PROJECT_STATUS.COMPLETED]: [PROJECT_STATUS.ACTIVE, PROJECT_STATUS.ARCHIVED],
  [PROJECT_STATUS.ARCHIVED]: [PROJECT_STATUS.ACTIVE],
});

export function getPermittedNextStatuses(currentStatus, isAdmin) {
  if (!currentStatus) return [];
  if (currentStatus === PROJECT_STATUS.ARCHIVED) {
    return isAdmin ? [PROJECT_STATUS.ACTIVE] : [];
  }
  return ALLOWED_PROJECT_TRANSITIONS[currentStatus] || [];
}
