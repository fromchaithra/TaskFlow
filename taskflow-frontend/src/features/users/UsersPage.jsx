import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Eye,
  Pencil,
  Power,
  RefreshCw,
  Search,
  Shield,
  UserPlus,
  Users,
  X,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useDebounce } from '../../hooks/useDebounce';
import { useToast } from '../../hooks/useToast';
import { userService } from '../../services/userService';
import { ALL_ROLES, ROLE_META, ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateTime } from '../../utils/formatters';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { ConfirmDialog } from '../../components/common/ConfirmDialog';
import { DataTable } from '../../components/common/DataTable';
import { Input } from '../../components/common/Input';
import { PageHeader } from '../../components/common/PageHeader';
import { Pagination } from '../../components/common/Pagination';
import { ChangeRoleModal } from './ChangeRoleModal';
import { UserDetailModal } from './UserDetailModal';
import { UserFormModal } from './UserFormModal';

const ALLOWED_SORTS = new Set(['createdAt', 'fullName', 'email', 'employeeCode']);

export function UsersPage() {
  const { user: currentUser, hasRole, refreshCurrentUser } = useAuth();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const isAdmin = hasRole(ROLES.ADMIN);

  // URL-synced query state
  const urlSearch = searchParams.get('search') || '';
  const urlRole = searchParams.get('role') || '';
  const urlActive = isAdmin ? searchParams.get('active') || '' : '';
  const urlPage = Math.max(Number.parseInt(searchParams.get('page') || '0', 10) || 0, 0);
  const urlSize = [10, 20, 50].includes(Number(searchParams.get('size')))
    ? Number(searchParams.get('size'))
    : 20;
  const urlSortBy = ALLOWED_SORTS.has(searchParams.get('sortBy'))
    ? searchParams.get('sortBy')
    : 'createdAt';
  const urlSortDir = searchParams.get('sortDir') === 'asc' ? 'asc' : 'desc';

  const [searchInput, setSearchInput] = useState(urlSearch);
  const debouncedSearch = useDebounce(searchInput, 300);

  const [pageData, setPageData] = useState({
    content: [],
    page: 0,
    size: urlSize,
    totalElements: 0,
    totalPages: 0,
    last: true,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Modals & confirmation state
  const [createOpen, setCreateOpen] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [viewingUser, setViewingUser] = useState(null);
  const [roleTarget, setRoleTarget] = useState(null);
  const [statusTarget, setStatusTarget] = useState(null);
  const [statusUpdating, setStatusUpdating] = useState(false);

  const updateParams = useCallback(
    (updates) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          for (const [key, val] of Object.entries(updates)) {
            if (val === undefined || val === null || val === '') {
              next.delete(key);
            } else {
              next.set(key, String(val));
            }
          }
          return next;
        },
        { replace: true }
      );
    },
    [setSearchParams]
  );

  // Push debounced search text into URL params
  useEffect(() => {
    if (debouncedSearch !== urlSearch) {
      updateParams({ search: debouncedSearch, page: 0 });
    }
  }, [debouncedSearch, urlSearch, updateParams]);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const activeParam =
        isAdmin && (urlActive === 'true' || urlActive === 'false')
          ? urlActive === 'true'
          : undefined;

      const response = await userService.listUsers({
        search: urlSearch || undefined,
        role: urlRole || undefined,
        active: activeParam,
        page: urlPage,
        size: urlSize,
        sortBy: urlSortBy,
        sortDir: urlSortDir,
      });
      setPageData(response);
    } catch (err) {
      setError(parseApiError(err));
    } finally {
      setLoading(false);
    }
  }, [isAdmin, urlSearch, urlRole, urlActive, urlPage, urlSize, urlSortBy, urlSortDir]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const hasActiveFilters = Boolean(urlSearch || urlRole || urlActive);

  const handleClearFilters = () => {
    setSearchInput('');
    updateParams({ search: '', role: '', active: '', page: 0 });
  };

  const handleConfirmToggleStatus = async () => {
    if (!statusTarget || statusUpdating) return;
    const nextActive = !statusTarget.active;
    setStatusUpdating(true);
    try {
      const updated = await userService.updateUserStatus(statusTarget.id, nextActive);
      toast.success(
        `${updated.fullName} has been ${nextActive ? 'activated' : 'deactivated'}.`,
        nextActive ? 'Account activated' : 'Account deactivated'
      );
      setStatusTarget(null);
      fetchUsers();
    } catch (err) {
      const parsed = parseApiError(err);
      toast.error(parsed.message, 'Status update failed');
    } finally {
      setStatusUpdating(false);
    }
  };

  const columns = useMemo(
    () => [
      {
        key: 'fullName',
        label: 'Employee',
        sortable: true,
        render: (row) => (
          <div className="flex items-center gap-3 min-w-[200px]">
            <Avatar name={row.fullName} color={row.avatarColor} size="sm" />
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-ink truncate">
                  {row.fullName}
                </span>
                {currentUser?.id === row.id && <Badge tone="cyan">You</Badge>}
              </div>
              <p className="text-xs text-ink-muted truncate">
                {row.jobTitle || 'No job title'}
              </p>
            </div>
          </div>
        ),
      },
      {
        key: 'employeeCode',
        label: 'Code',
        sortable: true,
        render: (row) => (
          <span className="text-xs font-mono text-ink-muted tabular-nums whitespace-nowrap">
            {row.employeeCode}
          </span>
        ),
      },
      {
        key: 'email',
        label: 'Email',
        sortable: true,
        render: (row) => (
          <span className="text-xs text-ink-muted truncate block max-w-[220px]">
            {row.email}
          </span>
        ),
      },
      {
        key: 'role',
        label: 'Role',
        render: (row) => {
          const meta = ROLE_META[row.role] || { label: row.role, tone: 'neutral' };
          return (
            <Badge tone={meta.tone} dot>
              {meta.label}
            </Badge>
          );
        },
      },
      {
        key: 'active',
        label: 'Status',
        render: (row) => (
          <Badge tone={row.active ? 'emerald' : 'red'}>
            {row.active ? 'Active' : 'Deactivated'}
          </Badge>
        ),
      },
      {
        key: 'createdAt',
        label: 'Joined',
        sortable: true,
        render: (row) => (
          <span className="text-xs font-mono text-ink-muted tabular-nums whitespace-nowrap">
            {formatDateTime(row.createdAt)}
          </span>
        ),
      },
      {
        key: 'actions',
        label: 'Actions',
        align: 'right',
        render: (row) => {
          const isSelf = currentUser?.id === row.id;
          const canEdit = isAdmin || isSelf;

          return (
            <div
              className="inline-flex items-center justify-end gap-1"
              onClick={(e) => e.stopPropagation()}
            >
              <button
                type="button"
                onClick={() => setViewingUser(row)}
                title="View details"
                aria-label={`View ${row.fullName}`}
                className="p-1.5 rounded-md text-ink-muted hover:text-ink hover:bg-subtle transition-colors"
              >
                <Eye className="h-4 w-4" aria-hidden="true" />
              </button>

              {canEdit && (
                <button
                  type="button"
                  onClick={() => setEditingUser(row)}
                  title="Edit profile"
                  aria-label={`Edit ${row.fullName}`}
                  className="p-1.5 rounded-md text-ink-muted hover:text-ink hover:bg-subtle transition-colors"
                >
                  <Pencil className="h-4 w-4" aria-hidden="true" />
                </button>
              )}

              {isAdmin && (
                <button
                  type="button"
                  onClick={() => setRoleTarget(row)}
                  title="Change role"
                  aria-label={`Change role for ${row.fullName}`}
                  className="p-1.5 rounded-md text-ink-muted hover:text-brand-600 hover:bg-subtle transition-colors"
                >
                  <Shield className="h-4 w-4" aria-hidden="true" />
                </button>
              )}

              {isAdmin && (
                <button
                  type="button"
                  disabled={isSelf && row.active}
                  onClick={() => setStatusTarget(row)}
                  title={
                    isSelf && row.active
                      ? 'You cannot deactivate your own admin account'
                      : row.active
                        ? 'Deactivate employee'
                        : 'Activate employee'
                  }
                  aria-label={`${row.active ? 'Deactivate' : 'Activate'} ${row.fullName}`}
                  className={[
                    'p-1.5 rounded-md transition-colors disabled:opacity-35 disabled:pointer-events-none',
                    row.active
                      ? 'text-ink-muted hover:text-red-600 hover:bg-red-50'
                      : 'text-emerald-600 hover:bg-emerald-500/10',
                  ].join(' ')}
                >
                  <Power className="h-4 w-4" aria-hidden="true" />
                </button>
              )}
            </div>
          );
        },
      },
    ],
    [currentUser?.id, isAdmin]
  );

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.16, ease: 'easeOut' }}
      className="space-y-6"
    >
      <PageHeader
        title="Employees"
        description={
          isAdmin
            ? 'Manage organization employees, workspace roles, and account access.'
            : 'Directory of active employees across your organization.'
        }
        meta={
          <Badge tone="neutral" mono>
            {pageData.totalElements} {pageData.totalElements === 1 ? 'member' : 'members'}
          </Badge>
        }
        actions={
          <>
            <Button
              variant="secondary"
              size="sm"
              onClick={fetchUsers}
              disabled={loading}
              leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
            >
              Refresh
            </Button>
            {isAdmin && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => setCreateOpen(true)}
                leftIcon={<UserPlus className="h-3.5 w-3.5" />}
              >
                Add Employee
              </Button>
            )}
          </>
        }
      />

      {/* Search & Filter Toolbar */}
      <div className="flex flex-col md:flex-row md:items-center flex-wrap gap-3">
        <Input
          type="search"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Search by name, email, or employee code..."
          aria-label="Search employees"
          leftIcon={<Search className="h-4 w-4" />}
          className="max-w-md flex-1"
        />

        <div className="flex flex-wrap items-center gap-2.5">
          <select
            aria-label="Filter by role"
            value={urlRole}
            onChange={(e) => updateParams({ role: e.target.value, page: 0 })}
            className="h-9 rounded-lg bg-surface border border-line px-3 text-xs text-ink focus:outline-none focus:border-brand-500"
          >
            <option value="">All Roles</option>
            {ALL_ROLES.map((role) => (
              <option key={role} value={role}>
                {ROLE_META[role]?.label || role}
              </option>
            ))}
          </select>

          {isAdmin ? (
            <select
              aria-label="Filter by account status"
              value={urlActive}
              onChange={(e) => updateParams({ active: e.target.value, page: 0 })}
              className="h-9 rounded-lg bg-surface border border-line px-3 text-xs text-ink focus:outline-none focus:border-brand-500"
            >
              <option value="">All Statuses</option>
              <option value="true">Active Only</option>
              <option value="false">Deactivated Only</option>
            </select>
          ) : (
            <Badge tone="emerald" dot>
              Active employees only
            </Badge>
          )}

          {hasActiveFilters && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleClearFilters}
              leftIcon={<X className="h-3.5 w-3.5" />}
            >
              Clear
            </Button>
          )}
        </div>
      </div>

      {/* Employee DataTable */}
      <DataTable
        columns={columns}
        data={pageData.content}
        loading={loading}
        error={error}
        onRetry={fetchUsers}
        sortBy={urlSortBy}
        sortDir={urlSortDir}
        onSort={(nextSortBy, nextSortDir) =>
          updateParams({ sortBy: nextSortBy, sortDir: nextSortDir, page: 0 })
        }
        onRowClick={(row) => setViewingUser(row)}
        emptyState={{
          icon: Users,
          title: hasActiveFilters ? 'No matching employees found' : 'No employees found',
          description: hasActiveFilters
            ? 'Adjust or clear your search and role filters to see organization members.'
            : 'Provision your first organization employee to begin building teams.',
          action: hasActiveFilters ? (
            <Button variant="secondary" size="sm" onClick={handleClearFilters}>
              Reset Filters
            </Button>
          ) : isAdmin ? (
            <Button
              variant="primary"
              size="sm"
              onClick={() => setCreateOpen(true)}
              leftIcon={<UserPlus className="h-3.5 w-3.5" />}
            >
              Add Employee
            </Button>
          ) : null,
        }}
        footer={
          !loading && !error && pageData.totalElements > 0 ? (
            <Pagination
              page={pageData.page}
              size={pageData.size}
              totalElements={pageData.totalElements}
              totalPages={pageData.totalPages}
              onPageChange={(nextPage) => updateParams({ page: nextPage })}
              onSizeChange={(nextSize) => updateParams({ size: nextSize, page: 0 })}
              disabled={loading}
            />
          ) : null
        }
      />

      {/* Modals */}
      {isAdmin && (
        <UserFormModal
          isOpen={createOpen}
          onClose={() => setCreateOpen(false)}
          mode="create"
          onSuccess={(created) => {
            toast.success(`${created.fullName} (${created.employeeCode}) was created.`, 'Employee added');
            fetchUsers();
          }}
        />
      )}

      <UserFormModal
        isOpen={Boolean(editingUser)}
        onClose={() => setEditingUser(null)}
        mode="edit"
        initialUser={editingUser}
        onSuccess={(updated) => {
          toast.success(`Profile updated for ${updated.fullName}.`, 'Changes saved');
          if (currentUser?.id === updated.id) {
            refreshCurrentUser().catch(() => {});
          }
          fetchUsers();
        }}
      />

      <UserDetailModal
        isOpen={Boolean(viewingUser)}
        onClose={() => setViewingUser(null)}
        selectedUser={viewingUser}
        onEdit={(u) => setEditingUser(u)}
        onChangeRole={isAdmin ? (u) => setRoleTarget(u) : undefined}
        onToggleStatus={isAdmin ? (u) => setStatusTarget(u) : undefined}
      />

      {isAdmin && (
        <ChangeRoleModal
          isOpen={Boolean(roleTarget)}
          onClose={() => setRoleTarget(null)}
          targetUser={roleTarget}
          onSuccess={(updated) => {
            const label = ROLE_META[updated.role]?.label || updated.role;
            toast.success(`${updated.fullName} is now assigned the ${label} role.`, 'Role updated');
            if (currentUser?.id === updated.id) {
              refreshCurrentUser().catch(() => {});
            }
            fetchUsers();
          }}
        />
      )}

      {isAdmin && (
        <ConfirmDialog
          isOpen={Boolean(statusTarget)}
          onClose={() => setStatusTarget(null)}
          onConfirm={handleConfirmToggleStatus}
          loading={statusUpdating}
          variant={statusTarget?.active ? 'danger' : 'primary'}
          title={
            statusTarget?.active
              ? `Deactivate ${statusTarget?.fullName}?`
              : `Activate ${statusTarget?.fullName}?`
          }
          message={
            statusTarget?.active
              ? 'Deactivating this account revokes all active refresh tokens and prevents sign-in until reactivated.'
              : 'Activating this account restores workspace sign-in access for this employee.'
          }
          confirmLabel={statusTarget?.active ? 'Deactivate Account' : 'Activate Account'}
        />
      )}
    </motion.div>
  );
}
