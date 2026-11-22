import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BarChart3, Calendar, CheckSquare, Info, Pencil, Target, User } from 'lucide-react';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { Modal } from '../../components/common/Modal';
import { sprintService } from '../../services/sprintService';
import { formatDateRange, formatDateTime } from '../../utils/formatters';
import { parseApiError } from '../../utils/errorHandler';
import { SprintLifecycleActions } from './SprintLifecycleActions';
import { SprintReport } from './SprintReport';
import { SprintStatusBadge } from './components/SprintStatusBadge';
import { SprintTasksManager } from './SprintTasksManager';
import { getSprintCapabilities, getSprintStateHint } from './sprintConstants';

const TABS = [
  { id: 'overview', label: 'Overview', icon: Info },
  { id: 'tasks', label: 'Tasks', icon: CheckSquare },
  { id: 'report', label: 'Report', icon: BarChart3 },
];

function MetaRow({ icon: Icon, label, value }) {
  return (
    <div className="flex items-start gap-2.5">
      <Icon className="h-3.5 w-3.5 text-ink-faint shrink-0 mt-0.5" aria-hidden="true" />
      <div className="min-w-0">
        <p className="text-[10px] font-semibold uppercase tracking-wider text-ink-faint">
          {label}
        </p>
        <p className="text-xs text-ink mt-0.5 break-words">{value}</p>
      </div>
    </div>
  );
}

/**
 * Full sprint workspace modal backed by SprintDetailResponse and SprintReportResponse.
 * All mutations flow through the real backend endpoints and refresh the parent list.
 */
