import { ArrowUpRight, Calendar, Users } from 'lucide-react';
import { Avatar } from '../../../components/common/Avatar';
import { Badge } from '../../../components/common/Badge';
import { formatDateRange } from '../../../utils/formatters';
import { ProjectStatusBadge } from './ProjectStatusBadge';

/**
 * Single-elevation project card consuming exact ProjectSummaryResponse fields:
 * { id, projectKey, name, description, status, startDate, endDate, manager, memberCount }
 * No progress or task values are shown because the list API does not provide them.
 */
export function ProjectCard({ project, onOpen }) {
  const memberCount = Number(project.memberCount ?? 0);

  return (
    <article
      onClick={() => onOpen?.(project)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onOpen?.(project);
        }
      }}
      role="button"
      tabIndex={0}
      aria-label={`Open project ${project.name}`}
      className="group relative flex h-full flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-card transition-colors duration-150 hover:border-brand-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/50"
    >
      <span className="absolute inset-x-0 top-0 h-0.5 bg-brand-500 opacity-0 transition-opacity group-hover:opacity-100" aria-hidden="true" />

      <div className="flex flex-1 flex-col gap-4 p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone="cyan" mono>{project.projectKey}</Badge>
            <ProjectStatusBadge status={project.status} />
          </div>
          <ArrowUpRight
            className="h-4 w-4 shrink-0 text-ink-faint transition-colors group-hover:text-brand-600"
            aria-hidden="true"
          />
        </div>

        <div className="min-w-0 flex-1">
          <h3 className="truncate text-[15px] font-semibold tracking-tight text-ink transition-colors group-hover:text-brand-700">
            {project.name}
          </h3>
          <p className="mt-1.5 line-clamp-2 min-h-[2.25rem] text-xs leading-relaxed text-ink-muted">
            {project.description?.trim() ? project.description : 'No project description provided.'}
          </p>
        </div>

        <div className="space-y-3 border-t border-line pt-3">
          <div className="flex items-center justify-between gap-2 text-xs text-ink-muted">
            <span className="inline-flex min-w-0 items-center gap-1.5 truncate">
              <Calendar className="h-3.5 w-3.5 shrink-0 text-ink-faint" aria-hidden="true" />
              <span className="truncate">{formatDateRange(project.startDate, project.endDate)}</span>
            </span>
            <span className="inline-flex shrink-0 items-center gap-1 font-mono tabular-nums">
              <Users className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
              {memberCount}
            </span>
          </div>

          <div className="flex items-center justify-between gap-2">
            {project.manager ? (
              <div className="flex min-w-0 items-center gap-2">
                <Avatar
                  name={project.manager.fullName}
                  color={project.manager.avatarColor}
                  size="xs"
                />
                <div className="min-w-0">
                  <p className="truncate text-xs font-medium text-ink">
                    {project.manager.fullName}
                  </p>
                  <p className="truncate text-[11px] text-ink-faint">Project Manager</p>
                </div>
              </div>
            ) : (
              <span className="text-xs text-ink-faint">Unassigned manager</span>
            )}
            <span className="text-[11px] font-medium text-brand-600 opacity-0 transition-opacity group-hover:opacity-100">
              Open
            </span>
          </div>
        </div>
      </div>
    </article>
  );
}
