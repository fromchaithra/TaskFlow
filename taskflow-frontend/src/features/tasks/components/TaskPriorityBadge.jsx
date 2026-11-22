import { AlertOctagon, ArrowDown, ArrowUp, Minus } from 'lucide-react';
import { Badge } from '../../../components/common/Badge';
import { PRIORITY_LEVEL, PRIORITY_META } from '../taskConstants';

const PRIORITY_ICONS = {
  [PRIORITY_LEVEL.CRITICAL]: AlertOctagon,
  [PRIORITY_LEVEL.HIGH]: ArrowUp,
  [PRIORITY_LEVEL.MEDIUM]: Minus,
  [PRIORITY_LEVEL.LOW]: ArrowDown,
};

export function TaskPriorityBadge({ priority, showIcon = true, className = '' }) {
  const meta = PRIORITY_META[priority] || {
    label: priority || 'Medium',
    tone: 'neutral',
  };
  const Icon = PRIORITY_ICONS[priority] || Minus;

  return (
    <Badge tone={meta.tone} className={className}>
      {showIcon && <Icon className="h-3 w-3 shrink-0" aria-hidden="true" />}
      <span>{meta.label}</span>
    </Badge>
  );
}
