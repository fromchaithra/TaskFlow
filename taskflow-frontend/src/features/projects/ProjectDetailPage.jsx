import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  Bug,
  Calendar,
  CheckSquare,
  Columns3,
  IterationCw,
  LayoutDashboard,
  RefreshCw,
  Settings,
  Users,
} from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { useAuth } from '../../hooks/useAuth';
import { projectService } from '../../services/projectService';
import { ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateRange } from '../../utils/formatters';
import { ProjectStatusBadge } from './components/ProjectStatusBadge';
import { ProjectOverviewTab } from './ProjectOverviewTab';
import { ProjectMembersTab } from './ProjectMembersTab';
import { ProjectSettingsTab } from './ProjectSettingsTab';
import { ProjectTasksTab } from '../tasks/ProjectTasksTab';
import { ProjectKanbanTab } from '../tasks/ProjectKanbanTab';
import { ProjectSprintsTab } from '../sprints/ProjectSprintsTab';
import { ProjectBugsTab } from '../bugs/ProjectBugsTab';

export function ProjectDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const { user, hasRole } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const isAdmin = hasRole(ROLES.ADMIN);
  const isProjectManagerRole = hasRole(ROLES.PROJECT_MANAGER);

  const canManage = useMemo(() => {
    if (!project || !user) return false;
    if (isAdmin) return true;
    return isProjectManagerRole && project.manager?.id === user.id;
  }, [project, user, isAdmin, isProjectManagerRole]);

  const rawTab = searchParams.get('tab') || 'overview';
  const activeTab = useMemo(() => {
    if (rawTab === 'tasks') return 'tasks';
    if (rawTab === 'board') return 'board';
    if (rawTab === 'sprints') return 'sprints';
    if (rawTab === 'bugs') return 'bugs';
    if (rawTab === 'members') return 'members';
    if (rawTab === 'settings' && canManage) return 'settings';
    return 'overview';
  }, [rawTab, canManage]);

  const backToProjects = location.state?.fromSearch
    ? `/projects${location.state.fromSearch}`
    : '/projects';

  const loadProject = useCallback(
    async ({ silent = false } = {}) => {
      if (!id) return;
      if (silent) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      try {
        const data = await projectService.getProjectById(id);
        setProject(data);
      } catch (err) {
        const parsed = parseApiError(err);
        setError({
          status: parsed.status,
          title:
            parsed.status === 403
              ? 'Access denied to this project'
              : parsed.status === 404
                ? 'Project not found'
                : 'Unable to load project details',
          message: parsed.message,
        });
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [id]
  );

  useEffect(() => {
    loadProject({ silent: false });
  }, [loadProject]);

  const handleTabChange = (nextTab) => {
    const next = new URLSearchParams(searchParams);
    if (nextTab === 'overview') {
      next.delete('tab');
    } else {
      next.set('tab', nextTab);
    }
    setSearchParams(next, { replace: true });
  };

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="space-y-3 pb-5 border-b border-line">
          <Skeleton className="h-4 w-28" />
          <div className="flex items-center gap-3">
            <Skeleton className="h-6 w-16" />
            <Skeleton className="h-7 w-64" />
            <Skeleton className="h-6 w-20" />
          </div>
          <Skeleton className="h-4 w-96 max-w-full" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {Array.from({ length: 4 }).map((_, idx) => (
            <Skeleton key={`kpi-skel-${idx}`} className="h-28 w-full rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  if (error || !project) {
    return (
      <div className="space-y-5">
        <Link
          to={backToProjects}
          className="inline-flex items-center gap-1.5 text-xs font-medium text-ink-muted hover:text-ink transition-colors"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          Back to Projects
        </Link>

        <ErrorState
          title={error?.title || 'Project unavailable'}
          description={error?.message || 'This project could not be retrieved.'}
          onRetry={error?.status === 403 || error?.status === 404 ? null : () => loadProject()}
        />

        <div className="flex justify-center">
          <Link to={backToProjects}>
            <Button variant="secondary" size="sm" leftIcon={<ArrowLeft className="h-3.5 w-3.5" />}>
              Return to Projects
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  const memberCount = Number(project.memberCount ?? project.members?.length ?? 0);
  const totalTasks = Number(project.totalTasks ?? 0);

  const activeTabs = [
    { id: 'overview', label: 'Overview', icon: LayoutDashboard },
    { id: 'tasks', label: 'Tasks', icon: CheckSquare, count: totalTasks },
    { id: 'board', label: 'Board', icon: Columns3 },
    { id: 'sprints', label: 'Sprints', icon: IterationCw },
    { id: 'members', label: 'Members', icon: Users, count: memberCount },
    ...(canManage ? [{ id: 'settings', label: 'Settings', icon: Settings }] : []),
    { id: 'bugs', label: 'Bugs', icon: Bug },
  ];

  return (
    <div className="space-y-6">
      {/* Project header */}
      <div className="space-y-4">
        <Link
          to={backToProjects}
          className="inline-flex items-center gap-1.5 text-xs font-medium text-ink-muted hover:text-ink transition-colors"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          Back to Projects
        </Link>

        <div className="rounded-xl border border-line bg-surface p-5 shadow-card">
          <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <div className="min-w-0 space-y-2.5">
              <div className="flex flex-wrap items-center gap-2.5">
                <Badge tone="cyan" mono>{project.projectKey}</Badge>
                <h1 className="text-2xl font-semibold tracking-tight text-ink truncate">
                  {project.name}
                </h1>
                <ProjectStatusBadge status={project.status} />
              </div>
              {project.description?.trim() ? (
                <p className="max-w-3xl text-sm leading-relaxed text-ink-muted line-clamp-2">
                  {project.description}
                </p>
              ) : (
                <p className="text-sm italic text-ink-faint">No description provided.</p>
              )}
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 pt-1 text-xs text-ink-muted">
                <span className="inline-flex items-center gap-1.5">
                  <Calendar className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                  {formatDateRange(project.startDate, project.endDate)}
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <Users className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                  {memberCount} {memberCount === 1 ? 'member' : 'members'}
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <CheckSquare className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                  {totalTasks} {totalTasks === 1 ? 'task' : 'tasks'}
                </span>
              </div>
            </div>

            <Button
              variant="secondary"
              size="sm"
              loading={refreshing}
              onClick={() => loadProject({ silent: true })}
              leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
            >
              Refresh
            </Button>
          </div>
        </div>
      </div>

      {/* Project Workspace Tab Navigation */}
      <div className="overflow-x-auto rounded-xl border border-line bg-surface p-1 shadow-card">
        <nav className="flex items-center gap-1 min-w-max" aria-label="Project workspace sections">
          {activeTabs.map((tab) => {
            const Icon = tab.icon;
            const isSelected = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={isSelected}
                onClick={() => handleTabChange(tab.id)}
                className={[
                  'inline-flex items-center gap-2 rounded-lg px-3.5 py-2.5 text-sm font-medium transition-colors whitespace-nowrap',
                  isSelected
                    ? 'bg-brand-50 text-brand-700'
                    : 'text-ink-muted hover:bg-subtle hover:text-ink',
                ].join(' ')}
              >
                <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span>{tab.label}</span>
                {tab.count != null && (
                  <span className="px-1.5 py-0.5 rounded bg-subtle text-[11px] font-mono tabular-nums text-ink-muted">
                    {tab.count}
                  </span>
                )}
              </button>
            );
          })}

        </nav>
      </div>

      {/* Tab Panels */}
      <div>
        {activeTab === 'overview' && (
          <ProjectOverviewTab
            project={project}
            canManage={canManage}
            onSwitchTab={handleTabChange}
          />
        )}

        {activeTab === 'tasks' && (
          <ProjectTasksTab
            project={project}
            canManageProject={canManage}
            onProjectMutated={() => loadProject({ silent: true })}
          />
        )}

        {activeTab === 'board' && (
          <ProjectKanbanTab
            project={project}
            canManageProject={canManage}
            onProjectMutated={() => loadProject({ silent: true })}
          />
        )}

        {activeTab === 'sprints' && (
          <ProjectSprintsTab
            project={project}
            canManageProject={canManage}
          />
        )}

        {activeTab === 'bugs' && (
          <ProjectBugsTab project={project} />
        )}

        {activeTab === 'members' && (
          <ProjectMembersTab
            project={project}
            canManage={canManage}
            onProjectMutated={() => loadProject({ silent: true })}
          />
        )}

        {activeTab === 'settings' && canManage && (
          <ProjectSettingsTab
            project={project}
            onProjectUpdated={(updated) => {
              if (updated && updated.id) {
                setProject(updated);
              } else {
                loadProject({ silent: true });
              }
            }}
          />
        )}
      </div>
    </div>
  );
}
