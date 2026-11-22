import { useEffect, useMemo, useState } from 'react';
import { Archive, ArrowRight, Lock, RotateCcw, Save, ShieldAlert } from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardFooter, CardHeader } from '../../components/common/Card';
import { ConfirmDialog } from '../../components/common/ConfirmDialog';
import { Select, Textarea } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { projectService } from '../../services/projectService';
import { userService } from '../../services/userService';
import { ROLE_META, ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import {
  getPermittedNextStatuses,
  PROJECT_STATUS,
  PROJECT_STATUS_META,
} from './projectConstants';
import { ProjectStatusBadge } from './components/ProjectStatusBadge';

const MANAGER_ROLES = [ROLES.ADMIN, ROLES.PROJECT_MANAGER];
const CONFIRM_REQUIRED_STATUSES = new Set([
  PROJECT_STATUS.ON_HOLD,
  PROJECT_STATUS.COMPLETED,
  PROJECT_STATUS.ARCHIVED,
]);

function buildFormFromProject(project) {
  return {
    name: project?.name || '',
    description: project?.description || '',
    startDate: project?.startDate || '',
    endDate: project?.endDate || '',
    managerId: project?.manager?.id ? String(project.manager.id) : '',
  };
}

/**
 * Project Settings tab (ADMIN or assigned PROJECT_MANAGER):
 * - PUT    /api/v1/projects/{id}         (UpdateProjectRequest)
 * - PATCH  /api/v1/projects/{id}/status  (UpdateProjectStatusRequest)
 * - DELETE /api/v1/projects/{id}         (Archive project)
 */
export function ProjectSettingsTab({ project, onProjectUpdated }) {
  const { hasRole } = useAuth();
  const { addToast } = useToast();
  const isAdmin = hasRole(ROLES.ADMIN);
  const isArchived = project.status === PROJECT_STATUS.ARCHIVED;

  const [form, setForm] = useState(() => buildFormFromProject(project));
  const [errors, setErrors] = useState({});
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);

  const [managers, setManagers] = useState([]);
  const [loadingManagers, setLoadingManagers] = useState(false);

  const [pendingStatus, setPendingStatus] = useState(null);
  const [statusError, setStatusError] = useState('');
  const [changingStatus, setChangingStatus] = useState(false);

  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false);
  const [archiving, setArchiving] = useState(false);

  useEffect(() => {
    setForm(buildFormFromProject(project));
    setErrors({});
    setSaveError('');
    setStatusError('');
  }, [project]);

  useEffect(() => {
    if (!isAdmin) return undefined;
    let cancelled = false;
    setLoadingManagers(true);

    userService
      .listUsers({ active: true, page: 0, size: 100, sortBy: 'fullName', sortDir: 'asc' })
      .then((data) => {
        if (cancelled) return;
        const list = Array.isArray(data?.content) ? data.content : [];
        setManagers(list.filter((u) => MANAGER_ROLES.includes(u.role)));
      })
      .catch(() => {
        if (!cancelled) setManagers([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingManagers(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isAdmin]);

  const managerOptions = useMemo(() => {
    const map = new Map();
    if (project.manager?.id) {
      map.set(String(project.manager.id), {
        value: String(project.manager.id),
        label: `${project.manager.fullName} (${ROLE_META[project.manager.role]?.shortLabel || project.manager.role || 'Manager'})`,
      });
    }
    for (const mgr of managers) {
      map.set(String(mgr.id), {
        value: String(mgr.id),
        label: `${mgr.fullName} (${ROLE_META[mgr.role]?.shortLabel || mgr.role})`,
      });
    }
    return Array.from(map.values());
  }, [managers, project.manager]);

  const permittedNextStatuses = useMemo(
    () => getPermittedNextStatuses(project.status, isAdmin),
    [project.status, isAdmin]
  );

  const isDirty =
    form.name !== (project.name || '') ||
    form.description !== (project.description || '') ||
    form.startDate !== (project.startDate || '') ||
    form.endDate !== (project.endDate || '') ||
    (isAdmin && form.managerId !== (project.manager?.id ? String(project.manager.id) : ''));

  const updateField = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
    if (saveError) setSaveError('');
  };

  const validate = () => {
    const nextErrors = {};
    const trimmedName = form.name.trim();
    if (!trimmedName) {
      nextErrors.name = 'Project name is required.';
    } else if (trimmedName.length < 2 || trimmedName.length > 150) {
      nextErrors.name = 'Project name must be between 2 and 150 characters.';
    }
    if (form.description && form.description.length > 4000) {
      nextErrors.description = 'Description must not exceed 4000 characters.';
    }
    if (!form.startDate) {
      nextErrors.startDate = 'Start date is required.';
    }
    if (form.startDate && form.endDate && form.endDate < form.startDate) {
      nextErrors.endDate = 'End date must be on or after the start date.';
    }
    return nextErrors;
  };

  const handleSaveDetails = async (event) => {
    event.preventDefault();
    if (saving || isArchived) return;

    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setSaving(true);
    setSaveError('');
    try {
      const updated = await projectService.updateProject(project.id, {
        name: form.name,
        description: form.description,
        startDate: form.startDate,
        endDate: form.endDate || null,
        managerId: isAdmin && form.managerId ? Number(form.managerId) : null,
      });
      addToast({
        type: 'success',
        title: 'Project updated',
        message: `${updated.projectKey} settings have been saved.`,
      });
      onProjectUpdated?.(updated);
    } catch (err) {
      const parsed = parseApiError(err);
      if (parsed.fieldErrors && Object.keys(parsed.fieldErrors).length > 0) {
        setErrors((prev) => ({ ...prev, ...parsed.fieldErrors }));
      }
      setSaveError(parsed.message);
    } finally {
      setSaving(false);
    }
  };

  const executeStatusTransition = async (targetStatus) => {
    if (!targetStatus || changingStatus) return;
    setChangingStatus(true);
    setStatusError('');
    try {
      const updated = await projectService.updateProjectStatus(project.id, targetStatus);
      addToast({
        type: 'success',
        title: 'Project status updated',
        message: `${project.projectKey} transitioned to ${PROJECT_STATUS_META[targetStatus]?.label || targetStatus}.`,
      });
      setPendingStatus(null);
      onProjectUpdated?.(updated);
    } catch (err) {
      const parsed = parseApiError(err);
      setStatusError(parsed.message);
      addToast({
        type: 'error',
        title: 'Status transition blocked',
        message: parsed.message,
      });
      setPendingStatus(null);
    } finally {
      setChangingStatus(false);
    }
  };

  const handleStatusButtonClick = (targetStatus) => {
    setStatusError('');
    const requiresConfirm =
      CONFIRM_REQUIRED_STATUSES.has(targetStatus) ||
      (project.status === PROJECT_STATUS.ARCHIVED && targetStatus === PROJECT_STATUS.ACTIVE);

    if (requiresConfirm) {
      setPendingStatus(targetStatus);
    } else {
      executeStatusTransition(targetStatus);
    }
  };

  const handleConfirmArchive = async () => {
    if (archiving) return;
    setArchiving(true);
    setStatusError('');
    try {
      await projectService.archiveProject(project.id);
      addToast({
        type: 'success',
        title: 'Project archived',
        message: `${project.projectKey} has been moved to Archived status.`,
      });
      setArchiveConfirmOpen(false);
      onProjectUpdated?.();
    } catch (err) {
      const parsed = parseApiError(err);
      setStatusError(parsed.message);
      addToast({
        type: 'error',
        title: 'Unable to archive project',
        message: parsed.message,
      });
      setArchiveConfirmOpen(false);
    } finally {
      setArchiving(false);
    }
  };

  return (
    <div className="space-y-6">
      {isArchived && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3.5 flex items-start gap-3 text-xs text-amber-700">
          <Lock className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" aria-hidden="true" />
          <div className="space-y-1">
            <p className="font-semibold text-amber-800">Archived Project (Read-Only)</p>
            <p className="text-amber-700 leading-relaxed">
              Archived projects cannot be edited.{' '}
              {isAdmin
                ? 'As an Administrator, you can reactivate this project back to Active below.'
                : 'Contact an organization Administrator to reactivate this project.'}
            </p>
          </div>
        </div>
      )}

      {/* 1. General Project Configuration */}
      <Card>
        <CardHeader
          title="General Details"
          description="Update project metadata, schedule boundaries, and project leadership."
          action={
            <Badge tone="cyan" mono>
              Key: {project.projectKey}
            </Badge>
          }
        />
        <form onSubmit={handleSaveDetails} noValidate>
          <CardBody className="space-y-4">
            {saveError && (
              <div
                role="alert"
                className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-600"
              >
                {saveError}
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <Input
                label="Project Name"
                required
                value={form.name}
                onChange={(e) => updateField('name', e.target.value)}
                maxLength={150}
                error={errors.name}
                disabled={saving || isArchived}
                className="sm:col-span-2"
              />

              <Input
                label="Project Key"
                value={project.projectKey || ''}
                disabled
                helperText="Immutable after creation"
                inputClassName="font-mono uppercase"
              />
            </div>

            <Textarea
              label="Description"
              value={form.description}
              onChange={(e) => updateField('description', e.target.value)}
              rows={4}
              maxLength={4000}
              error={errors.description}
              disabled={saving || isArchived}
            />

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <Input
                label="Start Date"
                type="date"
                required
                value={form.startDate}
                onChange={(e) => updateField('startDate', e.target.value)}
                error={errors.startDate}
                disabled={saving || isArchived}
              />

              <Input
                label="Target End Date"
                type="date"
                value={form.endDate}
                min={form.startDate || undefined}
                onChange={(e) => updateField('endDate', e.target.value)}
                error={errors.endDate}
                disabled={saving || isArchived}
              />

              {isAdmin ? (
                <Select
                  label="Project Manager"
                  value={form.managerId}
                  onChange={(e) => updateField('managerId', e.target.value)}
                  options={managerOptions}
                  error={errors.managerId}
                  disabled={saving || isArchived || loadingManagers}
                  helperText="Admin only: reassign project ownership"
                />
              ) : (
                <Input
                  label="Project Manager"
                  value={project.manager?.fullName || 'Unassigned'}
                  disabled
                  helperText="Only Administrators can reassign the manager"
                />
              )}
            </div>
          </CardBody>

          <CardFooter>
            <Button
              type="button"
              variant="ghost"
              disabled={!isDirty || saving || isArchived}
              onClick={() => {
                setForm(buildFormFromProject(project));
                setErrors({});
                setSaveError('');
              }}
            >
              Reset
            </Button>
            <Button
              type="submit"
              loading={saving}
              disabled={!isDirty || isArchived}
              leftIcon={<Save className="h-4 w-4" />}
            >
              Save Changes
            </Button>
          </CardFooter>
        </form>
      </Card>

      {/* 2. Lifecycle Status Transitions */}
      <Card>
        <CardHeader
          title="Project Lifecycle Status"
          description="Only workflow transitions permitted by backend rules and your role are available."
          action={<ProjectStatusBadge status={project.status} />}
        />
        <CardBody className="space-y-4">
          {statusError && (
            <div
              role="alert"
              className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-600"
            >
              {statusError}
            </div>
          )}

          <p className="text-xs text-ink-muted leading-relaxed">
            {PROJECT_STATUS_META[project.status]?.description}
          </p>

          {permittedNextStatuses.length === 0 ? (
            <div className="rounded-lg border border-line bg-canvas px-3.5 py-3 text-xs text-ink-muted">
              No status transitions are available for your role while the project is in{' '}
              <span className="font-medium text-ink">
                {PROJECT_STATUS_META[project.status]?.label || project.status}
              </span>{' '}
              status.
            </div>
          ) : (
            <div className="space-y-2.5">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-muted">
                Available Transitions
              </p>
              <div className="flex flex-wrap items-center gap-2.5">
                {permittedNextStatuses.map((nextStatus) => {
                  const meta = PROJECT_STATUS_META[nextStatus];
                  const isReactivate =
                    project.status === PROJECT_STATUS.ARCHIVED &&
                    nextStatus === PROJECT_STATUS.ACTIVE;
                  const isArchiveTarget = nextStatus === PROJECT_STATUS.ARCHIVED;

                  return (
                    <Button
                      key={nextStatus}
                      variant={
                        isArchiveTarget
                          ? 'danger'
                          : isReactivate
                            ? 'primary'
                            : 'secondary'
                      }
                      size="sm"
                      loading={changingStatus && pendingStatus === nextStatus}
                      disabled={changingStatus}
                      onClick={() => handleStatusButtonClick(nextStatus)}
                      leftIcon={
                        isReactivate ? (
                          <RotateCcw className="h-3.5 w-3.5" />
                        ) : isArchiveTarget ? (
                          <Archive className="h-3.5 w-3.5" />
                        ) : (
                          <ArrowRight className="h-3.5 w-3.5" />
                        )
                      }
                    >
                      {isReactivate
                        ? 'Reactivate to Active'
                        : `Move to ${meta?.label || nextStatus}`}
                    </Button>
                  );
                })}
              </div>
            </div>
          )}
        </CardBody>
      </Card>

      {/* 3. Archive Project (DELETE /api/v1/projects/{id}) */}
      {!isArchived && (
        <Card className="border-red-200">
          <CardHeader
            title="Archive Project"
            description="Soft-archives this project and locks editing and membership changes."
          />
          <CardBody className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex items-start gap-3 min-w-0">
              <div className="h-9 w-9 rounded-lg bg-red-50 border border-red-200 flex items-center justify-center text-red-600 shrink-0">
                <ShieldAlert className="h-4 w-4" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <p className="text-sm font-medium text-ink">
                  Archive {project.projectKey} — {project.name}
                </p>
                <p className="text-xs text-ink-muted mt-1 leading-relaxed">
                  Projects with an active running sprint cannot be archived until the sprint is
                  completed or cancelled. Only an organization Administrator can reactivate an
                  archived project.
                </p>
              </div>
            </div>

            <Button
              variant="danger"
              size="sm"
              className="shrink-0"
              onClick={() => setArchiveConfirmOpen(true)}
              leftIcon={<Archive className="h-4 w-4" />}
            >
              Archive Project
            </Button>
          </CardBody>
        </Card>
      )}

      {/* Status transition confirmation dialog */}
      <ConfirmDialog
        isOpen={Boolean(pendingStatus)}
        onClose={() => setPendingStatus(null)}
        onConfirm={() => executeStatusTransition(pendingStatus)}
        title={
          pendingStatus === PROJECT_STATUS.ARCHIVED
            ? 'Archive Project'
            : pendingStatus === PROJECT_STATUS.ACTIVE &&
                project.status === PROJECT_STATUS.ARCHIVED
              ? 'Reactivate Archived Project'
              : `Change Status to ${PROJECT_STATUS_META[pendingStatus]?.label || pendingStatus}`
        }
        message={
          pendingStatus === PROJECT_STATUS.ARCHIVED
            ? `Archive ${project.projectKey}? Editing and member changes will be locked, and only an Administrator can reactivate it. Projects with an active sprint cannot be archived.`
            : pendingStatus === PROJECT_STATUS.ACTIVE &&
                project.status === PROJECT_STATUS.ARCHIVED
              ? `Reactivate ${project.projectKey} back to Active status? Team members and managers will regain write access.`
              : `Transition ${project.projectKey} from ${PROJECT_STATUS_META[project.status]?.label || project.status} to ${PROJECT_STATUS_META[pendingStatus]?.label || pendingStatus}?`
        }
        confirmLabel={
          pendingStatus === PROJECT_STATUS.ARCHIVED
            ? 'Archive Project'
            : pendingStatus === PROJECT_STATUS.ACTIVE &&
                project.status === PROJECT_STATUS.ARCHIVED
              ? 'Reactivate Project'
              : 'Confirm Transition'
        }
        variant={pendingStatus === PROJECT_STATUS.ARCHIVED ? 'danger' : 'primary'}
        loading={changingStatus}
      />

      {/* Explicit Archive button confirmation dialog */}
      <ConfirmDialog
        isOpen={archiveConfirmOpen}
        onClose={() => setArchiveConfirmOpen(false)}
        onConfirm={handleConfirmArchive}
        title="Archive Project"
        message={`Are you sure you want to archive ${project.projectKey} — ${project.name}? All edits and membership changes will be locked until an Administrator reactivates the project.`}
        confirmLabel="Archive Project"
        variant="danger"
        loading={archiving}
      />
    </div>
  );
}
