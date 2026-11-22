import { useMemo, useState } from 'react';
import { CheckCircle2, Play, XCircle } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { ConfirmDialog } from '../../components/common/ConfirmDialog';
import { useToast } from '../../hooks/useToast';
import { sprintService } from '../../services/sprintService';
import { parseApiError } from '../../utils/errorHandler';
import { getSprintCapabilities } from './sprintConstants';

const ACTION_META = {
  start: {
    label: 'Start sprint',
    icon: Play,
    variant: 'primary',
    busyLabel: 'Starting...',
    title: 'Start sprint',
    confirmLabel: 'Start sprint',
    buildMessage: (sprint) =>
      `Start “${sprint.name}”? This makes it the project's active sprint. Only one sprint can be active per project.`,
  },
  complete: {
    label: 'Complete sprint',
    icon: CheckCircle2,
    variant: 'primary',
    busyLabel: 'Completing...',
    title: 'Complete sprint',
    confirmLabel: 'Complete sprint',
    buildMessage: (sprint) =>
      `Complete “${sprint.name}”? Done tasks are recorded in the report and unfinished tasks return to the backlog.`,
  },
  cancel: {
    label: 'Cancel sprint',
    icon: XCircle,
    variant: 'danger',
    busyLabel: 'Cancelling...',
    title: 'Cancel sprint',
    confirmLabel: 'Cancel sprint',
    buildMessage: (sprint) =>
      `Cancel “${sprint.name}”? All assigned tasks return to the backlog and this cannot be undone.`,
  },
};

/**
 * Lifecycle controls respecting SprintServiceImpl transition rules:
 * PLANNED -> start, PLANNED|ACTIVE -> cancel, ACTIVE -> complete.
 */
export function SprintLifecycleActions({
  sprint,
  projectStatus,
  canManage,
  onChanged,
  className = '',
}) {
  const toast = useToast();
  const [pendingAction, setPendingAction] = useState(null);
  const [busy, setBusy] = useState(false);

  const capabilities = useMemo(
    () => getSprintCapabilities({ sprint, projectStatus, canManage }),
    [sprint, projectStatus, canManage]
  );

  const availableActions = [
    capabilities.canStart && 'start',
    capabilities.canComplete && 'complete',
    capabilities.canCancel && 'cancel',
  ].filter(Boolean);

  if (!sprint || availableActions.length === 0) {
    return null;
  }

  const runAction = async () => {
    if (!pendingAction || busy) return;
    setBusy(true);

    try {
      let updated;
      if (pendingAction === 'start') {
        updated = await sprintService.startSprint(sprint.projectId, sprint.id);
      } else if (pendingAction === 'complete') {
        updated = await sprintService.completeSprint(sprint.projectId, sprint.id);
      } else {
        updated = await sprintService.cancelSprint(sprint.projectId, sprint.id);
      }

      const message =
        pendingAction === 'start'
          ? `Sprint “${sprint.name}” is now active.`
          : pendingAction === 'complete'
            ? `Sprint “${sprint.name}” was completed.`
            : `Sprint “${sprint.name}” was cancelled.`;

      toast.success(message, 'Sprint updated');
      setPendingAction(null);
      onChanged?.(updated);
    } catch (err) {
      toast.error(parseApiError(err).message, 'Sprint action failed');
    } finally {
      setBusy(false);
    }
  };

  const meta = pendingAction ? ACTION_META[pendingAction] : null;

  return (
    <div className={`flex flex-wrap items-center gap-2 ${className}`}>
      {capabilities.canStart && (
        <Button
          size="sm"
          variant="primary"
          leftIcon={<Play className="h-3.5 w-3.5" />}
          onClick={() => setPendingAction('start')}
          disabled={busy}
        >
          Start sprint
        </Button>
      )}

      {capabilities.canComplete && (
        <Button
          size="sm"
          variant="primary"
          leftIcon={<CheckCircle2 className="h-3.5 w-3.5" />}
          onClick={() => setPendingAction('complete')}
          disabled={busy}
        >
          Complete sprint
        </Button>
      )}

      {capabilities.canCancel && (
        <Button
          size="sm"
          variant="outline"
          leftIcon={<XCircle className="h-3.5 w-3.5" />}
          onClick={() => setPendingAction('cancel')}
          disabled={busy}
          className="text-red-600 hover:text-red-700"
        >
          Cancel sprint
        </Button>
      )}

      {meta && (
        <ConfirmDialog
          isOpen
          onClose={() => (busy ? null : setPendingAction(null))}
          onConfirm={runAction}
          title={meta.title}
          message={meta.buildMessage(sprint)}
          confirmLabel={meta.confirmLabel}
          variant={meta.variant}
          loading={busy}
        />
      )}
    </div>
  );
}
