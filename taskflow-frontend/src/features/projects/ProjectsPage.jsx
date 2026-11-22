import { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Calendar,
  FolderKanban,
  FolderPlus,
  LayoutGrid,
  List,
  RefreshCw,
  Search,
  Users,
  X,
} from 'lucide-react';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { DataTable } from '../../components/common/DataTable';
import { Select } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { EmptyState, ErrorState, Skeleton } from '../../components/common/Loading';
import { PageHeader } from '../../components/common/PageHeader';
import { Pagination } from '../../components/common/Pagination';
import { useAuth } from '../../hooks/useAuth';
import { useDebounce } from '../../hooks/useDebounce';
import { projectService } from '../../services/projectService';
import { ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateRange } from '../../utils/formatters';
import {
  ALL_PROJECT_STATUSES,
  PROJECT_STATUS_META,
} from './projectConstants';
import { ProjectCard } from './components/ProjectCard';
import { ProjectStatusBadge } from './components/ProjectStatusBadge';
import { ProjectFormModal } from './ProjectFormModal';

const STATUS_FILTER_OPTIONS = [
  { value: '', label: 'All Statuses' },
  ...ALL_PROJECT_STATUSES.map((status) => ({
    value: status,
    label: PROJECT_STATUS_META[status]?.label || status,
  })),
];

const SORT_OPTIONS = [
  { value: 'createdAt:desc', label: 'Newest First' },
  { value: 'updatedAt:desc', label: 'Recently Updated' },
  { value: 'name:asc', label: 'Name (A–Z)' },
  { value: 'name:desc', label: 'Name (Z–A)' },
  { value: 'projectKey:asc', label: 'Key (A–Z)' },
  { value: 'startDate:desc', label: 'Start Date (Latest)' },
  { value: 'startDate:asc', label: 'Start Date (Earliest)' },
];

const VALID_SORT_FIELDS = new Set(['createdAt', 'updatedAt', 'name', 'projectKey', 'startDate']);

