import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  CalendarPlus,
  IterationCw,
  LayoutGrid,
  List,
  Lock,
  RefreshCw,
  Search,
} from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Select } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { EmptyState, ErrorState, Skeleton } from '../../components/common/Loading';
import { useToast } from '../../hooks/useToast';
import { sprintService } from '../../services/sprintService';
import { parseApiError } from '../../utils/errorHandler';
import { SprintCard } from './components/SprintCard';
import { SprintList } from './components/SprintList';
import { SprintDetailModal } from './SprintDetailModal';
import { SprintFormModal } from './SprintFormModal';
import {
  ALL_SPRINT_STATUSES,
  canCreateSprintInProject,
  formatSprintStatus,
  isSprintEditable,
  SPRINT_STATUS,
  SPRINT_STATUS_META,
} from './sprintConstants';

const STATUS_FILTER_OPTIONS = [
  { value: 'ALL', label: 'All statuses' },
  ...ALL_SPRINT_STATUSES.map((status) => ({
    value: status,
    label: formatSprintStatus(status),
  })),
];

function SprintStats({ sprints }) {
  const counts = useMemo(() => {
    const result = {
      [SPRINT_STATUS.PLANNED]: 0,
      [SPRINT_STATUS.ACTIVE]: 0,
      [SPRINT_STATUS.COMPLETED]: 0,
      [SPRINT_STATUS.CANCELLED]: 0,
    };
    for (const sprint of sprints) {
      if (result[sprint.status] !== undefined) result[sprint.status] += 1;
    }
    return result;
  }, [sprints]);

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
      {ALL_SPRINT_STATUSES.map((status) => {
        const meta = SPRINT_STATUS_META[status];
        return (
          <div
            key={status}
            className="rounded-xl border border-line bg-surface px-4 py-3 shadow-card"
          >
            <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-ink-faint">
              <span className={`h-1.5 w-1.5 rounded-full ${meta.tone === 'cyan' ? 'bg-brand-500' : meta.tone === 'emerald' ? 'bg-emerald-400' : meta.tone === 'steel' ? 'bg-sky-500' : 'bg-red-400'}`} />
              <span>{meta.label}</span>
            </div>
            <p className="mt-1 text-xl font-semibold tabular-nums text-ink">
              {counts[status]}
            </p>
          </div>
        );
      })}
    </div>
  );
}

/**
 * Project sprint workspace embedded in ProjectDetailPage and the global /sprints route.
 * Uses only SprintController endpoints through sprintService.
 */
