import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowUpRight, FolderKanban, IterationCw } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Select } from '../../components/common/FormControls';
import { EmptyState, ErrorState, Skeleton } from '../../components/common/Loading';
import { PageHeader } from '../../components/common/PageHeader';
import { useAuth } from '../../hooks/useAuth';
import { projectService } from '../../services/projectService';
import { ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { ProjectStatusBadge } from '../projects/components/ProjectStatusBadge';
import { ProjectSprintsTab } from './ProjectSprintsTab';

/**
 * Global sprint workspace (/sprints).
 * Lets the user pick any accessible project (or deep-link via ?projectId=) and reuses
 * the same ProjectSprintsTab embedded in ProjectDetailPage.
 */
export function SprintsPage() {
  const { user, hasRole } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const isAdmin = hasRole(ROLES.ADMIN);
  const isPmRole = hasRole(ROLES.PROJECT_MANAGER);

  const [projects, setProjects] = useState([]);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [projectsError, setProjectsError] = useState(null);

  const [selectedProject, setSelectedProject] = useState(null);
  const [loadingProject, setLoadingProject] = useState(false);
  const [projectError, setProjectError] = useState(null);
  const [projectReloadToken, setProjectReloadToken] = useState(0);

  const requestedProjectId = searchParams.get('projectId') || '';

  const loadProjects = useCallback(async () => {
    setLoadingProjects(true);
    setProjectsError(null);

    try {
      const response = await projectService.listProjects({
        page: 0,
        size: 100,
        sortBy: 'updatedAt',
        sortDir: 'desc',
      });
      const list = Array.isArray(response?.content) ? response.content : [];
      setProjects(list);
    } catch (err) {
      setProjectsError(parseApiError(err).message);
    } finally {
      setLoadingProjects(false);
    }
  }, []);

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  useEffect(() => {
    if (loadingProjects || projectsError || projects.length === 0) return;
    const hasRequested =
      requestedProjectId && projects.some((p) => String(p.id) === String(requestedProjectId));
    if (hasRequested) return;

    const preferred = projects.find((p) => p.status === 'ACTIVE') || projects[0];
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set('projectId', String(preferred.id));
        return next;
      },
      { replace: true }
    );
  }, [loadingProjects, projectsError, projects, requestedProjectId, setSearchParams]);

  useEffect(() => {
    if (!requestedProjectId) {
      setSelectedProject(null);
      setProjectError(null);
      return undefined;
    }

    let cancelled = false;
    setLoadingProject(true);
    setProjectError(null);

    projectService
      .getProjectById(requestedProjectId)
      .then((detail) => {
        if (!cancelled) setSelectedProject(detail);
      })
      .catch((err) => {
        if (cancelled) return;
        const parsed = parseApiError(err);
        setSelectedProject(null);
        setProjectError({
          title:
            parsed.status === 403
              ? 'Access denied to this project'
              : parsed.status === 404
                ? 'Project not found'
                : 'Unable to load project',
          message: parsed.message,
        });
      })
      .finally(() => {
        if (!cancelled) setLoadingProject(false);
      });

    return () => {
      cancelled = true;
    };
  }, [requestedProjectId, projectReloadToken]);

  const projectOptions = useMemo(
    () =>
      projects.map((project) => ({
        value: String(project.id),
        label: `${project.projectKey} — ${project.name}`,
      })),
    [projects]
  );

  const canManageProject = useMemo(() => {
    if (!selectedProject || !user) return false;
    if (isAdmin) return true;
    return isPmRole && selectedProject.manager?.id === user.id;
  }, [selectedProject, user, isAdmin, isPmRole]);

  const handleProjectChange = (nextProjectId) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set('projectId', nextProjectId);
        return next;
      },
      { replace: true }
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Sprints"
        description="Plan delivery windows, manage sprint scope, and run lifecycle transitions against real backend sprint APIs."
        meta={
          selectedProject ? (
            <ProjectStatusBadge status={selectedProject.status} />
          ) : null
        }
        actions={
          projects.length > 0 ? (
            <div className="flex flex-wrap items-center gap-2.5 rounded-xl border border-line bg-surface p-2 shadow-card">
              <div className="w-64">
                <Select
                  aria-label="Select project sprints"
                  value={requestedProjectId}
                  onChange={(event) => handleProjectChange(event.target.value)}
                  options={projectOptions}
                />
              </div>
              {selectedProject && (
                <Link to={`/projects/${selectedProject.id}?tab=sprints`}>
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
        <div className="space-y-4">
          <Skeleton className="h-24 w-full rounded-xl" />
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, idx) => (
              <Skeleton key={`sprint-page-skel-${idx}`} className="h-52 w-full rounded-xl" />
            ))}
          </div>
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
          title="No projects available"
          description="You need access to at least one project to view or plan sprints."
          action={
            <Link to="/projects">
              <Button size="sm" leftIcon={<IterationCw className="h-4 w-4" />}>
                Go to Projects
              </Button>
            </Link>
          }
        />
      ) : projectError ? (
        <ErrorState
          title={projectError.title}
          description={projectError.message}
          onRetry={() => setProjectReloadToken((prev) => prev + 1)}
        />
      ) : loadingProject || !selectedProject ? (
        <div className="space-y-4">
          <Skeleton className="h-24 w-full rounded-xl" />
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, idx) => (
              <Skeleton key={`sprint-project-skel-${idx}`} className="h-52 w-full rounded-xl" />
            ))}
          </div>
        </div>
      ) : (
        <ProjectSprintsTab
          key={selectedProject.id}
          project={selectedProject}
          canManageProject={canManageProject}
        />
      )}
    </div>
  );
}
