import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft,
  CalendarDays,
  Clock,
  Crown,
  Pencil,
  PowerOff,
  RefreshCw,
  Search,
  UserMinus,
  UserPlus,
  Users,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { teamService } from '../../services/teamService';
import { ROLE_META, ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateTime } from '../../utils/formatters';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { ConfirmDialog } from '../../components/common/ConfirmDialog';
import { DataTable } from '../../components/common/DataTable';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { PageHeader } from '../../components/common/PageHeader';
import { AddTeamMemberModal } from './AddTeamMemberModal';
import { ChangeTeamLeadModal } from './ChangeTeamLeadModal';
import { TeamFormModal } from './TeamFormModal';

export function TeamDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const { hasRole } = useAuth();
  const toast = useToast();

  const isAdmin = hasRole(ROLES.ADMIN);
  const backPath = `/teams${location.state?.fromSearch || ''}`;

  const [team, setTeam] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [memberFilter, setMemberFilter] = useState('');

  const [editOpen, setEditOpen] = useState(false);
  const [addMemberOpen, setAddMemberOpen] = useState(false);
  const [changeLeadOpen, setChangeLeadOpen] = useState(false);
  const [deactivateOpen, setDeactivateOpen] = useState(false);
  const [deactivating, setDeactivating] = useState(false);

  const [removeTarget, setRemoveTarget] = useState(null);
  const [removing, setRemoving] = useState(false);

  const fetchTeam = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await teamService.getTeamById(id);
      setTeam(data);
    } catch (err) {
      setError(parseApiError(err));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchTeam();
  }, [fetchTeam]);

  const members = useMemo(() => team?.members || [], [team]);

  const filteredMembers = useMemo(() => {
    const term = memberFilter.trim().toLowerCase();
    if (!term) return members;
    return members.filter(
      (m) =>
        m.fullName?.toLowerCase().includes(term) ||
        m.email?.toLowerCase().includes(term) ||
        m.employeeCode?.toLowerCase().includes(term) ||
        m.jobTitle?.toLowerCase().includes(term)
    );
  }, [members, memberFilter]);

  const handleConfirmDeactivate = async () => {
    if (!team || deactivating) return;
    setDeactivating(true);
    try {
      await teamService.deactivateTeam(team.id);
      toast.success(`Team "${team.name}" has been deactivated.`, 'Team deactivated');
      setDeactivateOpen(false);
      fetchTeam();
    } catch (err) {
      toast.error(parseApiError(err).message, 'Deactivation failed');
    } finally {
      setDeactivating(false);
    }
  };

  const handleConfirmRemoveMember = async () => {
    if (!team || !removeTarget || removing) return;
    setRemoving(true);
    try {
      await teamService.removeMember(team.id, removeTarget.id);
      toast.success(
        `${removeTarget.fullName} was removed from ${team.name}.`,
        'Member removed'
      );
      setRemoveTarget(null);
      fetchTeam();
    } catch (err) {
      toast.error(parseApiError(err).message, 'Could not remove member');
    } finally {
      setRemoving(false);
    }
  };

  const memberColumns = useMemo(
    () => [
      {
        key: 'fullName',
        label: 'Member',
        render: (row) => (
          <div className="flex items-center gap-3 min-w-[200px]">
            <Avatar name={row.fullName} color={row.avatarColor} size="sm" />
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-ink truncate">
                  {row.fullName}
                </span>
                {team?.lead?.id === row.id && <Badge tone="cyan">Lead</Badge>}
              </div>
              <p className="text-xs text-ink-muted truncate">
                {row.jobTitle || 'No job title'}
              </p>
            </div>
          </div>
        ),
      },
      {
        key: 'employeeCode',
        label: 'Code',
        render: (row) => (
          <span className="text-xs font-mono text-ink-muted tabular-nums">
            {row.employeeCode}
          </span>
        ),
      },
      {
        key: 'email',
        label: 'Email',
        render: (row) => (
          <span className="text-xs text-ink-muted truncate block max-w-[240px]">
            {row.email}
          </span>
        ),
      },
      {
        key: 'role',
        label: 'Role',
        render: (row) => {
          const meta = ROLE_META[row.role] || { label: row.role, tone: 'neutral' };
          return (
            <Badge tone={meta.tone} dot>
              {meta.label}
            </Badge>
          );
        },
      },
      {
        key: 'active',
        label: 'Account',
        render: (row) => (
          <Badge tone={row.active ? 'emerald' : 'red'}>
            {row.active ? 'Active' : 'Deactivated'}
          </Badge>
        ),
      },
      ...(isAdmin
        ? [
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (row) => (
                <button
                  type="button"
                  onClick={() => setRemoveTarget(row)}
                  title={`Remove ${row.fullName} from team`}
                  aria-label={`Remove ${row.fullName} from team`}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs text-ink-muted hover:text-red-600 hover:bg-red-50 transition-colors"
                >
                  <UserMinus className="h-3.5 w-3.5" aria-hidden="true" />
                  <span>Remove</span>
                </button>
              ),
            },
          ]
        : []),
    ],
    [isAdmin, team?.lead?.id]
  );

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-14 w-72" />
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <Skeleton className="h-44 lg:col-span-7 rounded-xl" />
          <Skeleton className="h-44 lg:col-span-5 rounded-xl" />
        </div>
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  if (error || !team) {
    return (
      <div className="space-y-6">
        <Link to={backPath}>
          <Button variant="secondary" size="sm" leftIcon={<ArrowLeft className="h-3.5 w-3.5" />}>
            Back to Teams
          </Button>
        </Link>
        <ErrorState
          title={error?.status === 404 ? 'Team not found' : 'Unable to load team'}
          description={error?.message || 'The requested team record could not be loaded.'}
          onRetry={fetchTeam}
        />
      </div>
    );
  }

  const leadRoleMeta = team.lead?.role ? ROLE_META[team.lead.role] : null;

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.16, ease: 'easeOut' }}
      className="space-y-6"
    >
      <section className="rounded-xl border border-line bg-surface p-5 shadow-card">
        <Link
          to={backPath}
          className="inline-flex items-center gap-1.5 text-xs text-ink-muted hover:text-ink transition-colors mb-3"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          <span>Back to Teams</span>
        </Link>

        <PageHeader
          title={team.name}
          description={team.description || 'No description has been set for this team.'}
          meta={
            <Badge tone={team.active ? 'emerald' : 'red'} dot>
              {team.active ? 'Active' : 'Deactivated'}
            </Badge>
          }
          actions={
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={fetchTeam}
                leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
              >
                Refresh
              </Button>
              {isAdmin && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setEditOpen(true)}
                  leftIcon={<Pencil className="h-3.5 w-3.5" />}
                >
                  Edit Team
                </Button>
              )}
              {isAdmin && team.active && (
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => setDeactivateOpen(true)}
                  leftIcon={<PowerOff className="h-3.5 w-3.5" />}
                >
                  Deactivate
                </Button>
              )}
            </>
          }
        />
      </section>

      {/* Overview & Team Lead grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* Team Lead Card */}
        <div className="lg:col-span-7 flex">
          <Card className="w-full flex flex-col">
            <CardHeader
              title="Team Lead"
              description="Primary point of accountability for this team."
              action={
                isAdmin && (
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={!team.active}
                    title={
                      !team.active
                        ? 'Reactivate this team via Edit Team before changing its lead'
                        : undefined
                    }
                    onClick={() => setChangeLeadOpen(true)}
                    leftIcon={<Crown className="h-3.5 w-3.5" />}
                  >
                    {team.lead ? 'Change Lead' : 'Assign Lead'}
                  </Button>
                )
              }
            />
            <CardBody className="flex-1 flex items-center">
              {team.lead ? (
                <div className="flex items-center gap-4 w-full">
                  <Avatar name={team.lead.fullName} color={team.lead.avatarColor} size="lg" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="text-sm font-semibold text-ink truncate">
                        {team.lead.fullName}
                      </p>
                      {leadRoleMeta && (
                        <Badge tone={leadRoleMeta.tone} dot>
                          {leadRoleMeta.label}
                        </Badge>
                      )}
                    </div>
                    <p className="text-xs text-ink-muted mt-0.5 truncate">
                      {team.lead.jobTitle || 'No job title'} · {team.lead.email}
                    </p>
                    <p className="text-[11px] font-mono text-ink-faint mt-1 tabular-nums">
                      {team.lead.employeeCode}
                    </p>
                  </div>
                </div>
              ) : (
                <div className="w-full py-2 flex items-center justify-between gap-4">
                  <div>
                    <p className="text-sm font-medium text-ink-muted">No team lead assigned</p>
                    <p className="text-xs text-ink-faint mt-0.5">
                      {isAdmin
                        ? 'Assign an active organization employee to lead this team.'
                        : 'An administrator has not assigned a lead to this team yet.'}
                    </p>
                  </div>
                </div>
              )}
            </CardBody>
          </Card>
        </div>

        {/* Team Metadata Card */}
        <div className="lg:col-span-5 flex">
          <Card className="w-full">
            <CardHeader
              title="Team Summary"
              description="Current roster size and lifecycle timestamps."
            />
            <CardBody>
              <dl className="divide-y divide-line -my-2">
                <div className="py-2.5 flex items-center justify-between gap-3">
                  <dt className="flex items-center gap-2 text-xs text-ink-muted">
                    <Users className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                    <span>Roster Size</span>
                  </dt>
                  <dd className="text-xs font-mono font-medium text-ink tabular-nums">
                    {members.length} {members.length === 1 ? 'member' : 'members'}
                  </dd>
                </div>

                <div className="py-2.5 flex items-center justify-between gap-3">
                  <dt className="flex items-center gap-2 text-xs text-ink-muted">
                    <CalendarDays className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                    <span>Created</span>
                  </dt>
                  <dd className="text-xs font-mono text-ink-muted tabular-nums">
                    {formatDateTime(team.createdAt)}
                  </dd>
                </div>

                <div className="py-2.5 flex items-center justify-between gap-3">
                  <dt className="flex items-center gap-2 text-xs text-ink-muted">
                    <Clock className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                    <span>Last Updated</span>
                  </dt>
                  <dd className="text-xs font-mono text-ink-muted tabular-nums">
                    {formatDateTime(team.updatedAt)}
                  </dd>
                </div>
              </dl>
            </CardBody>
          </Card>
        </div>
      </div>

      {/* Team Members Roster */}
      <section aria-labelledby="team-members-heading" className="space-y-3.5">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h2 id="team-members-heading" className="text-sm font-semibold text-ink">
              Team Members
            </h2>
            <p className="text-xs text-ink-muted mt-0.5">
              {!team.active && isAdmin
                ? 'This team is deactivated. Reactivate it via Edit Team to add new members.'
                : 'Employees currently assigned to this team.'}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            {members.length > 0 && (
              <div className="relative">
                <Search
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-ink-faint"
                  aria-hidden="true"
                />
                <input
                  type="search"
                  value={memberFilter}
                  onChange={(e) => setMemberFilter(e.target.value)}
                  placeholder="Filter roster..."
                  aria-label="Filter team members"
                  className="h-8 w-52 rounded-lg bg-surface border border-line pl-8 pr-3 text-xs text-ink placeholder:text-ink-faint focus:outline-none focus:border-brand-500"
                />
              </div>
            )}

            {isAdmin && (
              <Button
                variant="primary"
                size="sm"
                disabled={!team.active}
                onClick={() => setAddMemberOpen(true)}
                leftIcon={<UserPlus className="h-3.5 w-3.5" />}
              >
                Add Member
              </Button>
            )}
          </div>
        </div>

        <DataTable
          columns={memberColumns}
          data={filteredMembers}
          loading={false}
          emptyState={{
            icon: Users,
            title: memberFilter.trim() ? 'No matching members' : 'No team members assigned',
            description: memberFilter.trim()
              ? 'Try clearing your roster search filter.'
              : isAdmin
                ? 'Add active organization employees to build out this team.'
                : 'No employees have been added to this team yet.',
            action:
              !memberFilter.trim() && isAdmin && team.active ? (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => setAddMemberOpen(true)}
                  leftIcon={<UserPlus className="h-3.5 w-3.5" />}
                >
                  Add First Member
                </Button>
              ) : null,
          }}
        />
      </section>

      {/* Modals & Confirmations */}
      {isAdmin && (
        <TeamFormModal
          isOpen={editOpen}
          onClose={() => setEditOpen(false)}
          mode="edit"
          initialTeam={team}
          onSuccess={(updated) => {
            setTeam(updated);
            toast.success(`Team "${updated.name}" updated.`, 'Team saved');
          }}
        />
      )}

      {isAdmin && (
        <AddTeamMemberModal
          isOpen={addMemberOpen}
          onClose={() => setAddMemberOpen(false)}
          teamId={team.id}
          teamName={team.name}
          existingMemberIds={members.map((m) => m.id)}
          onSuccess={(updated) => {
            setTeam(updated);
            toast.success('Employee added to the team.', 'Member added');
          }}
        />
      )}

      {isAdmin && (
        <ChangeTeamLeadModal
          isOpen={changeLeadOpen}
          onClose={() => setChangeLeadOpen(false)}
          teamId={team.id}
          teamName={team.name}
          currentLead={team.lead}
          onSuccess={(updated) => {
            setTeam(updated);
            toast.success(
              `${updated.lead?.fullName || 'New lead'} is now leading ${updated.name}.`,
              'Team lead updated'
            );
          }}
        />
      )}

      {isAdmin && (
        <ConfirmDialog
          isOpen={deactivateOpen}
          onClose={() => setDeactivateOpen(false)}
          onConfirm={handleConfirmDeactivate}
          loading={deactivating}
          variant="danger"
          title={`Deactivate "${team.name}"?`}
          message="Deactivated teams cannot accept new members or lead reassignments until reactivated via Edit Team."
          confirmLabel="Deactivate Team"
        />
      )}

      {isAdmin && (
        <ConfirmDialog
          isOpen={Boolean(removeTarget)}
          onClose={() => setRemoveTarget(null)}
          onConfirm={handleConfirmRemoveMember}
          loading={removing}
          variant="danger"
          title={`Remove ${removeTarget?.fullName} from ${team.name}?`}
          message="This removes the employee from the team roster without affecting their organization account."
          confirmLabel="Remove Member"
        />
      )}
    </motion.div>
  );
}
