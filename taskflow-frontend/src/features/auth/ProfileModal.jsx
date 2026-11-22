import { useState } from 'react';
import { Building2, Clock, Hash, Mail, Pencil, RefreshCw, Shield, Users } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { ROLE_META } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateTime } from '../../utils/formatters';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Modal } from '../../components/common/Modal';
import { UserFormModal } from '../users/UserFormModal';

export function ProfileModal({ isOpen, onClose }) {
  const { user, refreshCurrentUser } = useAuth();
  const toast = useToast();
  const [syncing, setSyncing] = useState(false);
  const [editOpen, setEditOpen] = useState(false);

  if (!user) return null;

  const roleInfo = ROLE_META[user.role] || { label: user.role, tone: 'neutral' };
  const teams = Array.isArray(user.teams) ? user.teams : [];

  const handleSyncProfile = async () => {
    setSyncing(true);
    try {
      await refreshCurrentUser();
      toast.success('Profile details refreshed from server.', 'Profile synced');
    } catch (err) {
      const parsed = parseApiError(err);
      toast.error(parsed.message, 'Unable to refresh profile');
    } finally {
      setSyncing(false);
    }
  };

  const fields = [
    { icon: Mail, label: 'Work Email', value: user.email, mono: false },
    { icon: Hash, label: 'Employee Code', value: user.employeeCode || 'Not assigned', mono: true },
    { icon: Building2, label: 'Organization', value: user.organizationName || 'Not assigned', mono: false },
    { icon: Shield, label: 'Job Title', value: user.jobTitle || 'Not specified', mono: false },
    { icon: Clock, label: 'Last Login', value: formatDateTime(user.lastLoginAt), mono: true },
  ];

  return (
    <>
      <Modal
        isOpen={isOpen && !editOpen}
        onClose={onClose}
        title="Account Profile"
        description="Authenticated member details retrieved from your TaskFlow workspace."
        size="md"
        footer={
          <div className="w-full flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setEditOpen(true)}
                leftIcon={<Pencil className="h-3.5 w-3.5" />}
              >
                Edit Profile
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleSyncProfile}
                loading={syncing}
                leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
              >
                Refresh
              </Button>
            </div>
            <Button variant="primary" size="sm" onClick={onClose}>
              Done
            </Button>
          </div>
        }
      >
        <div className="space-y-5">
          {/* Identity strip */}
          <div className="flex items-center gap-3.5 pb-5 border-b border-line">
            <Avatar name={user.fullName} color={user.avatarColor} size="lg" />
            <div className="min-w-0 flex-1">
              <h3 className="text-base font-semibold text-ink truncate">{user.fullName}</h3>
              <div className="flex items-center gap-2 flex-wrap mt-1.5">
                <Badge tone={roleInfo.tone} dot>
                  {roleInfo.label}
                </Badge>
                <Badge tone={user.active ? 'emerald' : 'red'}>
                  {user.active ? 'Active' : 'Inactive'}
                </Badge>
              </div>
            </div>
          </div>

          {/* Structured AuthUserDto metadata */}
          <dl className="divide-y divide-line rounded-lg border border-line bg-subtle/50">
            {fields.map((field) => {
              const Icon = field.icon;
              return (
                <div
                  key={field.label}
                  className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1 px-4 py-2.5"
                >
                  <dt className="flex items-center gap-2 text-xs text-ink-muted">
                    <Icon className="h-3.5 w-3.5 text-ink-faint shrink-0" aria-hidden="true" />
                    <span>{field.label}</span>
                  </dt>
                  <dd
                    className={[
                      'text-xs text-ink sm:text-right break-all',
                      field.mono ? 'font-mono tabular-nums' : 'font-medium',
                    ].join(' ')}
                  >
                    {field.value}
                  </dd>
                </div>
              );
            })}
          </dl>

          {/* Team assignments */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-xs font-medium text-ink-muted">
              <Users className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
              <span>Assigned Teams</span>
            </div>
            {teams.length > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {teams.map((teamName) => (
                  <Badge key={teamName} tone="neutral">
                    {teamName}
                  </Badge>
                ))}
              </div>
            ) : (
              <p className="text-xs text-ink-faint">
                No team assignments are currently linked to this account.
              </p>
            )}
          </div>
        </div>
      </Modal>

      <UserFormModal
        isOpen={editOpen}
        onClose={() => setEditOpen(false)}
        mode="edit"
        initialUser={user}
        onSuccess={async () => {
          toast.success('Your profile has been updated.', 'Profile saved');
          try {
            await refreshCurrentUser();
          } catch {
            // Ignore silent sync errors; user can refresh manually.
          }
        }}
      />
    </>
  );
}
