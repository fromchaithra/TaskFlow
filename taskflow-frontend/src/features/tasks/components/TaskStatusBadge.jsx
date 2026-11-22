import { Badge } from '../../../components/common/Badge';
import { TASK_STATUS_META } from '../taskConstants';

export function TaskStatusBadge({ status, className = '' }) {
  const meta = TASK_STATUS_META[status] || {
    label: status || 'Unknown',
    tone: 'neutral',
  };

  return (
    <Badge tone={meta.tone} dot className={className}>
      {meta.label}
    </Badge>
  );
}
