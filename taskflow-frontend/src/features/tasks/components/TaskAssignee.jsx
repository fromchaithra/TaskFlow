import { User } from 'lucide-react';
import { Avatar } from '../../../components/common/Avatar';

export function TaskAssignee({ assignee, size = 'xs', compact = false, className = '' }) {
  if (!assignee) {
    return (
      <span
        className={`inline-flex items-center gap-1.5 text-xs text-ink-faint ${className}`}
        title="Unassigned"
      >
        <span className="h-6 w-6 rounded bg-subtle border border-line-strong inline-flex items-center justify-center shrink-0">
          <User className="h-3 w-3 text-ink-faint" aria-hidden="true" />
        </span>
        {!compact && <span>Unassigned</span>}
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center gap-2 min-w-0 ${className}`}
      title={assignee.email ? `${assignee.fullName} (${assignee.email})` : assignee.fullName}
    >
      <Avatar name={assignee.fullName} color={assignee.avatarColor} size={size} />
      {!compact && (
        <span className="text-xs text-ink truncate">{assignee.fullName}</span>
      )}
    </span>
  );
}
