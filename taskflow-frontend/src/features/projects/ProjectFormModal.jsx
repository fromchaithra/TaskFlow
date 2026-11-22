import { useEffect, useMemo, useState } from 'react';
import { Check, FolderPlus, Search, Users } from 'lucide-react';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Select, Textarea } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { Spinner } from '../../components/common/Loading';
import { Modal } from '../../components/common/Modal';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { projectService } from '../../services/projectService';
import { teamService } from '../../services/teamService';
import { userService } from '../../services/userService';
import { ROLE_META, ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { getTodayIsoDate } from '../../utils/formatters';
import { INITIAL_PROJECT_STATUSES, PROJECT_STATUS_META } from './projectConstants';

const PROJECT_KEY_REGEX = /^[A-Za-z][A-Za-z0-9]{1,9}$/;
const MANAGER_ROLES = [ROLES.ADMIN, ROLES.PROJECT_MANAGER];

const INITIAL_STATUS_OPTIONS = INITIAL_PROJECT_STATUSES.map((status) => ({
  value: status,
  label: PROJECT_STATUS_META[status]?.label || status,
}));

function buildDefaultForm(currentUser) {
  return {
    name: '',
    projectKey: '',
    description: '',
    startDate: getTodayIsoDate(),
    endDate: '',
    initialStatus: 'ACTIVE',
    managerId: currentUser?.id ? String(currentUser.id) : '',
    teamId: '',
    memberIds: [],
  };
}

/**
 * Create Project Modal mapped strictly to com.taskflow.dto.project.CreateProjectRequest:
 * POST /api/v1/projects (ADMIN, PROJECT_MANAGER)
 */
export function ProjectFormModal({ isOpen, onClose, onCreated }) {
  const { user, hasRole } = useAuth();
  const { addToast } = useToast();
  const isAdmin = hasRole(ROLES.ADMIN);

  const [form, setForm] = useState(() => buildDefaultForm(user));
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [employees, setEmployees] = useState([]);
  const [teams, setTeams] = useState([]);
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [memberSearch, setMemberSearch] = useState('');

  useEffect(() => {
    if (!isOpen) return undefined;

    let cancelled = false;
    setForm(buildDefaultForm(user));
    setErrors({});
    setFormError('');
    setMemberSearch('');
    setLoadingOptions(true);

    Promise.all([
      userService.listUsers({ active: true, page: 0, size: 100, sortBy: 'fullName', sortDir: 'asc' }),
      teamService.listTeams({ active: true, page: 0, size: 100, sortBy: 'name', sortDir: 'asc' }),
    ])
      .then(([usersPage, teamsPage]) => {
        if (cancelled) return;
        setEmployees(Array.isArray(usersPage?.content) ? usersPage.content : []);
        setTeams(Array.isArray(teamsPage?.content) ? teamsPage.content : []);
      })
      .catch(() => {
        if (cancelled) return;
        // Non-blocking: core project creation still works even if optional pickers fail to load
        setEmployees([]);
        setTeams([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingOptions(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isOpen, user]);

  const managerOptions = useMemo(() => {
    const eligible = employees.filter((emp) => MANAGER_ROLES.includes(emp.role));
    return eligible.map((emp) => ({
      value: String(emp.id),
      label: `${emp.fullName} (${ROLE_META[emp.role]?.shortLabel || emp.role})`,
    }));
  }, [employees]);

  const teamOptions = useMemo(
    () => [
      { value: '', label: 'None (do not seed from a team)' },
      ...teams.map((team) => ({
        value: String(team.id),
        label: `${team.name}${team.memberCount != null ? ` (${team.memberCount} members)` : ''}`,
      })),
    ],
    [teams]
  );

  const filteredMemberCandidates = useMemo(() => {
    const query = memberSearch.trim().toLowerCase();
    const assignedManagerId = isAdmin
      ? Number(form.managerId || user?.id)
      : Number(user?.id);

    return employees
      .filter((emp) => Number(emp.id) !== assignedManagerId)
      .filter((emp) => {
        if (!query) return true;
        return (
          emp.fullName?.toLowerCase().includes(query) ||
          emp.email?.toLowerCase().includes(query) ||
          emp.employeeCode?.toLowerCase().includes(query)
        );
      });
  }, [employees, memberSearch, isAdmin, form.managerId, user]);

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

  const toggleMember = (employeeId) => {
    const idNum = Number(employeeId);
    setForm((prev) => {
      const exists = prev.memberIds.includes(idNum);
      return {
        ...prev,
        memberIds: exists
          ? prev.memberIds.filter((id) => id !== idNum)
          : [...prev.memberIds, idNum],
      };
    });
  };

  const validate = () => {
    const nextErrors = {};
    const trimmedName = form.name.trim();
    const trimmedKey = form.projectKey.trim().toUpperCase();

    if (!trimmedName) {
      nextErrors.name = 'Project name is required.';
    } else if (trimmedName.length < 2 || trimmedName.length > 150) {
      nextErrors.name = 'Project name must be between 2 and 150 characters.';
    }

    if (!trimmedKey) {
      nextErrors.projectKey = 'Project key is required.';
    } else if (!PROJECT_KEY_REGEX.test(trimmedKey)) {
      nextErrors.projectKey =
        'Must be 2–10 alphanumeric characters starting with a letter (e.g., TFLOW, WEB2).';
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
      const created = await projectService.createProject({
        name: form.name,
        projectKey: form.projectKey,
        description: form.description,
        startDate: form.startDate,
        endDate: form.endDate || null,
        initialStatus: form.initialStatus,
        managerId: isAdmin && form.managerId ? Number(form.managerId) : null,
        teamId: form.teamId ? Number(form.teamId) : null,
        memberIds: form.memberIds,
      });

      addToast({
        type: 'success',
        title: 'Project created',
        message: `${created.projectKey} — ${created.name} is ready.`,
      });
      onClose?.();
      onCreated?.(created);
    } catch (err) {
      const parsed = parseApiError(err);
      if (parsed.fieldErrors && Object.keys(parsed.fieldErrors).length > 0) {
        setErrors((prev) => ({ ...prev, ...parsed.fieldErrors }));
      }
      if (parsed.status === 409) {
        setErrors((prev) => ({
          ...prev,
          projectKey: parsed.message || 'This project key is already in use in your organization.',
        }));
      }
      setFormError(parsed.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? undefined : onClose}
      closeOnBackdrop={!submitting}
      title="Create Project"
      description="Define a new project scope, timeline, lifecycle state, and initial project members."
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="create-project-form"
            loading={submitting}
            leftIcon={<FolderPlus className="h-4 w-4" />}
          >
            Create Project
          </Button>
        </>
      }
    >
      <form id="create-project-form" onSubmit={handleSubmit} className="space-y-5" noValidate aria-busy={submitting}>
        {formError && (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-600"
          >
            {formError}
          </div>
        )}

        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-faint">Project details</p>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Input
            label="Project Name"
            required
            value={form.name}
            onChange={(e) => updateField('name', e.target.value)}
            placeholder="e.g., Core Billing Platform"
            maxLength={150}
            error={errors.name}
            disabled={submitting}
            className="sm:col-span-2"
          />

          <Input
            label="Project Key"
            required
            value={form.projectKey}
            onChange={(e) =>
              updateField('projectKey', e.target.value.replace(/[^A-Za-z0-9]/g, '').toUpperCase())
            }
            placeholder="e.g., BILL"
            maxLength={10}
            error={errors.projectKey}
            helperText="2–10 alphanumeric, starts with letter"
            inputClassName="font-mono uppercase tracking-wider"
            disabled={submitting}
          />
        </div>

        <Textarea
          label="Description"
          value={form.description}
          onChange={(e) => updateField('description', e.target.value)}
          placeholder="Outline objectives, delivery milestones, and engineering scope..."
          rows={3}
          maxLength={4000}
          error={errors.description}
          disabled={submitting}
        />

        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-faint">Schedule &amp; lifecycle</p>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Input
            label="Start Date"
            type="date"
            required
            value={form.startDate}
            onChange={(e) => updateField('startDate', e.target.value)}
            error={errors.startDate}
            disabled={submitting}
          />

          <Input
            label="End Date"
            type="date"
            value={form.endDate}
            min={form.startDate || undefined}
            onChange={(e) => updateField('endDate', e.target.value)}
            error={errors.endDate}
            helperText="Optional target date"
            disabled={submitting}
          />

          <Select
            label="Initial Status"
            value={form.initialStatus}
            onChange={(e) => updateField('initialStatus', e.target.value)}
            options={INITIAL_STATUS_OPTIONS}
            error={errors.initialStatus}
            disabled={submitting}
          />
        </div>

        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-faint">Team &amp; members</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1 border-t border-line">
          {isAdmin ? (
            <Select
              label="Project Manager"
              value={form.managerId}
              onChange={(e) => updateField('managerId', e.target.value)}
              options={
                managerOptions.length > 0
                  ? managerOptions
                  : [{ value: String(user?.id || ''), label: user?.fullName || 'Current user' }]
              }
              error={errors.managerId}
              helperText="Must be an active Administrator or Project Manager."
              disabled={submitting || loadingOptions}
            />
          ) : (
            <div className="rounded-lg border border-line bg-canvas px-3.5 py-2.5">
              <p className="text-[11px] font-medium uppercase tracking-wider text-ink-muted">
                Project Manager
              </p>
              <p className="text-sm font-medium text-ink mt-1">{user?.fullName}</p>
              <p className="text-xs text-ink-faint mt-0.5">
                You will be assigned as the manager of this project.
              </p>
            </div>
          )}

          <Select
            label="Seed Members from Team (Optional)"
            value={form.teamId}
            onChange={(e) => updateField('teamId', e.target.value)}
            options={teamOptions}
            error={errors.teamId}
            helperText="Adds the selected team's lead and active members on creation."
            disabled={submitting || loadingOptions}
          />
        </div>

        {/* Optional additional members checklist */}
        <div className="space-y-2.5 pt-1 border-t border-line">
          <div className="flex items-center justify-between gap-2">
            <label className="text-xs font-medium text-ink-muted flex items-center gap-1.5">
              <Users className="h-3.5 w-3.5 text-ink-muted" aria-hidden="true" />
              <span>Additional Project Members (Optional)</span>
            </label>
            {form.memberIds.length > 0 && (
              <Badge tone="cyan" mono>
                {form.memberIds.length} selected
              </Badge>
            )}
          </div>

          <Input
            value={memberSearch}
            onChange={(e) => setMemberSearch(e.target.value)}
            placeholder="Search active employees to include..."
            leftIcon={<Search className="h-3.5 w-3.5" />}
            disabled={submitting || loadingOptions}
          />

          <div className="rounded-lg border border-line bg-canvas overflow-hidden">
            {loadingOptions ? (
              <div className="flex items-center justify-center gap-2 py-5 text-xs text-ink-muted">
                <Spinner size="sm" />
                <span>Loading organization directory...</span>
              </div>
            ) : filteredMemberCandidates.length === 0 ? (
              <p className="p-3.5 text-xs text-ink-faint text-center">
                No additional employees match your search.
              </p>
            ) : (
              <ul className="max-h-40 overflow-y-auto divide-y divide-line">
                {filteredMemberCandidates.map((emp) => {
                  const selected = form.memberIds.includes(Number(emp.id));
                  const roleMeta = ROLE_META[emp.role] || { label: emp.role, tone: 'neutral' };
                  return (
                    <li key={emp.id}>
                      <button
                        type="button"
                        onClick={() => toggleMember(emp.id)}
                        disabled={submitting}
                        className={[
                          'w-full px-3 py-2 text-left flex items-center justify-between gap-3 transition-colors',
                          selected
                            ? 'bg-brand-500/10 text-ink'
                            : 'hover:bg-subtle text-ink-muted',
                        ].join(' ')}
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <span
                            className={[
                              'h-4 w-4 rounded border flex items-center justify-center shrink-0 transition-colors',
                              selected
                                ? 'bg-brand-500 border-brand-500 text-white'
                                : 'border-line-strong bg-canvas',
                            ].join(' ')}
                            aria-hidden="true"
                          >
                            {selected && <Check className="h-3 w-3 stroke-[3]" />}
                          </span>
                          <Avatar name={emp.fullName} color={emp.avatarColor} size="xs" />
                          <div className="min-w-0">
                            <p className="text-xs font-medium text-ink truncate">
                              {emp.fullName}
                            </p>
                            <p className="text-[11px] text-ink-faint truncate">{emp.email}</p>
                          </div>
                        </div>
                        <Badge tone={roleMeta.tone}>{roleMeta.shortLabel || roleMeta.label}</Badge>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>
      </form>
    </Modal>
  );
}
