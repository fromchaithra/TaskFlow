import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  AlertTriangle,
  Calendar,
  CheckSquare,
  Clock,
  GitBranch,
  Plus,
  RefreshCw,
} from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { DataTable } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { Pagination } from '../../components/common/Pagination';
import { useAuth } from '../../hooks/useAuth';
import { useDebounce } from '../../hooks/useDebounce';
import { projectService } from '../../services/projectService';
import { taskService } from '../../services/taskService';
import { ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDate } from '../../utils/formatters';
import { TaskAssignee } from './components/TaskAssignee';
import { TaskFilters } from './components/TaskFilters';
import { TaskPriorityBadge } from './components/TaskPriorityBadge';
import { TaskStatusBadge } from './components/TaskStatusBadge';
import { TaskDetailModal } from './TaskDetailModal';
import { TaskFormModal } from './TaskFormModal';

const SORT_OPTIONS = [
  { value: 'createdAt:desc', label: 'Newest First' },
  { value: 'updatedAt:desc', label: 'Recently Updated' },
  { value: 'dueDate:asc', label: 'Due Date (Soonest)' },
  { value: 'dueDate:desc', label: 'Due Date (Latest)' },
  { value: 'priority:desc', label: 'Priority' },
  { value: 'status:asc', label: 'Status' },
  { value: 'taskKey:asc', label: 'Task Key' },
];

const VALID_SORT_FIELDS = new Set([
  'createdAt',
  'updatedAt',
  'dueDate',
  'priority',
  'status',
  'boardOrder',
  'taskKey',
]);

/**
 * Global Tasks Page (/tasks) backed by:
 * - GET /api/v1/tasks          (scope = all)
 * - GET /api/v1/tasks/my-tasks (scope = mine)
 */
