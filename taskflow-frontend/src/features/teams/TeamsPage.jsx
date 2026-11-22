import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Eye,
  Pencil,
  Plus,
  PowerOff,
  RefreshCw,
  Search,
  UsersRound,
  X,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useDebounce } from '../../hooks/useDebounce';
import { useToast } from '../../hooks/useToast';
import { teamService } from '../../services/teamService';
import { ROLES } from '../../utils/constants';
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
import { TeamFormModal } from './TeamFormModal';

const ALLOWED_SORTS = new Set(['createdAt', 'name', 'updatedAt']);

export function TeamsPage() {
  const { hasRole } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();

  const isAdmin = hasRole(ROLES.ADMIN);

  // URL-synced query parameters
  const urlSearch = searchParams.get('search') || '';
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

  const [createOpen, setCreateOpen] = useState(false);
  const [editingTeam, setEditingTeam] = useState(null);
  const [deactivateTarget, setDeactivateTarget] = useState(null);
  const [deactivating, setDeactivating] = useState(false);

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

  useEffect(() => {
    if (debouncedSearch !== urlSearch) {
      updateParams({ search: debouncedSearch, page: 0 });
    }
  }, [debouncedSearch, urlSearch, updateParams]);

  const fetchTeams = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const activeParam =
        isAdmin && (urlActive === 'true' || urlActive === 'false')
          ? urlActive === 'true'
          : undefined;

      const response = await teamService.listTeams({
        search: urlSearch || undefined,
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
  }, [isAdmin, urlSearch, urlActive, urlPage, urlSize, urlSortBy, urlSortDir]);

  useEffect(() => {
    fetchTeams();
  }, [fetchTeams]);

  const hasActiveFilters = Boolean(urlSearch || urlActive);

  const handleClearFilters = () => {
    setSearchInput('');
    updateParams({ search: '', active: '', page: 0 });
  };

  const handleOpenTeam = useCallback(
    (teamId) => {
      navigate(`/teams/${teamId}`, {
        state: { fromSearch: location.search },
      });
    },
    [navigate, location.search]
  );

  const handleConfirmDeactivate = async () => {
    if (!deactivateTarget || deactivating) return;
    setDeactivating(true);
    try {
      await teamService.deactivateTeam(deactivateTarget.id);
      toast.success(`Team "${deactivateTarget.name}" has been deactivated.`, 'Team deactivated');
      setDeactivateTarget(null);
      fetchTeams();
    } catch (err) {
      toast.error(parseApiError(err).message, 'Deactivation failed');
    } finally {
      setDeactivating(false);
    }
  };

  const columns = useMemo(
    () => [
      {
        key: 'name',
        label: 'Team',
        sortable: true,
        render: (row) => (
          <div className="min-w-[200px] max-w-md">
            <p className="text-sm font-medium text-ink truncate">{row.name}</p>
            <p className="text-xs text-ink-muted truncate mt-0.5">
              {row.description || 'No description provided'}
            </p>
          </div>
        ),
      },
      {
        key: 'lead',
        label: 'Team Lead',
        render: (row) =>
          row.lead ? (
            <div className="flex items-center gap-2.5 min-w-[170px]">
              <Avatar name={row.lead.fullName} color={row.lead.avatarColor} size="xs" />
              <div className="min-w-0">
                <p className="text-xs font-medium text-ink truncate">
                  {row.lead.fullName}
                </p>
                <p className="text-[11px] text-ink-muted truncate">{row.lead.email}</p>
              </div>
            </div>
          ) : (
            <span className="text-xs text-ink-faint">Unassigned</span>
          ),
      },
      {
        key: 'memberCount',
        label: 'Members',
        align: 'right',
        render: (row) => (
          <span className="text-xs font-mono text-ink tabular-nums">
            {row.memberCount ?? 0}
          </span>
        ),
      },
      {
        key: 'active',
        label: 'Status',
        render: (row) => (
          <Badge tone={row.active ? 'emerald' : 'red'} dot>
            {row.active ? 'Active' : 'Deactivated'}
          </Badge>
        ),
      },
      {
        key: 'createdAt',
        label: 'Created',
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
        render: (row) => (
          <div
            className="inline-flex items-center justify-end gap-1"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              type="button"
              onClick={() => handleOpenTeam(row.id)}
              title="View team details"
              aria-label={`View ${row.name}`}
              className="p-1.5 rounded-md text-ink-muted hover:text-ink hover:bg-subtle transition-colors"
            >
              <Eye className="h-4 w-4" aria-hidden="true" />
            </button>

            {isAdmin && (
              <button
                type="button"
                onClick={() => setEditingTeam(row)}
                title="Edit team"
                aria-label={`Edit ${row.name}`}
                className="p-1.5 rounded-md text-ink-muted hover:text-ink hover:bg-subtle transition-colors"
              >
                <Pencil className="h-4 w-4" aria-hidden="true" />
              </button>
            )}

            {isAdmin && row.active && (
              <button
                type="button"
                onClick={() => setDeactivateTarget(row)}
                title="Deactivate team"
                aria-label={`Deactivate ${row.name}`}
                className="p-1.5 rounded-md text-ink-muted hover:text-red-600 hover:bg-red-50 transition-colors"
              >
                <PowerOff className="h-4 w-4" aria-hidden="true" />
              </button>
            )}
          </div>
        ),
      },
    ],
    [handleOpenTeam, isAdmin]
  );

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.16, ease: 'easeOut' }}
      className="space-y-6"
    >
      <PageHeader
        title="Teams"
        description={
          isAdmin
            ? 'Organize engineering squads, assign team leads, and manage roster membership.'
            : 'Directory of active teams and squad leads across your organization.'
        }
        meta={
          <Badge tone="neutral" mono>
            {pageData.totalElements} {pageData.totalElements === 1 ? 'team' : 'teams'}
          </Badge>
        }
        actions={
          <>
            <Button
              variant="secondary"
              size="sm"
              onClick={fetchTeams}
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
                leftIcon={<Plus className="h-3.5 w-3.5" />}
              >
                Create Team
              </Button>
            )}
          </>
        }
      />

      {/* Search & Filter Toolbar */}
      <div className="flex flex-col md:flex-row md:items-center flex-wrap gap-3 rounded-xl border border-line bg-surface p-3 shadow-card">
        <Input
          type="search"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Search teams by name..."
          aria-label="Search teams"
          leftIcon={<Search className="h-4 w-4" />}
          className="max-w-md flex-1"
        />

        <div className="flex flex-wrap items-center gap-2.5">
          {isAdmin ? (
            <select
              aria-label="Filter teams by status"
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
              Active teams only
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

      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-ink-muted tabular-nums">
          Showing {pageData.content.length} of {pageData.totalElements}{' '}
          {pageData.totalElements === 1 ? 'team' : 'teams'}
        </p>
        {hasActiveFilters && (
          <span className="text-[11px] text-ink-faint">Filters applied</span>
        )}
      </div>

      {/* Teams Table */}
      <DataTable
        columns={columns}
        data={pageData.content}
        loading={loading}
        error={error}
        onRetry={fetchTeams}
        sortBy={urlSortBy}
        sortDir={urlSortDir}
        onSort={(nextSortBy, nextSortDir) =>
          updateParams({ sortBy: nextSortBy, sortDir: nextSortDir, page: 0 })
        }
        onRowClick={(row) => handleOpenTeam(row.id)}
        emptyState={{
          icon: UsersRound,
          title: hasActiveFilters ? 'No matching teams found' : 'No teams created yet',
          description: hasActiveFilters
            ? 'Clear or adjust your search criteria to see organization teams.'
            : 'Create your first team to organize engineers, PMs, and QA members.',
          action: hasActiveFilters ? (
            <Button variant="secondary" size="sm" onClick={handleClearFilters}>
              Reset Filters
            </Button>
          ) : isAdmin ? (
            <Button
              variant="primary"
              size="sm"
              onClick={() => setCreateOpen(true)}
              leftIcon={<Plus className="h-3.5 w-3.5" />}
            >
              Create Team
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
        <TeamFormModal
          isOpen={createOpen}
          onClose={() => setCreateOpen(false)}
          mode="create"
          onSuccess={(created) => {
            toast.success(`Team "${created.name}" has been created.`, 'Team created');
            fetchTeams();
          }}
        />
      )}

      {isAdmin && (
        <TeamFormModal
          isOpen={Boolean(editingTeam)}
          onClose={() => setEditingTeam(null)}
          mode="edit"
          initialTeam={editingTeam}
          onSuccess={(updated) => {
            toast.success(`Team "${updated.name}" has been updated.`, 'Team saved');
            fetchTeams();
          }}
        />
      )}

      {isAdmin && (
        <ConfirmDialog
          isOpen={Boolean(deactivateTarget)}
          onClose={() => setDeactivateTarget(null)}
          onConfirm={handleConfirmDeactivate}
          loading={deactivating}
          variant="danger"
          title={`Deactivate "${deactivateTarget?.name}"?`}
          message="Deactivated teams remain archived for audit history but cannot accept new members or lead changes until reactivated."
          confirmLabel="Deactivate Team"
        />
      )}
    </motion.div>
  );
}
