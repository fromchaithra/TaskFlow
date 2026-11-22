import { ShieldCheck, Trash2, UserCog, Users } from 'lucide-react';
import { Avatar } from '../../../components/common/Avatar';
import { Badge } from '../../../components/common/Badge';
import { Button } from '../../../components/common/Button';
import { DataTable } from '../../../components/common/DataTable';
import { ROLE_META } from '../../../utils/constants';
import { formatDateTime } from '../../../utils/formatters';

/**
 * Reusable project members table mapped to ProjectMemberResponse:
 * { id, user: UserSummaryDto, projectRole: RoleName, addedBy: UserSummaryDto, joinedAt }
 */
export function ProjectMemberList({
  members = [],
  managerId = null,
  canManage = false,
  isArchived = false,
  loading = false,
  error = null,
  onRetry = null,
  onEditRole = null,
  onRemoveMember = null,
  emptyAction = null,
}) {
  const showActions = canManage && !isArchived;

  const columns = [
    {
      key: 'member',
      label: 'Member',
      render: (row) => {
        const user = row.user || {};
        const isProjectManager = managerId != null && user.id === managerId;
        return (
          <div className="flex items-center gap-3 min-w-[210px]">
            <Avatar name={user.fullName} color={user.avatarColor} size="sm" />
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-sm font-medium text-ink truncate">
                  {user.fullName || 'Unknown user'}
                </span>
                {isProjectManager && (
                  <Badge tone="cyan">
                    <ShieldCheck className="h-3 w-3" aria-hidden="true" />
                    Manager
                  </Badge>
                )}
              </div>
              <div className="flex items-center gap-2 text-xs text-ink-muted mt-0.5">
                <span className="truncate">{user.email || 'No email'}</span>
                {user.employeeCode && (
                  <>
                    <span className="text-ink-faint" aria-hidden="true">
                      •
                    </span>
                    <span className="font-mono text-[11px] text-ink-faint shrink-0">
                      {user.employeeCode}
                    </span>
                  </>
                )}
              </div>
            </div>
          </div>
        );
      },
    },
    {
      key: 'projectRole',
      label: 'Project Role',
      render: (row) => {
        const roleMeta = ROLE_META[row.projectRole] || {
          label: row.projectRole || 'Unknown',
          tone: 'neutral',
        };
        return (
          <Badge tone={roleMeta.tone} dot>
            {roleMeta.label}
          </Badge>
        );
      },
    },
    {
      key: 'jobTitle',
      label: 'Title / Org Role',
      render: (row) => {
        const user = row.user || {};
        const orgRoleMeta = ROLE_META[user.role];
        return (
          <div className="min-w-[140px]">
            <p className="text-xs text-ink truncate">
              {user.jobTitle || orgRoleMeta?.label || '—'}
            </p>
            {user.jobTitle && orgRoleMeta && (
              <p className="text-[11px] text-ink-faint truncate mt-0.5">{orgRoleMeta.label}</p>
            )}
          </div>
        );
      },
    },
    {
      key: 'addedBy',
      label: 'Added By',
      render: (row) =>
        row.addedBy ? (
          <div className="flex items-center gap-2 min-w-[130px]">
            <Avatar name={row.addedBy.fullName} color={row.addedBy.avatarColor} size="xs" />
            <span className="text-xs text-ink-muted truncate">{row.addedBy.fullName}</span>
          </div>
        ) : (
          <span className="text-xs text-ink-faint">System</span>
        ),
    },
    {
      key: 'joinedAt',
      label: 'Joined',
      render: (row) => (
        <span className="text-xs text-ink-muted whitespace-nowrap tabular-nums">
          {formatDateTime(row.joinedAt)}
        </span>
      ),
    },
  ];

  if (showActions) {
    columns.push({
      key: 'actions',
      label: 'Actions',
      align: 'right',
      render: (row) => {
        const user = row.user || {};
        const isProjectManager = managerId != null && user.id === managerId;

        return (
          <div className="inline-flex items-center justify-end gap-1.5">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onEditRole?.(row)}
              leftIcon={<UserCog className="h-3.5 w-3.5" />}
            >
              Role
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={isProjectManager}
              title={
                isProjectManager
                  ? 'Reassign the project manager in Settings before removing this member.'
                  : 'Remove member from project'
              }
              onClick={() => onRemoveMember?.(row)}
              className="text-red-600 hover:text-red-600 hover:bg-red-50 disabled:text-ink-faint"
              leftIcon={<Trash2 className="h-3.5 w-3.5" />}
            >
              Remove
            </Button>
          </div>
        );
      },
    });
  }

  return (
    <DataTable
      columns={columns}
      data={members}
      rowKey={(row) => row.id ?? row.user?.id}
      loading={loading}
      skeletonRows={5}
      error={error}
      onRetry={onRetry}
      emptyState={{
        icon: Users,
        title: 'No matching project members',
        description:
          members.length === 0
            ? 'Add active organization employees to grant them access to this project.'
            : 'No project members match your search query.',
        action: emptyAction,
      }}
    />
  );
}