export function SprintDetailModal({
  isOpen,
  onClose,
  sprintId,
  initialSprint = null,
  projectStatus,
  canManage = false,
  initialTab = 'overview',
  refreshToken = 0,
  onSprintChanged,
  onEdit,
}) {
  const [detail, setDetail] = useState(initialSprint);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState(initialTab);

  const [report, setReport] = useState(null);
  const [reportLoading, setReportLoading] = useState(false);
  const [reportError, setReportError] = useState(null);
  const [reportToken, setReportToken] = useState(0);

  const projectId = detail?.projectId ?? initialSprint?.projectId ?? null;

  // Keep the latest open-time defaults without resetting the modal on every parent refresh.
  const initialSprintRef = useRef(initialSprint);
  const initialTabRef = useRef(initialTab);
  initialSprintRef.current = initialSprint;
  initialTabRef.current = initialTab;

  const loadDetail = useCallback(
    async ({ silent = false } = {}) => {
      if (!projectId || !sprintId) return;
      if (silent) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      try {
        const data = await sprintService.getSprintById(projectId, sprintId);
        setDetail(data);
        return data;
      } catch (err) {
        setError(parseApiError(err).message);
        return null;
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [projectId, sprintId]
  );

  useEffect(() => {
    if (!isOpen) return;
    setActiveTab(initialTabRef.current);
    setReport(null);
    setReportError(null);
    setDetail(initialSprintRef.current || null);
  }, [isOpen, sprintId]);

  useEffect(() => {
    if (!isOpen || !sprintId) return;
    loadDetail({ silent: Boolean(detail) });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, sprintId, refreshToken]);

  useEffect(() => {
    if (!isOpen || activeTab !== 'report' || !projectId || !sprintId) return undefined;

    let cancelled = false;
    setReportLoading(true);
    setReportError(null);

    sprintService
      .getSprintReport(projectId, sprintId)
      .then((data) => {
        if (!cancelled) setReport(data);
      })
      .catch((err) => {
        if (!cancelled) setReportError(parseApiError(err).message);
      })
      .finally(() => {
        if (!cancelled) setReportLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isOpen, activeTab, projectId, sprintId, reportToken]);

  const effectiveSprint = detail || initialSprint;
  const capabilities = useMemo(
    () =>
      getSprintCapabilities({
        sprint: effectiveSprint,
        projectStatus,
        canManage,
      }),
    [effectiveSprint, projectStatus, canManage]
  );

  const stateHint = useMemo(
    () =>
      getSprintStateHint({
        sprint: effectiveSprint,
        projectStatus,
        canManage,
      }),
    [effectiveSprint, projectStatus, canManage]
  );

  const handleSprintUpdated = (updated) => {
    if (updated) {
      setDetail((prev) => ({ ...(prev || {}), ...updated }));
      onSprintChanged?.(updated);
    }
    setReportToken((prev) => prev + 1);
  };

  const taskCount = Number(effectiveSprint?.taskCount ?? effectiveSprint?.tasks?.length ?? 0);

  const footer = (
    <>
      <Button variant="ghost" onClick={onClose}>
        Close
      </Button>
      <Button
        variant="secondary"
        onClick={() => loadDetail({ silent: true })}
        loading={refreshing}
      >
        Refresh
      </Button>
    </>
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      size="xl"
      title={effectiveSprint?.name || 'Sprint details'}
      description={effectiveSprint?.projectKey ? `Project ${effectiveSprint.projectKey}` : undefined}
      footer={footer}
    >
      {loading && !effectiveSprint ? (
        <div className="space-y-3">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-24 w-full rounded-lg" />
          <Skeleton className="h-40 w-full rounded-lg" />
        </div>
      ) : error && !effectiveSprint ? (
        <ErrorState
          title="Unable to load sprint"
          description={error}
          onRetry={() => loadDetail()}
        />
      ) : !effectiveSprint ? (
        <p className="text-xs text-ink-muted">Sprint details are unavailable.</p>
      ) : (
        <div className="space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <SprintStatusBadge status={effectiveSprint.status} />
              <Badge tone="neutral" mono>
                Sprint #{effectiveSprint.id}
              </Badge>
              <span className="text-xs text-ink-muted">
                {formatDateRange(effectiveSprint.startDate, effectiveSprint.endDate)}
              </span>
            </div>
            <span className="text-xs text-ink-muted font-mono tabular-nums">
              {taskCount} {taskCount === 1 ? 'task' : 'tasks'}
            </span>
            {capabilities.canEdit && onEdit && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => onEdit(effectiveSprint)}
                leftIcon={<Pencil className="h-3.5 w-3.5" />}
              >
                Edit sprint
              </Button>
            )}
          </div>

          {error && (
            <p role="alert" className="text-[11px] text-amber-700">
              {error} Showing the last loaded sprint data.
            </p>
          )}

          <div className="border-b border-line overflow-x-auto">
            <nav className="flex items-center gap-1 min-w-max" aria-label="Sprint detail sections">
              {TABS.map((tab) => {
                const Icon = tab.icon;
                const isSelected = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    role="tab"
                    aria-selected={isSelected}
                    onClick={() => setActiveTab(tab.id)}
                    className={[
                      'inline-flex items-center gap-2 px-3.5 py-2.5 text-xs font-medium border-b-2 transition-colors whitespace-nowrap',
                      isSelected
                        ? 'border-brand-500 text-ink'
                        : 'border-transparent text-ink-muted hover:text-ink hover:border-line-strong',
                    ].join(' ')}
                  >
                    <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                    {tab.label}
                    {tab.id === 'tasks' && (
                      <span className="px-1.5 py-0.5 rounded bg-subtle text-[10px] font-mono tabular-nums text-ink-muted">
                        {taskCount}
                      </span>
                    )}
                  </button>
                );
              })}
            </nav>
          </div>

          {activeTab === 'overview' && (
            <div className="space-y-4">
              <div className="rounded-lg border border-line bg-subtle/60 px-3.5 py-3">
                <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-ink-faint">
                  <Target className="h-3.5 w-3.5" aria-hidden="true" />
                  Sprint goal
                </div>
                <p className="mt-1.5 text-xs text-ink leading-relaxed whitespace-pre-wrap">
                  {effectiveSprint.goal?.trim() || 'No sprint goal recorded.'}
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <MetaRow
                  icon={Calendar}
                  label="Sprint window"
                  value={formatDateRange(effectiveSprint.startDate, effectiveSprint.endDate)}
                />
                <MetaRow
                  icon={CheckSquare}
                  label="Assigned tasks"
                  value={`${taskCount} ${taskCount === 1 ? 'task' : 'tasks'}`}
                />
                <MetaRow
                  icon={User}
                  label="Created by"
                  value={
                    effectiveSprint.createdBy ? (
                      <span className="inline-flex items-center gap-2">
                        <Avatar
                          name={effectiveSprint.createdBy.fullName}
                          color={effectiveSprint.createdBy.avatarColor}
                          size="xs"
                        />
                        {effectiveSprint.createdBy.fullName}
                      </span>
                    ) : (
                      'Not recorded'
                    )
                  }
                />
                <MetaRow
                  icon={Calendar}
                  label="Started / closed"
                  value={`${formatDateTime(effectiveSprint.startedAt)} · ${formatDateTime(
                    effectiveSprint.completedAt
                  )}`}
                />
              </div>

              {(capabilities.canStart ||
                capabilities.canComplete ||
                capabilities.canCancel) && (
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-line bg-subtle/60 px-3.5 py-3">
                  <p className="text-xs text-ink-muted max-w-md leading-relaxed">{stateHint}</p>
                  <SprintLifecycleActions
                    sprint={effectiveSprint}
                    projectStatus={projectStatus}
                    canManage={canManage}
                    onChanged={handleSprintUpdated}
                  />
                </div>
              )}

              {!capabilities.canStart &&
                !capabilities.canComplete &&
                !capabilities.canCancel &&
                stateHint && (
                  <p className="text-[11px] text-ink-faint leading-relaxed">{stateHint}</p>
                )}
            </div>
          )}

          {activeTab === 'tasks' && (
            <SprintTasksManager
              sprint={effectiveSprint}
              canManageTasks={capabilities.canManageTasks}
              onSprintUpdated={handleSprintUpdated}
              onReload={async () => {
                const data = await loadDetail({ silent: true });
                if (data) onSprintChanged?.(data);
              }}
            />
          )}

          {activeTab === 'report' && (
            <SprintReport
              report={report}
              loading={reportLoading}
              error={reportError}
              onRetry={() => setReportToken((prev) => prev + 1)}
            />
          )}
        </div>
      )}
    </Modal>
  );
}