export function ProjectsPage() {
  const { hasRole } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();

  const isAdmin = hasRole(ROLES.ADMIN);
  const canCreateProject = hasRole([ROLES.ADMIN, ROLES.PROJECT_MANAGER]);

  // URL-synced query state
  const urlSearch = searchParams.get('search') || '';
  const statusFilter = searchParams.get('status') || '';
  const page = Math.max(0, Number.parseInt(searchParams.get('page') || '0', 10) || 0);
  const size = Math.min(100, Math.max(1, Number.parseInt(searchParams.get('size') || '20', 10) || 20));
  const rawSortBy = searchParams.get('sortBy') || 'createdAt';
  const sortBy = VALID_SORT_FIELDS.has(rawSortBy) ? rawSortBy : 'createdAt';
  const sortDir = searchParams.get('sortDir') === 'asc' ? 'asc' : 'desc';
  const viewMode = searchParams.get('view') === 'grid' ? 'grid' : 'table';

  const [searchInput, setSearchInput] = useState(urlSearch);
  const debouncedSearch = useDebounce(searchInput, 300);

  const [pageData, setPageData] = useState({
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    last: true,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);

  const updateParams = useCallback(
    (updates) => {
      const next = new URLSearchParams(searchParams);
      for (const [key, val] of Object.entries(updates)) {
        if (val === undefined || val === null || val === '') {
          next.delete(key);
        } else {
          next.set(key, String(val));
        }
      }
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams]
  );

  // Sync debounced search text into URL query params
  useEffect(() => {
    const trimmed = debouncedSearch.trim();
    if (trimmed !== urlSearch) {
      updateParams({ search: trimmed || null, page: 0 });
    }
  }, [debouncedSearch, urlSearch, updateParams]);

  // Keep local search input aligned when URL changes externally (e.g. Back/Clear)
  useEffect(() => {
    setSearchInput(urlSearch);
  }, [urlSearch]);

  const fetchProjects = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await projectService.listProjects({
        search: urlSearch || undefined,
        status: statusFilter || undefined,
        page,
        size,
        sortBy,
        sortDir,
      });
      setPageData({
        content: Array.isArray(response?.content) ? response.content : [],
        page: response?.page ?? page,
        size: response?.size ?? size,
        totalElements: response?.totalElements ?? 0,
        totalPages: response?.totalPages ?? 0,
        last: Boolean(response?.last),
      });
    } catch (err) {
      const parsed = parseApiError(err);
      setError({ title: 'Unable to load projects', message: parsed.message });
    } finally {
      setLoading(false);
    }
  }, [urlSearch, statusFilter, page, size, sortBy, sortDir]);

  useEffect(() => {
    fetchProjects();
  }, [fetchProjects]);

  const handleOpenProject = (project) => {
    navigate(`/projects/${project.id}`, {
      state: { fromSearch: location.search },
    });
  };

  const handleSort = (nextSortBy, nextSortDir) => {
    updateParams({ sortBy: nextSortBy, sortDir: nextSortDir, page: 0 });
  };

  const hasActiveFilters = Boolean(urlSearch || statusFilter);

  const handleClearFilters = () => {
    setSearchInput('');
    updateParams({ search: null, status: null, page: 0 });
  };

  const columns = [
    {
      key: 'projectKey',
      label: 'Key',
      sortable: true,
      render: (row) => (
        <Badge tone="cyan" mono>
          {row.projectKey}
        </Badge>
      ),
    },
    {
      key: 'name',
      label: 'Project',
      sortable: true,
      render: (row) => (
        <div className="min-w-[200px] max-w-md">
          <p className="text-sm font-medium text-ink truncate">{row.name}</p>
          <p className="text-xs text-ink-muted truncate mt-0.5">
            {row.description?.trim() || 'No description provided'}
          </p>
        </div>
      ),
    },
    {
      key: 'status',
      label: 'Status',
      render: (row) => <ProjectStatusBadge status={row.status} />,
    },
    {
      key: 'manager',
      label: 'Manager',
      render: (row) =>
        row.manager ? (
          <div className="flex items-center gap-2 min-w-[150px]">
            <Avatar name={row.manager.fullName} color={row.manager.avatarColor} size="xs" />
            <span className="text-xs text-ink truncate">{row.manager.fullName}</span>
          </div>
        ) : (
          <span className="text-xs text-ink-faint">Unassigned</span>
        ),
    },
    {
      key: 'startDate',
      label: 'Timeline',
      sortable: true,
      render: (row) => (
        <span className="inline-flex items-center gap-1.5 text-xs text-ink-muted whitespace-nowrap tabular-nums">
          <Calendar className="h-3.5 w-3.5 text-ink-faint shrink-0" aria-hidden="true" />
          {formatDateRange(row.startDate, row.endDate)}
        </span>
      ),
    },
    {
      key: 'memberCount',
      label: 'Members',
      align: 'right',
      render: (row) => (
        <span className="inline-flex items-center justify-end gap-1.5 text-xs font-mono tabular-nums text-ink">
          <Users className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
          {Number(row.memberCount ?? 0)}
        </span>
      ),
    },
  ];

  const paginationFooter =
    pageData.totalElements > 0 ? (
      <Pagination
        page={pageData.page}
        size={pageData.size}
        totalElements={pageData.totalElements}
        totalPages={pageData.totalPages}
        disabled={loading}
        onPageChange={(nextPage) => updateParams({ page: nextPage })}
        onSizeChange={(nextSize) => updateParams({ size: nextSize, page: 0 })}
      />
    ) : null;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Projects"
        description={
          isAdmin
            ? 'Browse and govern all organization projects, lifecycle states, and member rosters.'
            : 'Access projects you manage or collaborate on across your organization.'
        }
        meta={
          <Badge tone="neutral" mono>
            {pageData.totalElements} {pageData.totalElements === 1 ? 'project' : 'projects'}
          </Badge>
        }
        actions={
          <>
            <Button
              variant="secondary"
              size="sm"
              onClick={fetchProjects}
              loading={loading}
              leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
            >
              Refresh
            </Button>
            {canCreateProject && (
              <Button
                size="sm"
                onClick={() => setCreateOpen(true)}
                leftIcon={<FolderPlus className="h-4 w-4" />}
              >
                Create Project
              </Button>
            )}
          </>
        }
      />

      {/* Filter & View Bar */}
      <div className="rounded-xl border border-line bg-surface p-3 shadow-card">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <div className="flex-1 min-w-0">
          <Input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search by project name or key (e.g., BILL)..."
            leftIcon={<Search className="h-4 w-4" />}
          />
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <div className="w-full sm:w-44">
            <Select
              aria-label="Filter by project status"
              value={statusFilter}
              onChange={(e) => updateParams({ status: e.target.value || null, page: 0 })}
              options={STATUS_FILTER_OPTIONS}
            />
          </div>

          <div className="w-full sm:w-48">
            <Select
              aria-label="Sort projects"
              value={`${sortBy}:${sortDir}`}
              onChange={(e) => {
                const [nextField, nextDir] = e.target.value.split(':');
                updateParams({ sortBy: nextField, sortDir: nextDir, page: 0 });
              }}
              options={SORT_OPTIONS}
            />
          </div>

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

          {/* Table / Grid toggle */}
          <div
            className="inline-flex items-center rounded-lg border border-line bg-canvas p-0.5"
            role="group"
            aria-label="Projects display layout"
          >
            <button
              type="button"
              onClick={() => updateParams({ view: 'table' })}
              aria-pressed={viewMode === 'table'}
              aria-label="Table view"
              title="Table view"
              className={[
                'p-2 rounded-md transition-colors',
                viewMode === 'table'
                  ? 'bg-surface text-brand-600'
                  : 'text-ink-muted hover:text-ink',
              ].join(' ')}
            >
              <List className="h-4 w-4" aria-hidden="true" />
            </button>
            <button
              type="button"
              onClick={() => updateParams({ view: 'grid' })}
              aria-pressed={viewMode === 'grid'}
              aria-label="Card grid view"
              title="Card grid view"
              className={[
                'p-2 rounded-md transition-colors',
                viewMode === 'grid'
                  ? 'bg-surface text-brand-600'
                  : 'text-ink-muted hover:text-ink',
              ].join(' ')}
            >
              <LayoutGrid className="h-4 w-4" aria-hidden="true" />
            </button>
            </div>
          </div>
        </div>
      </div>

      {/* Result summary */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-ink-muted tabular-nums">
          Showing {pageData.content.length} of {pageData.totalElements}{' '}
          {pageData.totalElements === 1 ? 'project' : 'projects'}
        </p>
        {hasActiveFilters && (
          <span className="text-[11px] text-ink-faint">Filters applied</span>
        )}
      </div>

      {/* Main Content: Table or Grid */}
      {viewMode === 'table' ? (
        <DataTable
          columns={columns}
          data={pageData.content}
          rowKey="id"
          loading={loading}
          skeletonRows={6}
          error={error}
          onRetry={fetchProjects}
          sortBy={sortBy}
          sortDir={sortDir}
          onSort={handleSort}
          onRowClick={handleOpenProject}
          emptyState={{
            icon: FolderKanban,
            title: hasActiveFilters ? 'No projects match these filters' : 'No projects available',
            description: hasActiveFilters
              ? 'Try adjusting your search query or status filter.'
              : canCreateProject
                ? 'Create your first project to organize teams, timelines, and delivery workflows.'
                : 'You are not currently assigned to any projects. Ask a Project Manager or Administrator to add you.',
            action: hasActiveFilters ? (
              <Button variant="secondary" size="sm" onClick={handleClearFilters}>
                Clear Filters
              </Button>
            ) : canCreateProject ? (
              <Button
                size="sm"
                onClick={() => setCreateOpen(true)}
                leftIcon={<FolderPlus className="h-4 w-4" />}
              >
                Create Project
              </Button>
            ) : null,
          }}
          footer={paginationFooter}
        />
      ) : error && !loading ? (
        <ErrorState
          title={error.title}
          description={error.message}
          onRetry={fetchProjects}
        />
      ) : loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, idx) => (
            <div
              key={`project-card-skeleton-${idx}`}
              className="bg-surface border border-line rounded-xl p-5 space-y-4"
            >
              <div className="flex items-center gap-2">
                <Skeleton className="h-5 w-16" />
                <Skeleton className="h-5 w-20" />
              </div>
              <div className="space-y-2">
                <Skeleton className="h-5 w-3/4" />
                <Skeleton className="h-3.5 w-full" />
                <Skeleton className="h-3.5 w-2/3" />
              </div>
              <div className="pt-3 border-t border-line flex items-center justify-between">
                <Skeleton className="h-4 w-32" />
                <Skeleton className="h-4 w-10" />
              </div>
            </div>
          ))}
        </div>
      ) : pageData.content.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title={hasActiveFilters ? 'No projects match these filters' : 'No projects available'}
          description={
            hasActiveFilters
              ? 'Try adjusting your search query or status filter.'
              : canCreateProject
                ? 'Create your first project to organize teams, timelines, and delivery workflows.'
                : 'You are not currently assigned to any projects. Ask a Project Manager or Administrator to add you.'
          }
          action={
            hasActiveFilters ? (
              <Button variant="secondary" size="sm" onClick={handleClearFilters}>
                Clear Filters
              </Button>
            ) : canCreateProject ? (
              <Button
                size="sm"
                onClick={() => setCreateOpen(true)}
                leftIcon={<FolderPlus className="h-4 w-4" />}
              >
                Create Project
              </Button>
            ) : null
          }
        />
      ) : (
        <div className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {pageData.content.map((project) => (
              <ProjectCard
                key={project.id}
                project={project}
                onOpen={handleOpenProject}
              />
            ))}
          </div>

          {pageData.totalElements > 0 && (
            <div className="bg-surface border border-line rounded-xl overflow-hidden">
              {paginationFooter}
            </div>
          )}
        </div>
      )}

      {/* Create Project Modal (ADMIN & PROJECT_MANAGER) */}
      {canCreateProject && (
        <ProjectFormModal
          isOpen={createOpen}
          onClose={() => setCreateOpen(false)}
          onCreated={(created) => {
            if (created?.id) {
              navigate(`/projects/${created.id}`);
            } else {
              fetchProjects();
            }
          }}
        />
      )}
    </div>
  );
}
