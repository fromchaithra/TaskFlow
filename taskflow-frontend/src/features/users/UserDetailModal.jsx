import { useCallback, useEffect, useState } from 'react';
import {
  Briefcase,
  CalendarDays,
  Clock,
  Hash,
  Mail,
  Pencil,
  Power,
  Shield,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { userService } from '../../services/userService';
import { ROLE_META, ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateTime } from '../../utils/formatters';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { Modal } from '../../components/common/Modal';

export function UserDetailModal({
  isOpen,
  onClose,
  selectedUser,
  onEdit,
  onChangeRole,
  onToggleStatus,
}) {
  const { user: currentUser, hasRole } = useAuth();
  const isAdmin = hasRole(ROLES.ADMIN);
  const isSelf = Boolean(currentUser?.id && selectedUser?.id && currentUser.id === selectedUser.id);
  const canInspectEndpoint = isAdmin || isSelf;

  const [detail, setDetail] = useState(selectedUser || null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchDetail = useCallback(async () => {
    if (!selectedUser?.id) return;
    if (!canInspectEndpoint) {
      // Backend GET /api/v1/users/{id} allows ADMIN or self only.
      // For PROJECT_MANAGER inspecting another employee, display the record returned by GET /api/v1/users.
      setDetail(selectedUser);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const fresh = await userService.getUserById(selectedUser.id);
      setDetail(fresh);
    } catch (err) {
      setError(parseApiError(err));
    } finally {
      setLoading(false);
    }
  }, [selectedUser, canInspectEndpoint]);

  useEffect(() => {
    if (isOpen && selectedUser) {
      setDetail(selectedUser);
      fetchDetail();
    }
  }, [isOpen, selectedUser, fetchDetail]);

  const record = detail || selectedUser;
  if (!record) return null;

  const roleInfo = ROLE_META[record.role] || { label: record.role, tone: 'neutral' };

  const metadataRows = [
    { icon: Mail, label: 'Work Email', value: record.email, mono: false },
    { icon: Hash, label: 'Employee Code', value: record.employeeCode || '—', mono: true },
    { icon: Briefcase, label: 'Job Title', value: record.jobTitle || 'Not specified', mono: false },
    { icon: Clock, label: 'Last Login', value: formatDateTime(record.lastLoginAt), mono: true },
    { icon: CalendarDays, label: 'Created', value: formatDateTime(record.createdAt), mono: true },
    { icon: CalendarDays, label: 'Last Updated', value: formatDateTime(record.updatedAt), mono: true },
  ];

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Employee Details"
      description="Organization member record and permission metadata."
      size="md"
      footer={
        <div className="w-full flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            {canInspectEndpoint && onEdit && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  onClose();
                  onEdit(record);
                }}
                leftIcon={<Pencil className="h-3.5 w-3.5" />}
              >
                Edit
              </Button>
            )}
            {isAdmin && onChangeRole && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  onClose();
                  onChangeRole(record);
                }}
                leftIcon={<Shield className="h-3.5 w-3.5" />}
              >
                Change Role
              </Button>
            )}
            {isAdmin && !isSelf && onToggleStatus && (
              <Button
                variant={record.active ? 'danger' : 'outline'}
                size="sm"
                onClick={() => {
                  onClose();
                  onToggleStatus(record);
                }}
                leftIcon={<Power className="h-3.5 w-3.5" />}
              >
                {record.active ? 'Deactivate' : 'Activate'}
              </Button>
            )}
          </div>
          <Button variant="primary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      }
    >
      {loading && !detail ? (
        <div className="space-y-4">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      ) : error ? (
        <ErrorState
          title="Could not load full employee record"
          description={error.message}
          onRetry={fetchDetail}
        />
      ) : (
        <div className="space-y-5">
          <div className="flex items-center gap-3.5 pb-4 border-b border-line">
            <Avatar name={record.fullName} color={record.avatarColor} size="lg" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <h3 className="text-base font-semibold text-ink truncate">
                  {record.fullName}
                </h3>
                {isSelf && <Badge tone="cyan">You</Badge>}
              </div>
              <div className="flex items-center gap-2 flex-wrap mt-1.5">
                <Badge tone={roleInfo.tone} dot>
                  {roleInfo.label}
                </Badge>
                <Badge tone={record.active ? 'emerald' : 'red'}>
                  {record.active ? 'Active' : 'Deactivated'}
                </Badge>
              </div>
            </div>
          </div>

          <dl className="divide-y divide-line rounded-lg border border-line bg-subtle/50">
            {metadataRows.map((row) => {
              const Icon = row.icon;
              return (
                <div
                  key={row.label}
                  className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1 px-4 py-2.5"
                >
                  <dt className="flex items-center gap-2 text-xs text-ink-muted">
                    <Icon className="h-3.5 w-3.5 text-ink-faint shrink-0" aria-hidden="true" />
                    <span>{row.label}</span>
                  </dt>
                  <dd
                    className={[
                      'text-xs text-ink sm:text-right break-all',
                      row.mono ? 'font-mono tabular-nums' : 'font-medium',
                    ].join(' ')}
                  >
                    {row.value}
                  </dd>
                </div>
              );
            })}
          </dl>
        </div>
      )}
    </Modal>
  );
}
