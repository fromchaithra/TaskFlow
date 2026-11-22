import { Badge } from '../../../components/common/Badge';
import { PROJECT_STATUS_META } from '../projectConstants';

export function ProjectStatusBadge({ status, className = '' }) {
  const meta = PROJECT_STATUS_META[status] || {
    label: status || 'Unknown',
    tone: 'neutral',
  };

  return (
    <Badge tone={meta.tone} dot className={className}>
      {meta.label}
    </Badge>
  );
}
