import { useCallback, useEffect, useMemo, useState } from 'react';
import { Archive, RefreshCw, Search, UserPlus } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { ConfirmDialog } from '../../components/common/ConfirmDialog';
import { Input } from '../../components/common/Input';
import { useToast } from '../../hooks/useToast';
import { projectService } from '../../services/projectService';
import { ROLE_META } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { PROJECT_STATUS } from './projectConstants';
import { ProjectMemberList } from './components/ProjectMemberList';
import {
  AddProjectMemberModal,
  EditProjectMemberRoleModal,
} from './components/ProjectMemberModal';

/**
 * Project Members tab wired strictly to:
 * - GET    /api/v1/projects/{id}/members
 * - POST   /api/v1/projects/{id}/members
 * - PATCH  /api/v1/projects/{id}/members/{userId}/role
 * - DELETE /api/v1/projects/{id}/members/{userId}
 */
export function ProjectMembersTab({ project, canManage, onProjectMutated }) {
  const { addToast } = useToast();
  const isArchived = project.status === PROJECT_STATUS.ARCHIVED;

  const [members, setMembers] = useState(() =>
    Array.isArray(project.members) ? project.members : []
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');

  const [addOpen, setAddOpen] = useState(false);
  const [editingMember, setEditingMember] = useState(null);
  const [removingMember, setRemovingMember] = useState(null);
  const [removing, setRemoving] = useState(false);

  // Sync whenever parent reloads project detail
  useEffect(() => {
    if (Array.isArray(project.members)) {
      setMembers(project.members);
    }
  }, [project.members]);

  const fetchMembers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await projectService.listProjectMembers(project.id);
      setMembers(Array.isArray(list) ? list : []);
    } catch (err) {
      const parsed = parseApiError(err);
      setError({ title: 'Unable to load project members', message: parsed.message });
    } finally {
      setLoading(false);
    }
  }, [project.id]);

  const existingMemberUserIds = useMemo(
    () => members.map((m) => m.user?.id).filter((id) => id != null),
    [members]
  );

  const filteredMembers = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return members;

    return members.filter((member) => {
      const u = member.user || {};
      const roleLabel = ROLE_META[member.projectRole]?.label || member.projectRole || '';
      return (
        u.fullName?.toLowerCase().includes(query) ||
        u.email?.toLowerCase().includes(query) ||
        u.employeeCode?.toLowerCase().includes(query) ||
        u.jobTitle?.toLowerCase().includes(query) ||
        roleLabel.toLowerCase().includes(query)
      );
    });
  }, [members, searchQuery]);

  const handleMemberAdded = (added) => {
    addToast({
      type: 'success',
      title: 'Member added',
      message: `${added?.user?.fullName || 'Employee'} now has access to ${project.projectKey}.`,
    });
    fetchMembers();
    onProjectMutated?.();
  };

  const handleRoleUpdated = (updated) => {
    addToast({
      type: 'success',
      title: 'Project role updated',
      message: `${updated?.user?.fullName || 'Member'} is now ${ROLE_META[updated?.projectRole]?.label || updated?.projectRole}.`,
    });
    fetchMembers();
    onProjectMutated?.();
  };

  const handleConfirmRemove = async () => {
    if (!removingMember?.user?.id || removing) return;
    setRemoving(true);
    try {
      await projectService.removeProjectMember(project.id, removingMember.user.id);
      addToast({
        type: 'success',
        title: 'Member removed',
        message: `${removingMember.user.fullName} was removed from ${project.projectKey}.`,
      });
      setRemovingMember(null);
      fetchMembers();
      onProjectMutated?.();
    } catch (err) {
      const parsed = parseApiError(err);
      addToast({
        type: 'error',
        title: 'Unable to remove member',
        message: parsed.message,
      });
      setRemovingMember(null);
    } finally {
      setRemoving(false);
    }
  };

  return (
    <div className="space-y-4">
      {isArchived && canManage && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 flex items-center gap-2.5 text-xs text-amber-700">
          <Archive className="h-4 w-4 text-amber-600 shrink-0" aria-hidden="true" />
          <span>
            This project is archived. Membership changes are locked until an Administrator
            reactivates the project.
          </span>
        </div>
      )}

      {/* Toolbar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div className="w-full sm:max-w-sm">
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search members by name, email, code, or role..."
            leftIcon={<Search className="h-4 w-4" />}
          />
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <Button
            variant="secondary"
            size="sm"
            onClick={fetchMembers}
            loading={loading}
            leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
          >
            Refresh
          </Button>

          {canManage && !isArchived && (
            <Button
              size="sm"
              onClick={() => setAddOpen(true)}
              leftIcon={<UserPlus className="h-4 w-4" />}
            >
              Add Member
            </Button>
          )}
        </div>
      </div>

      {/* Member Table */}
      <ProjectMemberList
        members={filteredMembers}
        managerId={project.manager?.id ?? null}
        canManage={canManage}
        isArchived={isArchived}
        loading={loading}
        error={error}
        onRetry={fetchMembers}
        onEditRole={(member) => setEditingMember(member)}
        onRemoveMember={(member) => setRemovingMember(member)}
        emptyAction={
          searchQuery.trim() ? (
            <Button variant="secondary" size="sm" onClick={() => setSearchQuery('')}>
              Clear search
            </Button>
          ) : canManage && !isArchived ? (
            <Button
              size="sm"
              onClick={() => setAddOpen(true)}
              leftIcon={<UserPlus className="h-4 w-4" />}
            >
              Add First Member
            </Button>
          ) : null
        }
      />

      {/* Modals */}
      <AddProjectMemberModal
        isOpen={addOpen}
        onClose={() => setAddOpen(false)}
        projectId={project.id}
        existingMemberUserIds={existingMemberUserIds}
        onMemberAdded={handleMemberAdded}
      />

      <EditProjectMemberRoleModal
        isOpen={Boolean(editingMember)}
        onClose={() => setEditingMember(null)}
        projectId={project.id}
        member={editingMember}
        isProjectManager={
          Boolean(editingMember?.user?.id) &&
          project.manager?.id != null &&
          editingMember.user.id === project.manager.id
        }
        onRoleUpdated={handleRoleUpdated}
      />

      <ConfirmDialog
        isOpen={Boolean(removingMember)}
        onClose={() => setRemovingMember(null)}
        onConfirm={handleConfirmRemove}
        title="Remove Project Member"
        message={
          removingMember
            ? `Remove ${removingMember.user?.fullName} from ${project.projectKey}? Members with active in-progress or in-review tasks or assigned/in-progress bugs cannot be removed until work is reassigned.`
            : ''
        }
        confirmLabel="Remove Member"
        variant="danger"
        loading={removing}
      />
    </div>
  );
}
