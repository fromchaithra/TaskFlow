import { AlertTriangle, Search, X } from 'lucide-react';
import { Button } from '../../../components/common/Button';
import { Select } from '../../../components/common/FormControls';
import { Input } from '../../../components/common/Input';
import {
  ALL_PRIORITIES,
  KANBAN_COLUMN_ORDER,
  PRIORITY_META,
  TASK_STATUS_META,
} from '../taskConstants';

const STATUS_OPTIONS = [
  { value: '', label: 'All Statuses' },
  ...KANBAN_COLUMN_ORDER.map((status) => ({
    value: status,
    label: TASK_STATUS_META[status]?.label || status,
  })),
];

const PRIORITY_OPTIONS = [
  { value: '', label: 'All Priorities' },
  ...ALL_PRIORITIES.map((priority) => ({
    value: priority,
    label: PRIORITY_META[priority]?.label || priority,
  })),
];

/**
 * Reusable filter bar strictly aligned to backend query parameters on:
 * - GET /api/v1/tasks
 * - GET /api/v1/tasks/my-tasks
 * - GET /api/v1/projects/{projectId}/kanban
 */
export function TaskFilters({
  searchValue = '',
  onSearchChange,
  statusValue = '',
  onStatusChange = null,
  showStatus = true,
  priorityValue = '',
  onPriorityChange,
  assigneeValue = '',
  onAssigneeChange = null,
  assigneeOptions = [],
  sprintValue = '',
  onSprintChange = null,
  sprintOptions = [],
  projectValue = '',
  onProjectChange = null,
  projectOptions = [],
  overdueOnly = false,
  onOverdueOnlyChange = null,
  sortValue = '',
  onSortChange = null,
  sortOptions = [],
  onClear = null,
  hasActiveFilters = false,
}) {
  return (
    <div className="space-y-3">
      <div className="flex flex-col xl:flex-row xl:items-center gap-2.5">
        <div className="flex-1 min-w-[220px]">
          <Input
            value={searchValue}
            onChange={(e) => onSearchChange?.(e.target.value)}
            placeholder="Search by task key (e.g., TF-12) or title..."
            leftIcon={<Search className="h-4 w-4" />}
          />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {onProjectChange && projectOptions.length > 0 && (
            <div className="w-full sm:w-44">
              <Select
                aria-label="Filter by project"
                value={projectValue}
                onChange={(e) => onProjectChange(e.target.value)}
                options={projectOptions}
              />
            </div>
          )}

          {showStatus && onStatusChange && (
            <div className="w-full sm:w-40">
              <Select
                aria-label="Filter by status"
                value={statusValue}
                onChange={(e) => onStatusChange(e.target.value)}
                options={STATUS_OPTIONS}
              />
            </div>
          )}

          {onPriorityChange && (
            <div className="w-full sm:w-36">
              <Select
                aria-label="Filter by priority"
                value={priorityValue}
                onChange={(e) => onPriorityChange(e.target.value)}
                options={PRIORITY_OPTIONS}
              />
            </div>
          )}

          {onAssigneeChange && assigneeOptions.length > 0 && (
            <div className="w-full sm:w-44">
              <Select
                aria-label="Filter by assignee"
                value={assigneeValue}
                onChange={(e) => onAssigneeChange(e.target.value)}
                options={assigneeOptions}
              />
            </div>
          )}

          {onSprintChange && sprintOptions.length > 1 && (
            <div className="w-full sm:w-44">
              <Select
                aria-label="Filter by sprint"
                value={sprintValue}
                onChange={(e) => onSprintChange(e.target.value)}
                options={sprintOptions}
              />
            </div>
          )}

          {onSortChange && sortOptions.length > 0 && (
            <div className="w-full sm:w-44">
              <Select
                aria-label="Sort tasks"
                value={sortValue}
                onChange={(e) => onSortChange(e.target.value)}
                options={sortOptions}
              />
            </div>
          )}

          {onOverdueOnlyChange && (
            <button
              type="button"
              onClick={() => onOverdueOnlyChange(!overdueOnly)}
              aria-pressed={overdueOnly}
              className={[
                'h-10 px-3 rounded-lg border text-xs font-medium inline-flex items-center gap-1.5 transition-colors whitespace-nowrap shrink-0',
                overdueOnly
                  ? 'bg-amber-500/15 border-amber-200 text-amber-700'
                  : 'bg-canvas border-line text-ink-muted hover:border-line-strong hover:text-ink',
              ].join(' ')}
            >
              <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
              <span>Overdue</span>
            </button>
          )}

          {hasActiveFilters && onClear && (
            <Button
              variant="ghost"
              size="sm"
              onClick={onClear}
              leftIcon={<X className="h-3.5 w-3.5" />}
            >
              Clear
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
