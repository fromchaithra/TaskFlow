import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Calendar,
  CheckSquare,
  Clock,
  GitBranch,
  Lock,
  Plus,
  RefreshCw,
} from 'lucide-react';
import { Button } from '../../components/common/Button';
import { DataTable } from '../../components/common/DataTable';
import { Pagination } from '../../components/common/Pagination';
import { useDebounce } from '../../hooks/useDebounce';
import { projectService } from '../../services/projectService';
import { taskService } from '../../services/taskService';
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

/**
 * Project-scoped Task List tab mounted inside ProjectDetailPage (?tab=tasks).
 * Backed by GET /api/v1/tasks?projectId={project.id}.
 */
export function ProjectTasksTab({ project, canManageProject = false, onProjectMutated }) {
  const isArchived = project?.status === 'ARCHIVED';
  const isCompleted = project?.status === 'COMPLETED';
  const canCreateTask = canManageProject && !isArchived && !isCompleted;

  const [searchInput, setSearchInput] = useState('');
  const debouncedSearch = useDebounce(searchInput, 300);
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [assigneeFilter, setAssigneeFilter] = useState('');
  const [sprintFilter, setSprintFilter] = useState('');
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [sortBy, setSortBy] = useState('createdAt');
  const [sortDir, setSortDir] = useState('desc');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const [members, setMembers] = useState([]);
  const [sprints, setSprints] = useState([]);

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
  const [selectedTaskId, setSelectedTaskId] = useState(null);

  // Load project members and sprints for filters
  useEffect(() => {
    if (!project?.id) return undefined;
    let cancelled = false;

    Promise.all([
      projectService.listProjectMembers(project.id).catch(() => []),
      taskService.listProjectSprints(project.id).catch(() => []),
    ]).then(([membersList, sprintsList]) => {
      if (cancelled) return;
      const map = new Map();
      if (project.manager?.id) map.set(project.manager.id, project.manager);
      if (Array.isArray(membersList)) {
        for (const item of membersList) {
          if (item?.user?.id) map.set(item.user.id, item.user);
        }
      }
      setMembers(Array.from(map.values()));
      setSprints(Array.isArray(sprintsList) ? sprintsList : []);
    });

    return () => {
      cancelled = true;
    };
  }, [project?.id, project?.manager]);

  const fetchTasks = useCallback(async () => {
    if (!project?.id) return;
    setLoading(true);
    setError(null);
    try {
      const response = await taskService.listTasks({
        projectId: project.id,
        search: debouncedSearch || undefined,
        status: statusFilter || undefined,
        priority: priorityFilter || undefined,
        assigneeId: assigneeFilter ? Number(assigneeFilter) : undefined,
        sprintId: sprintFilter ? Number(sprintFilter) : undefined,
        overdueOnly,
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
      setError({ title: 'Unable to load project tasks', message: parsed.message });
    } finally {
      setLoading(false);
    }
  }, [
    project?.id,
    debouncedSearch,
    statusFilter,
    priorityFilter,
    assigneeFilter,
    sprintFilter,
    overdueOnly,
    page,
    size,
    sortBy,
    sortDir,
  ]);

  useEffect(() => {
    fetchTasks();
  }, [fetchTasks]);

  // Reset page to 0 when search changes
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  const assigneeOptions = useMemo(() => {
    const opts = [{ value: '', label: 'All Assignees' }];
    for (const member of members) {
      opts.push({ value: String(member.id), label: member.fullName });
    }
    return opts;
  }, [members]);

  const sprintOptions = useMemo(() => {
    const opts = [{ value: '', label: 'All Sprints' }];
    for (const sprint of sprints) {
      opts.push({ value: String(sprint.id), label: `${sprint.name} (${sprint.status})` });
    }
    return opts;
  }, [sprints]);

  const hasActiveFilters = Boolean(
    searchInput.trim() ||
      statusFilter ||
      priorityFilter ||
      assigneeFilter ||
      sprintFilter ||
      overdueOnly
  );

  const handleClearFilters = () => {
    setSearchInput('');
    setStatusFilter('');
    setPriorityFilter('');
    setAssigneeFilter('');
    setSprintFilter('');
    setOverdueOnly(false);
    setPage(0);
  };

  const columns = [
    {
      key: 'taskKey',
      label: 'Key',
      sortable: true,
      render: (row) => (
        <div className="inline-flex items-center gap-1.5 whitespace-nowrap">
          <span className="text-xs font-mono font-medium text-brand-600">{row.taskKey}</span>
          {row.parentTaskId && (
            <span
              className="text-ink-faint"
              title="Subtask"
              aria-label="Subtask"
            >
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
            {row.sprintName ? (
              <span className="truncate">{row.sprintName}</span>
            ) : (
              <span>Backlog</span>
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
        onPageChange={(nextPage) => setPage(nextPage)}
        onSizeChange={(nextSize) => {
          setSize(nextSize);
          setPage(0);
        }}
      />
    ) : null;

  return (
    <div className="space-y-4">
      {(isArchived || isCompleted) && canManageProject && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 flex items-center gap-2.5 text-xs text-amber-700">
          <Lock className="h-4 w-4 text-amber-600 shrink-0" aria-hidden="true" />
          <span>
            {isArchived
              ? 'This project is archived. Tasks cannot be created or modified until reactivated.'
              : 'This project is marked Completed. New tasks cannot be created while completed.'}
          </span>
        </div>
      )}

      {/* Action & Count Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-ink-muted">
          <span className="font-mono tabular-nums text-ink font-medium">
            {pageData.totalElements}
          </span>{' '}
          {pageData.totalElements === 1 ? 'task' : 'tasks'} in {project.projectKey}
        </p>

        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={fetchTasks}
            loading={loading}
            leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
          >
            Refresh
          </Button>
          {canCreateTask && (
            <Button
              size="sm"
              onClick={() => setCreateOpen(true)}
              leftIcon={<Plus className="h-4 w-4" />}
            >
              Create Task
            </Button>
          )}
        </div>
      </div>

      {/* Filters */}
      <TaskFilters
        searchValue={searchInput}
        onSearchChange={setSearchInput}
        statusValue={statusFilter}
        onStatusChange={(val) => {
          setStatusFilter(val);
          setPage(0);
        }}
        priorityValue={priorityFilter}
        onPriorityChange={(val) => {
          setPriorityFilter(val);
          setPage(0);
        }}
        assigneeValue={assigneeFilter}
        onAssigneeChange={(val) => {
          setAssigneeFilter(val);
          setPage(0);
        }}
        assigneeOptions={assigneeOptions}
        sprintValue={sprintFilter}
        onSprintChange={(val) => {
          setSprintFilter(val);
          setPage(0);
        }}
        sprintOptions={sprintOptions}
        overdueOnly={overdueOnly}
        onOverdueOnlyChange={(val) => {
          setOverdueOnly(val);
          setPage(0);
        }}
        sortValue={`${sortBy}:${sortDir}`}
        onSortChange={(val) => {
          const [field, dir] = val.split(':');
          setSortBy(field);
          setSortDir(dir);
          setPage(0);
        }}
        sortOptions={SORT_OPTIONS}
        onClear={handleClearFilters}
        hasActiveFilters={hasActiveFilters}
      />

      {/* Task DataTable */}
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
        onSort={(nextField, nextDir) => {
          setSortBy(nextField);
          setSortDir(nextDir);
          setPage(0);
        }}
        onRowClick={(row) => setSelectedTaskId(row.id)}
        emptyState={{
          icon: CheckSquare,
          title: hasActiveFilters ? 'No tasks match these filters' : 'No tasks in this project yet',
          description: hasActiveFilters
            ? 'Adjust or clear your active search and filter criteria.'
            : canCreateTask
              ? 'Create the first task to start tracking deliverables and Kanban workflow.'
              : 'Tasks created by the Project Manager or Administrator will appear here.',
          action: hasActiveFilters ? (
            <Button variant="secondary" size="sm" onClick={handleClearFilters}>
              Clear Filters
            </Button>
          ) : canCreateTask ? (
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
      {canCreateTask && (
        <TaskFormModal
          isOpen={createOpen}
          onClose={() => setCreateOpen(false)}
          mode="create"
          project={project}
          canManageProject={canManageProject}
          onSaved={() => {
            fetchTasks();
            onProjectMutated?.();
          }}
        />
      )}

      {/* Task Detail Modal */}
      <TaskDetailModal
        isOpen={Boolean(selectedTaskId)}
        onClose={() => setSelectedTaskId(null)}
        taskId={selectedTaskId}
        project={project}
        canManageProject={canManageProject}
        onSelectTask={(nextId) => setSelectedTaskId(nextId)}
        onTaskChanged={() => {
          fetchTasks();
          onProjectMutated?.();
        }}
      />
    </div>
  );
}
