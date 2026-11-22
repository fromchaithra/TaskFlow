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

export function ChangeTeamLeadModal({
  isOpen,
  onClose,
  teamId,
  teamName,
  currentLead = null,
  onSuccess,
}) {
  const [employees, setEmployees] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);

  const [search, setSearch] = useState('');
  const [selectedLeadId, setSelectedLeadId] = useState(currentLead?.id || null);
  const [submitError, setSubmitError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const loadEmployees = async () => {
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
    setSelectedLeadId(currentLead?.id || null);
    setSubmitError(null);
    loadEmployees();
  }, [isOpen, currentLead?.id]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return employees;
    return employees.filter(
      (emp) =>
        emp.fullName?.toLowerCase().includes(term) ||
        emp.email?.toLowerCase().includes(term) ||
        emp.employeeCode?.toLowerCase().includes(term)
    );
  }, [employees, search]);

  const unchanged = Boolean(currentLead?.id && selectedLeadId === currentLead.id);

  const handleSubmit = async () => {
    if (!selectedLeadId || unchanged || submitting) return;
    setSubmitError(null);
    setSubmitting(true);
    try {
      const updated = await teamService.updateTeamLead(teamId, selectedLeadId);
      onSuccess?.(updated);
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
      title="Change Team Lead"
      description={`Assign an active employee as lead for ${teamName || 'this team'}.`}
      size="md"
      footer={
        <>
          <Button variant="ghost" size="sm" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            disabled={!selectedLeadId || unchanged}
            loading={submitting}
            onClick={handleSubmit}
          >
            Assign Team Lead
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
            placeholder="Search active employees..."
            className="w-full h-9 rounded-lg bg-canvas border border-line pl-9 pr-3 text-xs text-ink placeholder:text-ink-faint focus:outline-none focus:border-brand-500"
          />
        </div>

        {loading ? (
          <div className="py-10 flex items-center justify-center gap-2 text-xs text-ink-muted">
            <Spinner size="sm" />
            <span>Loading active employees...</span>
          </div>
        ) : loadError ? (
          <ErrorState
            title="Could not load employees"
            description={loadError.message}
            onRetry={loadEmployees}
          />
        ) : filtered.length === 0 ? (
          <p className="py-8 text-center text-xs text-ink-faint">
            No active employees match your search.
          </p>
        ) : (
          <div className="max-h-64 overflow-y-auto rounded-lg border border-line bg-subtle/60 divide-y divide-line">
            {filtered.map((emp) => {
              const selected = selectedLeadId === emp.id;
              const isCurrent = currentLead?.id === emp.id;
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
                    name="leadUserId"
                    value={emp.id}
                    checked={selected}
                    disabled={submitting}
                    onChange={() => setSelectedLeadId(emp.id)}
                    className="accent-brand-500"
                  />
                  <Avatar name={emp.fullName} color={emp.avatarColor} size="sm" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="text-xs font-medium text-ink truncate">
                        {emp.fullName}
                      </p>
                      {isCurrent && <Badge tone="cyan">Current Lead</Badge>}
                    </div>
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
