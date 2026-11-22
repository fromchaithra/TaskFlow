import { useEffect, useState } from 'react';
import { AlertCircle, ShieldAlert } from 'lucide-react';
import { userService } from '../../services/userService';
import { ALL_ROLES, ROLE_META } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Modal } from '../../components/common/Modal';

export function ChangeRoleModal({ isOpen, onClose, targetUser, onSuccess }) {
  const [selectedRole, setSelectedRole] = useState(targetUser?.role || 'DEVELOPER');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (isOpen && targetUser) {
      setSelectedRole(targetUser.role || 'DEVELOPER');
      setError(null);
    }
  }, [isOpen, targetUser]);

  if (!targetUser) return null;

  const unchanged = selectedRole === targetUser.role;

  const handleConfirm = async () => {
    if (submitting || unchanged) return;
    setError(null);
    setSubmitting(true);
    try {
      const updated = await userService.updateUserRole(targetUser.id, selectedRole);
      onSuccess?.(updated);
      onClose?.();
    } catch (err) {
      const parsed = parseApiError(err);
      setError(parsed.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? undefined : onClose}
      closeOnBackdrop={!submitting}
      title="Change Employee Role"
      description="Reassigning a workspace role updates permissions immediately."
      size="sm"
      footer={
        <>
          <Button variant="ghost" size="sm" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            disabled={unchanged}
            loading={submitting}
            onClick={handleConfirm}
          >
            Update Role
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center gap-3 p-3 rounded-lg bg-subtle/70 border border-line">
          <Avatar name={targetUser.fullName} color={targetUser.avatarColor} size="md" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-ink truncate">{targetUser.fullName}</p>
            <p className="text-xs text-ink-muted truncate">{targetUser.email}</p>
          </div>
        </div>

        {error && (
          <div
            role="alert"
            className="flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-700"
          >
            <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}

        <fieldset className="space-y-2">
          <legend className="text-xs font-medium text-ink-muted mb-1.5">Select Role</legend>
          {ALL_ROLES.map((role) => {
            const meta = ROLE_META[role];
            const isSelected = selectedRole === role;
            const isCurrent = targetUser.role === role;
            return (
              <label
                key={role}
                className={[
                  'flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors',
                  isSelected
                    ? 'bg-brand-500/10 border-brand-500/45'
                    : 'bg-canvas border-line hover:border-line-strong',
                ].join(' ')}
              >
                <input
                  type="radio"
                  name="roleName"
                  value={role}
                  checked={isSelected}
                  disabled={submitting}
                  onChange={() => setSelectedRole(role)}
                  className="mt-1 accent-brand-500"
                />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-ink">{meta.label}</span>
                    {isCurrent && <Badge tone="neutral">Current</Badge>}
                  </div>
                  <p className="text-[11px] text-ink-muted mt-0.5 leading-relaxed">
                    {meta.description}
                  </p>
                </div>
              </label>
            );
          })}
        </fieldset>

        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-500/10 px-3 py-2.5 text-[11px] text-amber-700 leading-relaxed">
          <ShieldAlert className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" aria-hidden="true" />
          <span>
            For security, changing an employee&apos;s role revokes all of their active refresh
            sessions and requires them to sign in again.
          </span>
        </div>
      </div>
    </Modal>
  );
}
