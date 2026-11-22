import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, Search } from 'lucide-react';
import { teamService } from '../../services/teamService';
import { userService } from '../../services/userService';
import { ROLE_META } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Checkbox, Select, Textarea } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { Spinner } from '../../components/common/Loading';
import { Modal } from '../../components/common/Modal';

export function TeamFormModal({
  isOpen,
  onClose,
  mode = 'create',
  initialTeam = null,
  onSuccess,
}) {
  const isEdit = mode === 'edit';

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [leadUserId, setLeadUserId] = useState('');
  const [active, setActive] = useState(true);
  const [selectedMemberIds, setSelectedMemberIds] = useState([]);
  const [memberSearch, setMemberSearch] = useState('');

  const [employees, setEmployees] = useState([]);
  const [loadingEmployees, setLoadingEmployees] = useState(false);
  const [employeeLoadError, setEmployeeLoadError] = useState(null);

  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen) return;

    if (isEdit && initialTeam) {
      setName(initialTeam.name || '');
      setDescription(initialTeam.description || '');
      setLeadUserId(initialTeam.lead?.id ? String(initialTeam.lead.id) : '');
      setActive(Boolean(initialTeam.active));
      setSelectedMemberIds([]);
    } else {
      setName('');
      setDescription('');
      setLeadUserId('');
      setActive(true);
      setSelectedMemberIds([]);
    }
    setMemberSearch('');
    setFieldErrors({});
    setFormError(null);

    let mounted = true;
    async function loadEmployees() {
      setLoadingEmployees(true);
      setEmployeeLoadError(null);
      try {
        const res = await userService.listUsers({
          active: true,
          page: 0,
          size: 100,
          sortBy: 'fullName',
          sortDir: 'asc',
        });
        if (mounted) {
          setEmployees(res.content || []);
        }
      } catch (err) {
        if (mounted) {
          setEmployeeLoadError(parseApiError(err).message);
        }
      } finally {
        if (mounted) {
          setLoadingEmployees(false);
        }
      }
    }

    loadEmployees();
    return () => {
      mounted = false;
    };
  }, [isOpen, isEdit, initialTeam]);

  const leadOptions = useMemo(() => {
    const base = [{ value: '', label: 'Unassigned (optional)' }];
    const items = employees.map((emp) => ({
      value: String(emp.id),
      label: `${emp.fullName} — ${ROLE_META[emp.role]?.shortLabel || emp.role} (${emp.employeeCode})`,
    }));

    // Preserve current lead in dropdown if not present in first 100 active employees
    if (
      isEdit &&
      initialTeam?.lead?.id &&
      !employees.some((e) => e.id === initialTeam.lead.id)
    ) {
      items.unshift({
        value: String(initialTeam.lead.id),
        label: `${initialTeam.lead.fullName} (Current Lead)`,
      });
    }
    return [...base, ...items];
  }, [employees, isEdit, initialTeam]);

  const filteredEmployees = useMemo(() => {
    const term = memberSearch.trim().toLowerCase();
    if (!term) return employees;
    return employees.filter(
      (emp) =>
        emp.fullName?.toLowerCase().includes(term) ||
        emp.email?.toLowerCase().includes(term) ||
        emp.employeeCode?.toLowerCase().includes(term)
    );
  }, [employees, memberSearch]);

  const toggleMember = (userId) => {
    setSelectedMemberIds((prev) =>
      prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId]
    );
  };

  const validate = () => {
    const errors = {};
    const trimmedName = name.trim();
    if (!trimmedName) {
      errors.name = 'Team name is required.';
    } else if (trimmedName.length < 2 || trimmedName.length > 100) {
      errors.name = 'Team name must be between 2 and 100 characters.';
    }

    if (description.trim().length > 2000) {
      errors.description = 'Description must not exceed 2000 characters.';
    }
    return errors;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;

    setFormError(null);
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);

    try {
      let saved;
      if (isEdit && initialTeam) {
        saved = await teamService.updateTeam(initialTeam.id, {
          name,
          description,
          leadUserId: leadUserId ? Number(leadUserId) : null,
          active,
          // Keep memberIds null so updateTeam does not overwrite existing membership roster
          memberIds: null,
        });
      } else {
        saved = await teamService.createTeam({
          name,
          description,
          leadUserId: leadUserId ? Number(leadUserId) : null,
          memberIds: selectedMemberIds,
        });
      }
      onSuccess?.(saved);
      onClose?.();
    } catch (err) {
      const parsed = parseApiError(err);
      if (Object.keys(parsed.fieldErrors).length > 0) {
        setFieldErrors(parsed.fieldErrors);
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
      title={isEdit ? 'Edit Team' : 'Create Team'}
      description={
        isEdit
          ? 'Update team details, lead assignment, or active status.'
          : 'Create a new engineering or delivery squad within your organization.'
      }
      size="lg"
      footer={
        <>
          <Button variant="ghost" size="sm" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="taskflow-team-form"
            variant="primary"
            size="sm"
            loading={submitting}
          >
            {isEdit ? 'Save Team' : 'Create Team'}
          </Button>
        </>
      }
    >
      <form id="taskflow-team-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        {formError && (
          <div
            role="alert"
            className="flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700"
          >
            <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" aria-hidden="true" />
            <span>{formError}</span>
          </div>
        )}

        <Input
          label="Team Name"
          name="name"
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            if (fieldErrors.name) setFieldErrors((p) => ({ ...p, name: undefined }));
          }}
          error={fieldErrors.name}
          placeholder="e.g. Platform Infrastructure"
          disabled={submitting}
          required
        />

        <Textarea
          label="Description"
          name="description"
          rows={3}
          value={description}
          onChange={(e) => {
            setDescription(e.target.value);
            if (fieldErrors.description) setFieldErrors((p) => ({ ...p, description: undefined }));
          }}
          error={fieldErrors.description}
          placeholder="Summarize this team's scope, owned services, or mission..."
          disabled={submitting}
        />

        <div>
          <Select
            label="Team Lead"
            name="leadUserId"
            value={leadUserId}
            onChange={(e) => setLeadUserId(e.target.value)}
            options={leadOptions}
            disabled={submitting || loadingEmployees}
            helperText={
              employeeLoadError
                ? `Could not load employees: ${employeeLoadError}`
                : 'Team leads must be active organization employees.'
            }
          />
        </div>

        {isEdit && (
          <div className="p-3.5 rounded-lg bg-subtle/70 border border-line">
            <Checkbox
              label="Team is active"
              description="Deactivated teams cannot accept new members or lead changes until reactivated."
              checked={active}
              disabled={submitting}
              onChange={(e) => setActive(e.target.checked)}
            />
          </div>
        )}

        {!isEdit && (
          <div className="space-y-2 pt-1">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-medium text-ink-muted">
                Initial Members <span className="text-ink-faint font-normal">(optional)</span>
              </span>
              <Badge tone="neutral" mono>
                {selectedMemberIds.length} selected
              </Badge>
            </div>

            <div className="relative">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-ink-faint"
                aria-hidden="true"
              />
              <input
                type="search"
                value={memberSearch}
                onChange={(e) => setMemberSearch(e.target.value)}
                placeholder="Filter active employees..."
                className="w-full h-9 rounded-lg bg-canvas border border-line pl-8 pr-3 text-xs text-ink placeholder:text-ink-faint focus:outline-none focus:border-brand-500"
              />
            </div>

            <div className="max-h-48 overflow-y-auto rounded-lg border border-line bg-subtle/60 divide-y divide-line">
              {loadingEmployees ? (
                <div className="py-6 flex items-center justify-center gap-2 text-xs text-ink-muted">
                  <Spinner size="sm" />
                  <span>Loading active employees...</span>
                </div>
              ) : filteredEmployees.length === 0 ? (
                <p className="py-6 text-center text-xs text-ink-faint">
                  No active employees match your filter.
                </p>
              ) : (
                filteredEmployees.map((emp) => {
                  const checked = selectedMemberIds.includes(emp.id);
                  const roleInfo = ROLE_META[emp.role];
                  return (
                    <label
                      key={emp.id}
                      className="flex items-center gap-3 px-3.5 py-2.5 hover:bg-subtle cursor-pointer"
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={submitting}
                        onChange={() => toggleMember(emp.id)}
                        className="h-4 w-4 rounded border-line-strong accent-brand-500"
                      />
                      <Avatar name={emp.fullName} color={emp.avatarColor} size="xs" />
                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-medium text-ink truncate">
                          {emp.fullName}
                        </p>
                        <p className="text-[11px] text-ink-muted truncate">{emp.email}</p>
                      </div>
                      <Badge tone={roleInfo?.tone || 'neutral'}>
                        {roleInfo?.shortLabel || emp.role}
                      </Badge>
                    </label>
                  );
                })
              )}
            </div>
          </div>
        )}
      </form>
    </Modal>
  );
}
