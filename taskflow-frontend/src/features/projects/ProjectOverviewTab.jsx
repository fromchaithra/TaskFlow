import {
  AlertTriangle,
  ArrowRight,
  Bug,
  Calendar,
  CheckCircle2,
  Clock,
  Settings,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { ROLE_META } from '../../utils/constants';
import { formatDate, formatDateTime } from '../../utils/formatters';
import { PROJECT_STATUS_META } from './projectConstants';
import { ProjectStatusBadge } from './components/ProjectStatusBadge';

/**
 * Project Overview tab backed strictly by ProjectDetailResponse fields:
 * { id, projectKey, name, description, status, startDate, endDate, manager,
 *   memberCount, totalTasks, completedTasks, overdueTasks, openBugs,
 *   progressPercent, members, createdAt, updatedAt }
 */
export function ProjectOverviewTab({ project, canManage, onSwitchTab }) {
  const totalTasks = Number(project.totalTasks ?? 0);
  const completedTasks = Number(project.completedTasks ?? 0);
  const overdueTasks = Number(project.overdueTasks ?? 0);
  const openBugs = Number(project.openBugs ?? 0);
  const memberCount = Number(project.memberCount ?? project.members?.length ?? 0);
  const progressPercent = Math.min(100, Math.max(0, Number(project.progressPercent ?? 0)));

  const statusMeta = PROJECT_STATUS_META[project.status];
  const previewMembers = Array.isArray(project.members) ? project.members.slice(0, 6) : [];
  const remainingMembers = Math.max(0, memberCount - previewMembers.length);

  return (
    <div className="space-y-6">
      {/* Real backend-computed operational summary strip */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card>
          <CardBody className="p-4 space-y-2.5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-medium text-ink-muted">Task Progress</span>
              <CheckCircle2 className="h-4 w-4 text-brand-600" aria-hidden="true" />
            </div>
            <div className="flex items-baseline justify-between gap-2">
              <span className="text-2xl font-semibold text-ink font-mono tabular-nums">
                {progressPercent.toFixed(0)}%
              </span>
              <span className="text-xs text-ink-muted font-mono tabular-nums">
                {completedTasks} / {totalTasks} done
              </span>
            </div>
            <div className="h-1.5 w-full rounded-full bg-subtle overflow-hidden">
              <div
                className="h-full rounded-full bg-brand-500 transition-all duration-300"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 space-y-1.5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-medium text-ink-muted">Project Members</span>
              <Users className="h-4 w-4 text-sky-600" aria-hidden="true" />
            </div>
            <p className="text-2xl font-semibold text-ink font-mono tabular-nums">
              {memberCount}
            </p>
            <p className="text-xs text-ink-faint">Assigned active collaborators</p>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 space-y-1.5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-medium text-ink-muted">Overdue Tasks</span>
              <AlertTriangle
                className={`h-4 w-4 ${overdueTasks > 0 ? 'text-amber-600' : 'text-ink-faint'}`}
                aria-hidden="true"
              />
            </div>
            <p
              className={`text-2xl font-semibold font-mono tabular-nums ${
                overdueTasks > 0 ? 'text-amber-700' : 'text-ink'
              }`}
            >
              {overdueTasks}
            </p>
            <p className="text-xs text-ink-faint">
              {overdueTasks > 0 ? 'Past target due date' : 'No overdue items'}
            </p>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 space-y-1.5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-medium text-ink-muted">Open Bugs</span>
              <Bug
                className={`h-4 w-4 ${openBugs > 0 ? 'text-red-600' : 'text-ink-faint'}`}
                aria-hidden="true"
              />
            </div>
            <p
              className={`text-2xl font-semibold font-mono tabular-nums ${
                openBugs > 0 ? 'text-red-600' : 'text-ink'
              }`}
            >
              {openBugs}
            </p>
            <p className="text-xs text-ink-faint">
              {openBugs > 0 ? 'Unresolved project defects' : 'No open defects'}
            </p>
          </CardBody>
        </Card>
      </div>

      {/* Main 2-column detail grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 columns: Description & Schedule */}
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader
              title="Project Summary"
              description="Scope definition, lifecycle status, and delivery schedule."
              action={
                canManage && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => onSwitchTab?.('settings')}
                    leftIcon={<Settings className="h-3.5 w-3.5" />}
                  >
                    Edit in Settings
                  </Button>
                )
              }
            />
            <CardBody className="space-y-5">
              <div>
                <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted mb-2">
                  Description
                </h3>
                {project.description?.trim() ? (
                  <p className="text-sm text-ink leading-relaxed whitespace-pre-wrap">
                    {project.description}
                  </p>
                ) : (
                  <p className="text-sm text-ink-faint italic">
                    No description has been added for this project yet.
                  </p>
                )}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-4 border-t border-line">
                <div className="rounded-lg bg-canvas border border-line p-3.5">
                  <span className="inline-flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wider text-ink-muted">
                    <Calendar className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                    Start Date
                  </span>
                  <p className="text-sm font-medium text-ink mt-1 tabular-nums">
                    {formatDate(project.startDate)}
                  </p>
                </div>

                <div className="rounded-lg bg-canvas border border-line p-3.5">
                  <span className="inline-flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wider text-ink-muted">
                    <Calendar className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                    Target End Date
                  </span>
                  <p className="text-sm font-medium text-ink mt-1 tabular-nums">
                    {project.endDate ? formatDate(project.endDate) : 'Ongoing (no end date)'}
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-4 border-t border-line text-xs text-ink-muted">
                <div className="flex items-center gap-2">
                  <Clock className="h-3.5 w-3.5 text-ink-faint shrink-0" aria-hidden="true" />
                  <span>
                    Created:{' '}
                    <span className="text-ink-muted tabular-nums">
                      {formatDateTime(project.createdAt)}
                    </span>
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <Clock className="h-3.5 w-3.5 text-ink-faint shrink-0" aria-hidden="true" />
                  <span>
                    Last updated:{' '}
                    <span className="text-ink-muted tabular-nums">
                      {formatDateTime(project.updatedAt)}
                    </span>
                  </span>
                </div>
              </div>
            </CardBody>
          </Card>

          {/* Compact Roster Preview */}
          <Card>
            <CardHeader
              title="Project Roster"
              description={`${memberCount} ${memberCount === 1 ? 'member' : 'members'} assigned to ${project.projectKey}.`}
              action={
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => onSwitchTab?.('members')}
                  rightIcon={<ArrowRight className="h-3.5 w-3.5" />}
                >
                  View All Members
                </Button>
              }
            />
            <CardBody>
              {previewMembers.length === 0 ? (
                <p className="text-xs text-ink-faint">No members are currently listed.</p>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {previewMembers.map((member) => {
                    const mUser = member.user || {};
                    const roleMeta = ROLE_META[member.projectRole] || {
                      label: member.projectRole,
                      tone: 'neutral',
                    };
                    return (
                      <div
                        key={member.id ?? mUser.id}
                        className="flex items-center justify-between gap-2.5 p-2.5 rounded-lg bg-canvas border border-line"
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <Avatar name={mUser.fullName} color={mUser.avatarColor} size="sm" />
                          <div className="min-w-0">
                            <p className="text-xs font-medium text-ink truncate">
                              {mUser.fullName}
                            </p>
                            <p className="text-[11px] text-ink-faint truncate">{mUser.email}</p>
                          </div>
                        </div>
                        <Badge tone={roleMeta.tone}>{roleMeta.shortLabel || roleMeta.label}</Badge>
                      </div>
                    );
                  })}
                </div>
              )}

              {remainingMembers > 0 && (
                <p className="text-xs text-ink-muted mt-3">
                  +{remainingMembers} more {remainingMembers === 1 ? 'member' : 'members'} in the
                  Members tab.
                </p>
              )}
            </CardBody>
          </Card>
        </div>

        {/* Right column: Manager & Lifecycle State */}
        <div className="space-y-6">
          <Card>
            <CardHeader title="Project Manager" />
            <CardBody>
              {project.manager ? (
                <div className="space-y-4">
                  <div className="flex items-center gap-3">
                    <Avatar
                      name={project.manager.fullName}
                      color={project.manager.avatarColor}
                      size="md"
                    />
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-ink truncate">
                        {project.manager.fullName}
                      </p>
                      <p className="text-xs text-ink-muted truncate">{project.manager.email}</p>
                    </div>
                  </div>

                  <div className="space-y-2 pt-3 border-t border-line text-xs">
                    {project.manager.jobTitle && (
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-ink-faint">Title</span>
                        <span className="text-ink truncate">{project.manager.jobTitle}</span>
                      </div>
                    )}
                    {project.manager.employeeCode && (
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-ink-faint">Employee Code</span>
                        <span className="font-mono text-ink">
                          {project.manager.employeeCode}
                        </span>
                      </div>
                    )}
                    {project.manager.role && (
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-ink-faint">Organization Role</span>
                        <Badge tone={ROLE_META[project.manager.role]?.tone || 'neutral'}>
                          {ROLE_META[project.manager.role]?.label || project.manager.role}
                        </Badge>
                      </div>
                    )}
                  </div>
                </div>
              ) : (
                <p className="text-xs text-ink-faint">No project manager assigned.</p>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Lifecycle State" />
            <CardBody className="space-y-3">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs text-ink-muted">Current Status</span>
                <ProjectStatusBadge status={project.status} />
              </div>

              {statusMeta?.description && (
                <p className="text-xs text-ink-muted leading-relaxed">{statusMeta.description}</p>
              )}

              <div className="pt-3 border-t border-line flex items-center gap-2 text-xs text-ink-muted">
                <ShieldCheck className="h-4 w-4 text-brand-600 shrink-0" aria-hidden="true" />
                <span>
                  {canManage
                    ? 'You have management permissions for this project.'
                    : 'Read-only project access based on your role.'}
                </span>
              </div>
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
}
