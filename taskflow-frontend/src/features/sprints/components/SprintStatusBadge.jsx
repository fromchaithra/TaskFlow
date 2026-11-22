import { Badge } from '../../../components/common/Badge';
import { SPRINT_STATUS_META } from '../sprintConstants';

export function SprintStatusBadge({ status, className = '' }) {
  const meta = SPRINT_STATUS_META[status] || {
    label: status || 'Unknown',
    tone: 'neutral',
  };

  return (
    <Badge tone={meta.tone} dot className={className}>
      {meta.label}
    </Badge>
  );
}
