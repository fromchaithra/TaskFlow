import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowUpRight, Columns3, FolderKanban } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Select } from '../../components/common/FormControls';
import { EmptyState, ErrorState, Skeleton } from '../../components/common/Loading';
import { PageHeader } from '../../components/common/PageHeader';
import { useAuth } from '../../hooks/useAuth';
import { projectService } from '../../services/projectService';
import { ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { ProjectStatusBadge } from '../projects/components/ProjectStatusBadge';
import { ProjectKanbanTab } from './ProjectKanbanTab';

/**
 * Global Kanban Board Route (/kanban).
 * Lets the user select any accessible project (or deep-link via ?projectId=)
 * and renders the full interactive ProjectKanbanTab.
 */
export function TaskBoardPage() {
  const { user, hasRole } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const isAdmin = hasRole(ROLES.ADMIN);
  const isPmRole = hasRole(ROLES.PROJECT_MANAGER);

  const [projects, setProjects] = useState([]);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [projectsError, setProjectsError] = useState(null);

  const [selectedProject, setSelectedProject] = useState(null);
  const [loadingProjectDetail, setLoadingProjectDetail] = useState(false);

  const requestedProjectId = searchParams.get('projectId') || '';

  const loadProjects = useCallback(async () => {
    setLoadingProjects(true);
    setProjectsError(null);
    try {
      const res = await projectService.listProjects({
        page: 0,
        size: 100,
        sortBy: 'updatedAt',
        sortDir: 'desc',
      });
      const list = Array.isArray(res?.content) ? res.content : [];
      setProjects(list);

      if (list.length > 0) {
        const hasRequested =
          requestedProjectId && list.some((p) => String(p.id) === String(requestedProjectId));
        if (!hasRequested) {
          const preferred = list.find((p) => p.status === 'ACTIVE') || list[0];
          const next = new URLSearchParams(searchParams);
          next.set('projectId', String(preferred.id));
          setSearchParams(next, { replace: true });
        }
      }
    } catch (err) {
      setProjectsError(parseApiError(err).message);
    } finally {
      setLoadingProjects(false);
    }
  }, [requestedProjectId, searchParams, setSearchParams]);

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  useEffect(() => {
    if (!requestedProjectId) {
      setSelectedProject(null);
      return undefined;
    }

    let cancelled = false;
    setLoadingProjectDetail(true);

    projectService
      .getProjectById(requestedProjectId)
      .then((detail) => {
        if (!cancelled) setSelectedProject(detail);
      })
      .catch(() => {
        if (!cancelled) setSelectedProject(null);
      })
      .finally(() => {
        if (!cancelled) setLoadingProjectDetail(false);
      });

    return () => {
      cancelled = true;
    };
  }, [requestedProjectId]);

  const projectOptions = useMemo(() => {
    return projects.map((p) => ({
      value: String(p.id),
      label: `${p.projectKey} — ${p.name}`,
    }));
  }, [projects]);

  const canManageProject = useMemo(() => {
    if (!selectedProject || !user) return false;
    if (isAdmin) return true;
    return isPmRole && selectedProject.manager?.id === user.id;
  }, [selectedProject, user, isAdmin, isPmRole]);

  const handleProjectChange = (nextProjectId) => {
    const next = new URLSearchParams(searchParams);
    next.set('projectId', nextProjectId);
    setSearchParams(next, { replace: true });
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Kanban Board"
        description="Visual workflow columns synced directly with backend task statuses and role permissions."
        meta={selectedProject ? <ProjectStatusBadge status={selectedProject.status} /> : null}
        actions={
          projects.length > 0 ? (
            <div className="flex flex-wrap items-center gap-2.5 rounded-xl border border-line bg-surface p-2 shadow-card">
              <div className="w-64">
                <Select
                  aria-label="Select project board"
                  value={requestedProjectId}
                  onChange={(e) => handleProjectChange(e.target.value)}
                  options={projectOptions}
                />
              </div>
              {selectedProject && (
                <Link to={`/projects/${selectedProject.id}?tab=board`}>
                  <Button
                    variant="secondary"
                    size="sm"
                    rightIcon={<ArrowUpRight className="h-3.5 w-3.5" />}
                  >
                    Open Project
                  </Button>
                </Link>
              )}
            </div>
          ) : null
        }
      />

      {loadingProjects ? (
        <div className="flex gap-3.5 overflow-x-auto pb-2">
          {Array.from({ length: 5 }).map((_, idx) => (
            <Skeleton key={`board-col-skel-${idx}`} className="w-[300px] h-[440px] shrink-0 rounded-xl" />
          ))}
        </div>
      ) : projectsError ? (
        <ErrorState
          title="Unable to load accessible projects"
          description={projectsError}
          onRetry={loadProjects}
        />
      ) : projects.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title="No projects available for Kanban view"
          description="You need access to at least one project to open a Kanban board."
          action={
            <Link to="/projects">
              <Button size="sm" leftIcon={<Columns3 className="h-4 w-4" />}>
                Go to Projects
              </Button>
            </Link>
          }
        />
      ) : loadingProjectDetail || !selectedProject ? (
        <div className="flex gap-3.5 overflow-x-auto pb-2">
          {Array.from({ length: 5 }).map((_, idx) => (
            <Skeleton key={`board-detail-skel-${idx}`} className="w-[300px] h-[440px] shrink-0 rounded-xl" />
          ))}
        </div>
      ) : (
        <ProjectKanbanTab
          project={selectedProject}
          canManageProject={canManageProject}
        />
      )}
    </div>
  );
}