export function TasksPage() {
  const { user, hasRole } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const isAdmin = hasRole(ROLES.ADMIN);
  const isPmRole = hasRole(ROLES.PROJECT_MANAGER);

  const scope = searchParams.get('scope') === 'mine' ? 'mine' : 'all';
  const urlSearch = searchParams.get('search') || '';
  const projectFilter = searchParams.get('projectId') || '';
  const statusFilter = searchParams.get('status') || '';
  const priorityFilter = searchParams.get('priority') || '';
  const overdueOnly = searchParams.get('overdueOnly') === 'true';
  const page = Math.max(0, Number.parseInt(searchParams.get('page') || '0', 10) || 0);
  const size = Math.min(100, Math.max(1, Number.parseInt(searchParams.get('size') || '20', 10) || 20));
  const defaultSortBy = scope === 'mine' ? 'dueDate' : 'createdAt';
  const defaultSortDir = scope === 'mine' ? 'asc' : 'desc';
  const rawSortBy = searchParams.get('sortBy') || defaultSortBy;
  const sortBy = VALID_SORT_FIELDS.has(rawSortBy) ? rawSortBy : defaultSortBy;
  const sortDir = searchParams.get('sortDir')
    ? searchParams.get('sortDir') === 'asc'
      ? 'asc'
      : 'desc'
    : defaultSortDir;

  const [searchInput, setSearchInput] = useState(urlSearch);
  const debouncedSearch = useDebounce(searchInput, 300);

  const [projects, setProjects] = useState([]);
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
  const [selectedTask, setSelectedTask] = useState(null);

  const updateParams = useCallback(
    (updates) => {
      const next = new URLSearchParams(searchParams);
      for (const [key, val] of Object.entries(updates)) {
        if (val === undefined || val === null || val === '' || val === false) {
          next.delete(key);
        } else {
          next.set(key, String(val));
        }
      }
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams]
  );

  useEffect(() => {
    const trimmed = debouncedSearch.trim();
    if (trimmed !== urlSearch) {
      updateParams({ search: trimmed || null, page: 0 });
    }
  }, [debouncedSearch, urlSearch, updateParams]);

  useEffect(() => {
    setSearchInput(urlSearch);
  }, [urlSearch]);

  // Load accessible projects for filter and create modal
  useEffect(() => {
    let cancelled = false;
    projectService
      .listProjects({ page: 0, size: 100, sortBy: 'name', sortDir: 'asc' })
      .then((res) => {
        if (!cancelled) setProjects(Array.isArray(res?.content) ? res.content : []);
      })
      .catch(() => {
        if (!cancelled) setProjects([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const projectMap = useMemo(() => {
    const map = new Map();
    for (const p of projects) {
      map.set(p.id, p);
    }
    return map;
  }, [projects]);

  const manageableProjects = useMemo(() => {
    return projects.filter((proj) => {
      if (proj.status === 'ARCHIVED' || proj.status === 'COMPLETED') return false;
      if (isAdmin) return true;
      return isPmRole && proj.manager?.id === user?.id;
    });
  }, [projects, isAdmin, isPmRole, user?.id]);

  const projectOptions = useMemo(() => {
    return [
      { value: '', label: 'All Projects' },
      ...projects.map((p) => ({ value: String(p.id), label: `${p.projectKey} — ${p.name}` })),
    ];
  }, [projects]);

  const fetchTasks = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const commonParams = {
        projectId: projectFilter ? Number(projectFilter) : undefined,
        status: statusFilter || undefined,
        priority: priorityFilter || undefined,
        overdueOnly,
        search: urlSearch || undefined,
        page,
        size,
        sortBy,
        sortDir,
      };
      const response =
        scope === 'mine'
          ? await taskService.listMyTasks(commonParams)
          : await taskService.listTasks(commonParams);

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
      setError({ title: 'Unable to load tasks', message: parsed.message });
    } finally {
      setLoading(false);
    }
  }, [
    scope,
    projectFilter,
    statusFilter,
    priorityFilter,
    overdueOnly,
    urlSearch,
    page,
    size,
    sortBy,
    sortDir,
  ]);

  useEffect(() => {
    fetchTasks();
  }, [fetchTasks]);

  const hasActiveFilters = Boolean(
    urlSearch || projectFilter || statusFilter || priorityFilter || overdueOnly
  );

  const handleClearFilters = () => {
    setSearchInput('');
    updateParams({
      search: null,
      projectId: null,
      status: null,
      priority: null,
      overdueOnly: null,
      page: 0,
    });
  };

  const selectedTaskProject = selectedTask ? projectMap.get(selectedTask.projectId) || null : null;
  const canManageSelectedTaskProject = useMemo(() => {
    if (!selectedTask || !user) return false;
    if (isAdmin) return true;
    return isPmRole && selectedTaskProject?.manager?.id === user.id;
  }, [selectedTask, user, isAdmin, isPmRole, selectedTaskProject]);

  const columns = [
    {
      key: 'taskKey',
      label: 'Key',
      sortable: true,
      render: (row) => (
        <div className="inline-flex items-center gap-1.5 whitespace-nowrap">
          <span className="text-xs font-mono font-medium text-brand-600">{row.taskKey}</span>
          {row.parentTaskId && (
            <span className="text-ink-faint" title="Subtask">
              <GitBranch className="h-3 w-3" aria-hidden="true" />
            </span>
          )}
        </div>
      ),
    },
    {
      key: 'title',
      label: 'Task',
      render: (row) => (
        <div className="min-w-[220px] max-w-md">
          <p className="text-sm font-medium text-ink truncate">{row.title}</p>
          <div className="flex items-center gap-2 text-[11px] text-ink-faint mt-0.5">
            <Link
              to={`/projects/${row.projectId}?tab=tasks`}
              onClick={(e) => e.stopPropagation()}
              className="font-mono text-ink-muted hover:text-brand-600 transition-colors"
            >
              {row.projectKey}
            </Link>
            {row.sprintName && (
              <>
                <span aria-hidden="true">·</span>
                <span className="truncate">{row.sprintName}</span>
              </>
            )}
            {row.estimatedHours != null && (
              <>
                <span aria-hidden="true">·</span>
                <span className="inline-flex items-center gap-0.5 font-mono tabular-nums">
                  <Clock className="h-3 w-3" aria-hidden="true" />
                  {row.estimatedHours}h
                </span>
              </>
            )}
          </div>
        </div>
      ),
    },
    {
      key: 'status',
      label: 'Status',
      sortable: true,
      render: (row) => <TaskStatusBadge status={row.status} />,
    },
    {
      key: 'priority',
      label: 'Priority',
      sortable: true,
      render: (row) => <TaskPriorityBadge priority={row.priority} />,
    },
    {
      key: 'assignee',
      label: 'Assignee',
      render: (row) => <TaskAssignee assignee={row.assignee} />,
    },
    {
      key: 'dueDate',
      label: 'Due Date',
      sortable: true,
      render: (row) =>
        row.dueDate ? (
          <span
            className={[
              'inline-flex items-center gap-1.5 text-xs whitespace-nowrap tabular-nums',
              row.overdue ? 'text-red-600 font-medium' : 'text-ink-muted',
            ].join(' ')}
          >
            {row.overdue ? (
              <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            ) : (
              <Calendar className="h-3.5 w-3.5 text-ink-faint shrink-0" aria-hidden="true" />
            )}
            {formatDate(row.dueDate)}
          </span>
        ) : (
          <span className="text-xs text-ink-faint">Not set</span>
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
        title="Tasks"
        description={
          scope === 'mine'
            ? 'Your personal engineering queue across all accessible organization projects.'
            : 'Browse, filter, and track tasks across every project you have access to.'
        }
        meta={
          <Badge tone="neutral" mono>
            {pageData.totalElements} {pageData.totalElements === 1 ? 'task' : 'tasks'}
          </Badge>
        }
        actions={
          <>
            <Button
              variant="secondary"
              size="sm"
              onClick={fetchTasks}
              loading={loading}
              leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
            >
              Refresh
            </Button>
            {manageableProjects.length > 0 && (
              <Button
                size="sm"
                onClick={() => setCreateOpen(true)}
                leftIcon={<Plus className="h-4 w-4" />}
              >
                Create Task
              </Button>
            )}
          </>
        }
      />

      {/* Scope Segmented Control */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-surface p-3 shadow-card">
        <div
          className="inline-flex items-center p-1 rounded-lg bg-subtle border border-line"
          role="tablist"
          aria-label="Task queue scope"
        >
          <button
            type="button"
            role="tab"
            aria-selected={scope === 'all'}
            onClick={() =>
              updateParams({ scope: null, sortBy: 'createdAt', sortDir: 'desc', page: 0 })
            }
            className={[
              'px-3.5 py-1.5 rounded-md text-xs font-medium transition-colors whitespace-nowrap',
              scope === 'all'
                ? 'bg-surface text-brand-600'
                : 'text-ink-muted hover:text-ink',
            ].join(' ')}
          >
            All Accessible Tasks
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={scope === 'mine'}
            onClick={() =>
              updateParams({ scope: 'mine', sortBy: 'dueDate', sortDir: 'asc', page: 0 })
            }
            className={[
              'px-3.5 py-1.5 rounded-md text-xs font-medium transition-colors whitespace-nowrap',
              scope === 'mine'
                ? 'bg-surface text-brand-600'
                : 'text-ink-muted hover:text-ink',
            ].join(' ')}
          >
            My Assigned Tasks
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="rounded-xl border border-line bg-surface p-3 shadow-card">
        <TaskFilters
        searchValue={searchInput}
        onSearchChange={setSearchInput}
        projectValue={projectFilter}
        onProjectChange={(val) => updateParams({ projectId: val || null, page: 0 })}
        projectOptions={projectOptions}
        statusValue={statusFilter}
        onStatusChange={(val) => updateParams({ status: val || null, page: 0 })}
        priorityValue={priorityFilter}
        onPriorityChange={(val) => updateParams({ priority: val || null, page: 0 })}
        overdueOnly={overdueOnly}
        onOverdueOnlyChange={(val) => updateParams({ overdueOnly: val ? 'true' : null, page: 0 })}
        sortValue={`${sortBy}:${sortDir}`}
        onSortChange={(val) => {
          const [field, dir] = val.split(':');
          updateParams({ sortBy: field, sortDir: dir, page: 0 });
        }}
        sortOptions={SORT_OPTIONS}
        onClear={handleClearFilters}
        hasActiveFilters={hasActiveFilters}
        />
      </div>

      {/* Result summary */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-ink-muted tabular-nums">
          Showing {pageData.content.length} of {pageData.totalElements}{' '}
          {pageData.totalElements === 1 ? 'task' : 'tasks'}
        </p>
        {hasActiveFilters && (
          <span className="text-[11px] text-ink-faint">Filters applied</span>
        )}
      </div>

      {/* Tasks Table */}
      <DataTable
        columns={columns}
        data={pageData.content}
        rowKey="id"
        loading={loading}
        skeletonRows={6}
        error={error}
        onRetry={fetchTasks}
        sortBy={sortBy}
        sortDir={sortDir}
        onSort={(nextField, nextDir) =>
          updateParams({ sortBy: nextField, sortDir: nextDir, page: 0 })
        }
        onRowClick={(row) => setSelectedTask(row)}
        emptyState={{
          icon: CheckSquare,
          title: hasActiveFilters
            ? 'No tasks match these filters'
            : scope === 'mine'
              ? 'No tasks assigned to you'
              : 'No tasks available',
          description: hasActiveFilters
            ? 'Try adjusting or clearing your search and filter selections.'
            : scope === 'mine'
              ? 'Tasks assigned to your account will appear here ordered by due date.'
              : manageableProjects.length > 0
                ? 'Create a task inside one of your active projects to get started.'
                : 'Tasks from projects you belong to will appear here.',
          action: hasActiveFilters ? (
            <Button variant="secondary" size="sm" onClick={handleClearFilters}>
              Clear Filters
            </Button>
          ) : manageableProjects.length > 0 ? (
            <Button
              size="sm"
              onClick={() => setCreateOpen(true)}
              leftIcon={<Plus className="h-4 w-4" />}
            >
              Create Task
            </Button>
          ) : null,
        }}
        footer={paginationFooter}
      />

      {/* Create Task Modal */}
      {manageableProjects.length > 0 && (
        <TaskFormModal
          isOpen={createOpen}
          onClose={() => setCreateOpen(false)}
          mode="create"
          project={
            projectFilter && manageableProjects.some((p) => String(p.id) === projectFilter)
              ? manageableProjects.find((p) => String(p.id) === projectFilter)
              : null
          }
          availableProjects={manageableProjects}
          canManageProject
          onSaved={() => fetchTasks()}
        />
      )}

      {/* Task Detail Modal */}
      <TaskDetailModal
        isOpen={Boolean(selectedTask)}
        onClose={() => setSelectedTask(null)}
        taskId={selectedTask?.id || null}
        project={selectedTaskProject}
        canManageProject={canManageSelectedTaskProject}
        onSelectTask={(nextId) =>
          setSelectedTask((prev) => ({ ...(prev || {}), id: nextId }))
        }
        onTaskChanged={() => fetchTasks()}
      />
    </div>
  );
}
