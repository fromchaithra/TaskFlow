import { useEffect, useMemo, useState } from 'react';
import { Check, Search, UserPlus } from 'lucide-react';
import { Avatar } from '../../../components/common/Avatar';
import { Badge } from '../../../components/common/Badge';
import { Button } from '../../../components/common/Button';
import { Select } from '../../../components/common/FormControls';
import { Input } from '../../../components/common/Input';
import { Spinner } from '../../../components/common/Loading';
import { Modal } from '../../../components/common/Modal';
import { projectService } from '../../../services/projectService';
import { userService } from '../../../services/userService';
import { ALL_ROLES, ROLE_META, ROLES } from '../../../utils/constants';
import { parseApiError } from '../../../utils/errorHandler';

const ROLE_OPTIONS = ALL_ROLES.map((role) => ({
  value: role,
  label: ROLE_META[role]?.label || role,
}));

const MANAGER_ALLOWED_ROLES = [ROLES.ADMIN, ROLES.PROJECT_MANAGER];

/**
 * Modal for POST /api/v1/projects/{id}/members
 * Request DTO: AddProjectMemberRequest { userId (required), projectRole (optional RoleName) }
 */
export function AddProjectMemberModal({
  isOpen,
  onClose,
  projectId,
  existingMemberUserIds = [],
  onMemberAdded,
}) {
  const [employees, setEmployees] = useState([]);
  const [loadingEmployees, setLoadingEmployees] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedUserId, setSelectedUserId] = useState('');
  const [projectRole, setProjectRole] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');

  const existingSet = useMemo(
    () => new Set(existingMemberUserIds.map(Number)),
    [existingMemberUserIds]
  );

  useEffect(() => {
    if (!isOpen) return undefined;

    let cancelled = false;
    setSearchQuery('');
    setSelectedUserId('');
    setProjectRole('');
    setSubmitError('');
    setLoadError('');
    setLoadingEmployees(true);

    userService
      .listUsers({ active: true, page: 0, size: 100, sortBy: 'fullName', sortDir: 'asc' })
      .then((data) => {
        if (cancelled) return;
        setEmployees(Array.isArray(data?.content) ? data.content : []);
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(parseApiError(err).message);
      })
      .finally(() => {
        if (!cancelled) setLoadingEmployees(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isOpen]);

  const availableEmployees = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    return employees
      .filter((emp) => !existingSet.has(Number(emp.id)))
      .filter((emp) => {
        if (!query) return true;
        return (
          emp.fullName?.toLowerCase().includes(query) ||
          emp.email?.toLowerCase().includes(query) ||
          emp.employeeCode?.toLowerCase().includes(query) ||
          emp.jobTitle?.toLowerCase().includes(query)
        );
      });
  }, [employees, existingSet, searchQuery]);

  const selectedEmployee = useMemo(
    () => employees.find((emp) => String(emp.id) === String(selectedUserId)) || null,
    [employees, selectedUserId]
  );

  const handleSelectEmployee = (emp) => {
    setSelectedUserId(String(emp.id));
    setProjectRole(emp.role || '');
    setSubmitError('');
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!selectedUserId) {
      setSubmitError('Select an organization employee to add.');
      return;
    }

    setSubmitting(true);
    setSubmitError('');
    try {
      const added = await projectService.addProjectMember(projectId, {
        userId: Number(selectedUserId),
        projectRole: projectRole || null,
      });
      onMemberAdded?.(added);
      onClose?.();
    } catch (err) {
      setSubmitError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? undefined : onClose}
      closeOnBackdrop={!submitting}
      title="Add Project Member"
      description="Grant an active organization employee access to this project and assign their project role."
      size="md"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="add-project-member-form"
            loading={submitting}
            disabled={!selectedUserId || loadingEmployees}
            leftIcon={<UserPlus className="h-4 w-4" />}
          >
            Add Member
          </Button>
        </>
      }
    >
      <form id="add-project-member-form" onSubmit={handleSubmit} className="space-y-4">
        {submitError && (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-600"
          >
            {submitError}
          </div>
        )}

        <Input
          label="Search Active Employees"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Filter by name, email, employee code, or title..."
          leftIcon={<Search className="h-4 w-4" />}
          disabled={loadingEmployees || submitting}
        />

        <div className="rounded-lg border border-line bg-canvas overflow-hidden">
          {loadingEmployees ? (
            <div className="flex items-center justify-center gap-2 py-8 text-xs text-ink-muted">
              <Spinner size="sm" />
              <span>Loading active organization employees...</span>
            </div>
          ) : loadError ? (
            <div className="p-4 text-xs text-red-600">{loadError}</div>
          ) : availableEmployees.length === 0 ? (
            <div className="p-5 text-center text-xs text-ink-muted">
              {searchQuery.trim()
                ? 'No available employees match your search.'
                : 'All active organization employees are already members of this project.'}
            </div>
          ) : (
            <ul className="max-h-56 overflow-y-auto divide-y divide-line">
              {availableEmployees.map((emp) => {
                const isSelected = String(emp.id) === String(selectedUserId);
                const roleMeta = ROLE_META[emp.role] || { label: emp.role, tone: 'neutral' };
                return (
                  <li key={emp.id}>
                    <button
                      type="button"
                      onClick={() => handleSelectEmployee(emp)}
                      className={[
                        'w-full px-3.5 py-2.5 text-left flex items-center justify-between gap-3 transition-colors',
                        isSelected
                          ? 'bg-brand-500/12 text-ink'
                          : 'hover:bg-subtle text-ink',
                      ].join(' ')}
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <Avatar name={emp.fullName} color={emp.avatarColor} size="sm" />
                        <div className="min-w-0">
                          <p className="text-sm font-medium truncate">{emp.fullName}</p>
                          <p className="text-xs text-ink-muted truncate">
                            {emp.email}
                            {emp.employeeCode ? ` · ${emp.employeeCode}` : ''}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <Badge tone={roleMeta.tone}>{roleMeta.shortLabel || roleMeta.label}</Badge>
                        {isSelected && (
                          <span className="h-5 w-5 rounded-full bg-brand-500 text-white flex items-center justify-center">
                            <Check className="h-3 w-3 stroke-[3]" aria-hidden="true" />
                          </span>
                        )}
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <Select
          label="Project Role"
          value={projectRole}
          onChange={(e) => setProjectRole(e.target.value)}
          options={ROLE_OPTIONS}
          placeholder="Select project role..."
          disabled={submitting || !selectedEmployee}
          helperText={
            selectedEmployee
              ? `Defaults to ${selectedEmployee.fullName}'s organization role (${ROLE_META[selectedEmployee.role]?.label || selectedEmployee.role}).`
              : 'Select an employee above to assign their role on this project.'
          }
        />
      </form>
    </Modal>
  );
}

/**
 * Modal for PATCH /api/v1/projects/{id}/members/{userId}/role
 * Request DTO: UpdateProjectMemberRoleRequest { projectRole (required RoleName) }
 */
export function EditProjectMemberRoleModal({
  isOpen,
  onClose,
  projectId,
  member,
  isProjectManager = false,
  onRoleUpdated,
}) {
  const [projectRole, setProjectRole] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen && member) {
      setProjectRole(member.projectRole || '');
      setError('');
    }
  }, [isOpen, member]);

  const roleOptions = useMemo(() => {
    if (!isProjectManager) return ROLE_OPTIONS;
    return ROLE_OPTIONS.filter((opt) => MANAGER_ALLOWED_ROLES.includes(opt.value));
  }, [isProjectManager]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!member?.user?.id || !projectRole) return;

    setSubmitting(true);
    setError('');
    try {
      const updated = await projectService.updateProjectMemberRole(
        projectId,
        member.user.id,
        projectRole
      );
      onRoleUpdated?.(updated);
      onClose?.();
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  };

  const user = member?.user || {};

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? undefined : onClose}
      closeOnBackdrop={!submitting}
      title="Update Project Role"
      description="Change this member's role within the current project without changing their organization role."
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="edit-project-member-role-form"
            loading={submitting}
            disabled={!projectRole || projectRole === member?.projectRole}
          >
            Save Role
          </Button>
        </>
      }
    >
      <form id="edit-project-member-role-form" onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-600"
          >
            {error}
          </div>
        )}

        {member && (
          <div className="flex items-center gap-3 p-3 rounded-lg bg-canvas border border-line">
            <Avatar name={user.fullName} color={user.avatarColor} size="sm" />
            <div className="min-w-0">
              <p className="text-sm font-medium text-ink truncate">{user.fullName}</p>
              <p className="text-xs text-ink-muted truncate">{user.email}</p>
            </div>
          </div>
        )}

        <Select
          label="Project Role"
          required
          value={projectRole}
          onChange={(e) => {
            setProjectRole(e.target.value);
            setError('');
          }}
          options={roleOptions}
          disabled={submitting}
          helperText={
            isProjectManager
              ? 'The assigned project manager must hold Administrator or Project Manager role on the project.'
              : 'Applies strictly to this project membership.'
          }
        />
      </form>
    </Modal>
  );
}
