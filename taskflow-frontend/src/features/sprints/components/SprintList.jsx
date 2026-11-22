import { BarChart3, Pencil } from 'lucide-react';
import { Button } from '../../../components/common/Button';
import { DataTable } from '../../../components/common/DataTable';
import { formatDateRange } from '../../../utils/formatters';
import { SprintStatusBadge } from './SprintStatusBadge';

/**
 * Compact list view over SprintSummaryResponse rows with the same actions as SprintCard.
 */
export function SprintList({ sprints, canEdit = false, onOpen, onEdit }) {
  const columns = [
    {
      key: 'name',
      label: 'Sprint',
      render: (sprint) => (
        <div className="min-w-0">
          <p className="text-sm font-medium text-ink truncate">{sprint.name}</p>
          <p className="text-[11px] text-ink-faint truncate max-w-[360px]">
            {sprint.goal?.trim() || 'No sprint goal recorded.'}
          </p>
        </div>
      ),
    },
    {
      key: 'status',
      label: 'Status',
      render: (sprint) => <SprintStatusBadge status={sprint.status} />,
    },
    {
      key: 'dates',
      label: 'Window',
      render: (sprint) => (
        <span className="text-xs text-ink-muted whitespace-nowrap">
          {formatDateRange(sprint.startDate, sprint.endDate)}
        </span>
      ),
    },
    {
      key: 'taskCount',
      label: 'Tasks',
      align: 'right',
      render: (sprint) => (
        <span className="font-mono tabular-nums text-ink">
          {Number(sprint.taskCount ?? 0)}
        </span>
      ),
    },
    {
      key: 'createdBy',
      label: 'Created by',
      render: (sprint) => (
        <span className="text-xs text-ink-muted truncate block max-w-[160px]">
          {sprint.createdBy?.fullName || 'Not recorded'}
        </span>
      ),
    },
    {
      key: 'actions',
      label: 'Actions',
      align: 'right',
      render: (sprint) => {
        const editable = typeof canEdit === 'function' ? canEdit(sprint) : canEdit;
        return (
          <div className="flex items-center justify-end gap-1.5">
            <Button
              variant="ghost"
              size="sm"
              onClick={(event) => {
                event.stopPropagation();
                onOpen?.(sprint, 'report');
              }}
              leftIcon={<BarChart3 className="h-3.5 w-3.5" />}
            >
              Report
            </Button>
            {editable && (
              <Button
                variant="ghost"
                size="sm"
                onClick={(event) => {
                  event.stopPropagation();
                  onEdit?.(sprint);
                }}
                leftIcon={<Pencil className="h-3.5 w-3.5" />}
              >
                Edit
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <DataTable
      columns={columns}
      data={sprints}
      rowKey="id"
      onRowClick={(sprint) => onOpen?.(sprint, 'overview')}
      emptyState={{
        title: 'No sprints match these filters',
        description: 'Adjust the status filter or clear the search to see more sprints.',
      }}
    />
  );
}