export function ProjectSprintsTab({ project, canManageProject = false }) {
  const toast = useToast();

  const [sprints, setSprints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [viewMode, setViewMode] = useState('grid');

  const [createOpen, setCreateOpen] = useState(false);
  const [editingSprint, setEditingSprint] = useState(null);
  const [detailState, setDetailState] = useState(null);
  const [detailRefreshToken, setDetailRefreshToken] = useState(0);

  const projectId = project?.id;
  const projectStatus = project?.status;
  const canCreate = canManageProject && canCreateSprintInProject(projectStatus);

  const loadSprints = useCallback(
    async ({ silent = false } = {}) => {
      if (!projectId) return;
      if (silent) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      try {
        const data = await sprintService.listProjectSprints(projectId);
        setSprints(Array.isArray(data) ? data : []);
      } catch (err) {
        const parsed = parseApiError(err);
        setError({
          title:
            parsed.status === 403
              ? 'Access denied to project sprints'
              : parsed.status === 404
                ? 'Project not found'
                : 'Unable to load sprints',
          message: parsed.message,
        });
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [projectId]
  );

  useEffect(() => {
    loadSprints();
  }, [loadSprints]);

  const filteredSprints = useMemo(() => {
    const term = search.trim().toLowerCase();
    return sprints.filter((sprint) => {
      if (statusFilter !== 'ALL' && sprint.status !== statusFilter) return false;
      if (!term) return true;
      return (
        sprint.name?.toLowerCase().includes(term) ||
        sprint.goal?.toLowerCase().includes(term) ||
        sprint.projectKey?.toLowerCase().includes(term)
      );
    });
  }, [sprints, search, statusFilter]);

  const handleCreate = async (payload) => {
    const created = await sprintService.createSprint(projectId, payload);
    toast.success(`Sprint “${created.name}” was created.`, 'Sprint created');
    await loadSprints({ silent: true });
    return created;
  };

  const handleUpdate = async (payload) => {
    const target = editingSprint;
    if (!target) return null;
    const updated = await sprintService.updateSprint(projectId, target.id, payload);
    toast.success(`Sprint “${updated.name}” was updated.`, 'Sprint updated');
    setSprints((prev) =>
      prev.map((sprint) => (sprint.id === updated.id ? { ...sprint, ...updated } : sprint))
    );
    await loadSprints({ silent: true });
    if (detailState?.sprint?.id === updated.id) {
      setDetailRefreshToken((prev) => prev + 1);
    }
    return updated;
  };

  const handleSprintChanged = (updated) => {
    if (!updated) return;
    setSprints((prev) =>
      prev.map((sprint) => (sprint.id === updated.id ? { ...sprint, ...updated } : sprint))
    );
    loadSprints({ silent: true });
  };

  const openDetail = (sprint, tab = 'overview') => {
    setDetailState({ sprint, tab });
  };

  const openEdit = (sprint) => {
    setDetailState(null);
    setEditingSprint(sprint);
  };

  const toolbar = (
    <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-3 rounded-xl border border-line bg-surface p-3 shadow-card">
      <div className="flex flex-col sm:flex-row sm:items-center gap-2.5 w-full lg:max-w-2xl">
        <Input
          aria-label="Search sprints"
          placeholder="Search sprints by name or goal..."
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          leftIcon={<Search className="h-4 w-4" />}
          className="sm:max-w-xs"
        />
        <div className="w-full sm:w-44">
          <Select
            aria-label="Filter sprints by status"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            options={STATUS_FILTER_OPTIONS}
          />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 shrink-0">
        <div className="inline-flex items-center rounded-lg border border-line bg-canvas p-0.5">
          <button
            type="button"
            aria-label="Card view"
            aria-pressed={viewMode === 'grid'}
            onClick={() => setViewMode('grid')}
            className={`h-8 w-8 inline-flex items-center justify-center rounded-md transition-colors ${
              viewMode === 'grid'
                ? 'bg-subtle text-ink'
                : 'text-ink-muted hover:text-ink'
            }`}
          >
            <LayoutGrid className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
          <button
            type="button"
            aria-label="List view"
            aria-pressed={viewMode === 'list'}
            onClick={() => setViewMode('list')}
            className={`h-8 w-8 inline-flex items-center justify-center rounded-md transition-colors ${
              viewMode === 'list'
                ? 'bg-subtle text-ink'
                : 'text-ink-muted hover:text-ink'
            }`}
          >
            <List className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
        </div>

        <Button
          variant="secondary"
          size="sm"
          loading={refreshing}
          onClick={() => loadSprints({ silent: true })}
          leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
        >
          Refresh
        </Button>

        {canCreate && (
          <Button
            size="sm"
            onClick={() => setCreateOpen(true)}
            leftIcon={<CalendarPlus className="h-3.5 w-3.5" />}
          >
            New sprint
          </Button>
        )}
      </div>
    </div>
  );

  if (loading) {
    return (
      <div className="space-y-4">
        {toolbar}
        <Skeleton className="h-24 w-full rounded-xl" />
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, idx) => (
            <Skeleton key={`sprint-skel-${idx}`} className="h-52 w-full rounded-xl" />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-4">
        {toolbar}
        <ErrorState
          title={error.title}
          description={error.message}
          onRetry={() => loadSprints()}
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {sprints.length > 0 && <SprintStats sprints={sprints} />}

      {toolbar}

      {!canCreate && sprints.length === 0 && (
        <p className="inline-flex items-center gap-1.5 text-[11px] text-ink-faint">
          <Lock className="h-3.5 w-3.5" aria-hidden="true" />
          {projectStatus === 'ARCHIVED' || projectStatus === 'COMPLETED'
            ? `Sprints cannot be created in a project with status ${projectStatus.toLowerCase()}.`
            : 'Only an admin or the assigned project manager can create sprints.'}
        </p>
      )}

      {sprints.length === 0 ? (
        <EmptyState
          icon={IterationCw}
          title="No sprints in this project yet"
          description={
            canCreate
              ? 'Plan the first delivery window, define a goal, and start adding backlog tasks.'
              : 'Sprints created by the project manager will appear here.'
          }
          action={
            canCreate ? (
              <Button
                size="sm"
                onClick={() => setCreateOpen(true)}
                leftIcon={<CalendarPlus className="h-4 w-4" />}
              >
                Create sprint
              </Button>
            ) : null
          }
        />
      ) : filteredSprints.length === 0 ? (
        <EmptyState
          icon={Search}
          title="No sprints match these filters"
          description="Try a different status or clear the search to see all project sprints."
          action={
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                setSearch('');
                setStatusFilter('ALL');
              }}
            >
              Clear filters
            </Button>
          }
        />
      ) : viewMode === 'grid' ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filteredSprints.map((sprint) => (
            <SprintCard
              key={sprint.id}
              sprint={sprint}
              canEdit={canManageProject && isSprintEditable(sprint, projectStatus)}
              onOpen={openDetail}
              onEdit={openEdit}
            />
          ))}
        </div>
      ) : (
        <SprintList
          sprints={filteredSprints}
          canEdit={(sprint) => canManageProject && isSprintEditable(sprint, projectStatus)}
          onOpen={openDetail}
          onEdit={openEdit}
        />
      )}

      {canCreate && (
        <SprintFormModal
          isOpen={createOpen}
          onClose={() => setCreateOpen(false)}
          onSubmit={handleCreate}
          project={project}
        />
      )}

      <SprintFormModal
        isOpen={Boolean(editingSprint)}
        onClose={() => setEditingSprint(null)}
        onSubmit={handleUpdate}
        sprint={editingSprint}
        project={project}
      />

      <SprintDetailModal
        isOpen={Boolean(detailState)}
        onClose={() => setDetailState(null)}
        sprintId={detailState?.sprint?.id}
        initialSprint={detailState?.sprint}
        initialTab={detailState?.tab || 'overview'}
        projectStatus={projectStatus}
        canManage={canManageProject}
        refreshToken={detailRefreshToken}
        onSprintChanged={handleSprintChanged}
        onEdit={openEdit}
      />
    </div>
  );
}
