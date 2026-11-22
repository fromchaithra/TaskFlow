import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, Search } from 'lucide-react';
import { teamService } from '../../services/teamService';
import { userService } from '../../services/userService';
import { ROLE_META } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { ErrorState, Spinner } from '../../components/common/Loading';
import { Modal } from '../../components/common/Modal';

export function AddTeamMemberModal({
  isOpen,
  onClose,
  teamId,
  teamName,
  existingMemberIds = [],
  onSuccess,
}) {
  const [employees, setEmployees] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);

  const [search, setSearch] = useState('');
  const [selectedUserId, setSelectedUserId] = useState(null);
  const [submitError, setSubmitError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const loadCandidates = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await userService.listUsers({
        active: true,
        page: 0,
        size: 100,
        sortBy: 'fullName',
        sortDir: 'asc',
      });
      setEmployees(res.content || []);
    } catch (err) {
      setLoadError(parseApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isOpen) return;
    setSearch('');
    setSelectedUserId(null);
    setSubmitError(null);
    loadCandidates();
  }, [isOpen]);

  const existingSet = useMemo(() => new Set(existingMemberIds), [existingMemberIds]);

  const candidates = useMemo(() => {
    const term = search.trim().toLowerCase();
    return employees
      .filter((emp) => !existingSet.has(emp.id))
      .filter((emp) => {
        if (!term) return true;
        return (
          emp.fullName?.toLowerCase().includes(term) ||
          emp.email?.toLowerCase().includes(term) ||
          emp.employeeCode?.toLowerCase().includes(term)
        );
      });
  }, [employees, existingSet, search]);

  const handleSubmit = async () => {
    if (!selectedUserId || submitting) return;
    setSubmitError(null);
    setSubmitting(true);
    try {
      const updatedTeam = await teamService.addMember(teamId, selectedUserId);
      onSuccess?.(updatedTeam);
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
      title="Add Team Member"
      description={`Select an active organization employee to join ${teamName || 'this team'}.`}
      size="md"
      footer={
        <>
          <Button variant="ghost" size="sm" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            disabled={!selectedUserId}
            loading={submitting}
            onClick={handleSubmit}
          >
            Add to Team
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {submitError && (
          <div
            role="alert"
            className="flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-700"
          >
            <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" aria-hidden="true" />
            <span>{submitError}</span>
          </div>
        )}

        <div className="relative">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-ink-faint"
            aria-hidden="true"
          />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search active employees by name, email, or code..."
            className="w-full h-9 rounded-lg bg-canvas border border-line pl-9 pr-3 text-xs text-ink placeholder:text-ink-faint focus:outline-none focus:border-brand-500"
          />
        </div>

        {loading ? (
          <div className="py-10 flex items-center justify-center gap-2 text-xs text-ink-muted">
            <Spinner size="sm" />
            <span>Loading available employees...</span>
          </div>
        ) : loadError ? (
          <ErrorState
            title="Could not load employees"
            description={loadError.message}
            onRetry={loadCandidates}
          />
        ) : candidates.length === 0 ? (
          <div className="py-8 text-center rounded-lg border border-dashed border-line bg-subtle/40 px-4">
            <p className="text-xs font-medium text-ink-muted">No eligible employees available</p>
            <p className="text-[11px] text-ink-faint mt-1">
              {search.trim()
                ? 'No active employees outside this team match your search.'
                : 'All active organization employees are already assigned to this team.'}
            </p>
          </div>
        ) : (
          <div className="max-h-64 overflow-y-auto rounded-lg border border-line bg-subtle/60 divide-y divide-line">
            {candidates.map((emp) => {
              const selected = selectedUserId === emp.id;
              const roleInfo = ROLE_META[emp.role];
              return (
                <label
                  key={emp.id}
                  className={[
                    'flex items-center gap-3 px-3.5 py-2.5 cursor-pointer transition-colors',
                    selected ? 'bg-brand-500/10' : 'hover:bg-subtle',
                  ].join(' ')}
                >
                  <input
                    type="radio"
                    name="memberUserId"
                    value={emp.id}
                    checked={selected}
                    disabled={submitting}
                    onChange={() => setSelectedUserId(emp.id)}
                    className="accent-brand-500"
                  />
                  <Avatar name={emp.fullName} color={emp.avatarColor} size="sm" />
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-medium text-ink truncate">{emp.fullName}</p>
                    <p className="text-[11px] text-ink-muted truncate">
                      {emp.email} · <span className="font-mono">{emp.employeeCode}</span>
                    </p>
                  </div>
                  <Badge tone={roleInfo?.tone || 'neutral'}>
                    {roleInfo?.shortLabel || emp.role}
                  </Badge>
                </label>
              );
            })}
          </div>
        )}
      </div>
    </Modal>
  );
}
