import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { EmptyState, ErrorState, Skeleton } from './Loading';

/**
 * High-density single-elevation enterprise DataTable with sorting, skeletons,
 * empty state, and error recovery.
 */
export function DataTable({
  columns = [],
  data = [],
  rowKey = 'id',
  loading = false,
  skeletonRows = 6,
  error = null,
  onRetry = null,
  sortBy = null,
  sortDir = 'desc',
  onSort = null,
  onRowClick = null,
  emptyState = {},
  footer = null,
}) {
  const handleHeaderSort = (col) => {
    if (!col.sortable || !onSort) return;
    const key = col.sortKey || col.key;
    const nextDir = sortBy === key && sortDir === 'asc' ? 'desc' : 'asc';
    onSort(key, nextDir);
  };

  if (error && !loading) {
    return (
      <div className="p-4">
        <ErrorState
          title={error.title || 'Failed to load records'}
          description={error.message || 'An unexpected error occurred while fetching data.'}
          onRetry={onRetry}
        />
      </div>
    );
  }

  return (
    <div className="bg-surface border border-line rounded-xl shadow-card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-line bg-subtle/80">
              {columns.map((col) => {
                const sortKey = col.sortKey || col.key;
                const isSorted = col.sortable && sortBy === sortKey;
                const alignClass =
                  col.align === 'right'
                    ? 'text-right'
                    : col.align === 'center'
                      ? 'text-center'
                      : 'text-left';

                return (
                  <th
                    key={col.key}
                    scope="col"
                    className={[
                      'px-4 py-3 text-[11px] font-semibold uppercase tracking-wider text-ink-faint whitespace-nowrap',
                      alignClass,
                      col.headerClassName || '',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                  >
                    {col.sortable && onSort ? (
                      <button
                        type="button"
                        onClick={() => handleHeaderSort(col)}
                        className={[
                          'inline-flex items-center gap-1.5 hover:text-ink transition-colors',
                          col.align === 'right' ? 'flex-row-reverse' : '',
                          isSorted ? 'text-brand-600' : '',
                        ]
                          .filter(Boolean)
                          .join(' ')}
                      >
                        <span>{col.label}</span>
                        {isSorted ? (
                          sortDir === 'asc' ? (
                            <ArrowUp className="h-3 w-3 shrink-0" aria-hidden="true" />
                          ) : (
                            <ArrowDown className="h-3 w-3 shrink-0" aria-hidden="true" />
                          )
                        ) : (
                          <ArrowUpDown className="h-3 w-3 opacity-50 shrink-0" aria-hidden="true" />
                        )}
                      </button>
                    ) : (
                      col.label
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>

          <tbody className="divide-y divide-line">
            {loading ? (
              Array.from({ length: skeletonRows }).map((_, idx) => (
                <tr key={`skeleton-${idx}`} className="h-[54px]">
                  {columns.map((col) => (
                    <td key={`${col.key}-${idx}`} className="px-4 py-3">
                      <Skeleton className="h-4 w-full max-w-[180px]" />
                    </td>
                  ))}
                </tr>
              ))
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={columns.length} className="p-6">
                  <EmptyState
                    icon={emptyState.icon}
                    title={emptyState.title || 'No records found'}
                    description={
                      emptyState.description || 'Try adjusting your search or filter criteria.'
                    }
                    action={emptyState.action}
                  />
                </td>
              </tr>
            ) : (
              data.map((row) => {
                const key = typeof rowKey === 'function' ? rowKey(row) : row[rowKey];
                return (
                  <tr
                    key={key}
                    onClick={onRowClick ? () => onRowClick(row) : undefined}
                    className={[
                      'transition-colors duration-100 hover:bg-brand-50/50',
                      onRowClick ? 'cursor-pointer' : '',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                  >
                    {columns.map((col) => {
                      const alignClass =
                        col.align === 'right'
                          ? 'text-right'
                          : col.align === 'center'
                            ? 'text-center'
                            : 'text-left';

                      return (
                        <td
                          key={col.key}
                          className={[
                            'px-4 py-3 text-sm text-ink align-middle',
                            alignClass,
                            col.cellClassName || '',
                          ]
                            .filter(Boolean)
                            .join(' ')}
                        >
                          {col.render ? col.render(row) : row[col.key]}
                        </td>
                      );
                    })}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {footer}
    </div>
  );
}
