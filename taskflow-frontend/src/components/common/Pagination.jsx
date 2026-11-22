import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';

const PAGE_SIZE_OPTIONS = [10, 20, 50];

/**
 * Pagination bar mapped to Spring Boot PagedResponse<T>:
 * { content, page (0-indexed), size, totalElements, totalPages, last }
 */
export function Pagination({
  page = 0,
  size = 20,
  totalElements = 0,
  totalPages = 0,
  onPageChange,
  onSizeChange,
  disabled = false,
}) {
  const safeTotalPages = Math.max(totalPages, 1);
  const startItem = totalElements === 0 ? 0 : page * size + 1;
  const endItem = Math.min((page + 1) * size, totalElements);

  const canPrev = page > 0 && !disabled;
  const canNext = page + 1 < totalPages && !disabled;

  // Compact window of up to 5 page numbers
  const pages = [];
  const windowStart = Math.max(0, Math.min(page - 2, safeTotalPages - 5));
  const windowEnd = Math.min(safeTotalPages - 1, windowStart + 4);
  for (let i = windowStart; i <= windowEnd; i += 1) {
    pages.push(i);
  }

  return (
    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 px-4 py-3 border-t border-line bg-subtle/50 rounded-b-xl">
      <div className="flex items-center gap-4 text-xs text-ink-muted">
        <span className="tabular-nums">
          Showing <span className="font-medium text-ink">{startItem}</span>–
          <span className="font-medium text-ink">{endItem}</span> of{' '}
          <span className="font-medium text-ink">{totalElements}</span>
        </span>

        {onSizeChange && (
          <label className="inline-flex items-center gap-1.5">
            <span className="text-ink-faint">Rows</span>
            <select
              value={size}
              disabled={disabled}
              onChange={(e) => onSizeChange(Number(e.target.value))}
              className="h-7 rounded-md bg-canvas border border-line-strong px-2 text-xs text-ink font-mono tabular-nums focus:outline-none focus:border-brand-500"
            >
              {PAGE_SIZE_OPTIONS.map((opt) => (
                <option key={opt} value={opt} className="bg-canvas">
                  {opt}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      <div className="flex items-center gap-1.5">
        <Button
          variant="secondary"
          size="sm"
          disabled={!canPrev}
          onClick={() => onPageChange(page - 1)}
          leftIcon={<ChevronLeft className="h-3.5 w-3.5" />}
        >
          Prev
        </Button>

        {totalPages > 1 && (
          <div className="hidden sm:flex items-center gap-1">
            {pages.map((pageIndex) => {
              const isCurrent = pageIndex === page;
              return (
                <button
                  key={pageIndex}
                  type="button"
                  disabled={disabled || isCurrent}
                  onClick={() => onPageChange(pageIndex)}
                  aria-current={isCurrent ? 'page' : undefined}
                  className={[
                    'h-8 min-w-[32px] px-2 rounded-md text-xs font-mono tabular-nums transition-colors border',
                    isCurrent
                      ? 'bg-brand-50 text-brand-700 border-brand-200 font-semibold'
                      : 'bg-transparent text-ink-faint border-transparent hover:bg-subtle hover:text-ink',
                  ].join(' ')}
                >
                  {pageIndex + 1}
                </button>
              );
            })}
          </div>
        )}

        <Button
          variant="secondary"
          size="sm"
          disabled={!canNext}
          onClick={() => onPageChange(page + 1)}
          rightIcon={<ChevronRight className="h-3.5 w-3.5" />}
        >
          Next
        </Button>
      </div>
    </div>
  );
}
