import { useEffect, useMemo, useState } from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { Checkbox, Select } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { Skeleton } from '../../components/common/Loading';
import { useAuth } from '../../hooks/useAuth';
import { useDebounce } from '../../hooks/useDebounce';
import { projectService } from '../../services/projectService';
import { sprintService } from '../../services/sprintService';
import { teamService } from '../../services/teamService';
import { userService } from '../../services/userService';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateRange } from '../../utils/formatters';
import { ROLES } from '../../utils/constants';

const MAX_SELECTIONS = { projectIds: 50, teamIds: 100, employeeIds: 100 };
const GRANULARITIES = ['WEEK', 'MONTH'];
const MAX_RANGE_MONTHS = 24;

function toIsoDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function daysAgoIso(days) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return toIsoDate(date);
}

export function createDefaultFilters() {
  return {
    from: daysAgoIso(30),
    to: daysAgoIso(0),
    granularity: 'WEEK',
    comparisonEnabled: false,
    compareFrom: '',
    compareTo: '',
    projectIds: [],
    teamIds: [],
    employeeIds: [],
    sprintId: null,
  };
}

/**
 * Mirrors ChronoUnit.MONTHS.between (complete months) used by the backend
 * AnalyticsRangeValidator. Invalid inputs return 0; other rules report them.
 */
function monthsBetween(from, to) {
  const start = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(from || ''));
  const end = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(to || ''));
  if (!start || !end) return 0;
  const startYear = Number(start[1]);
  const startMonth = Number(start[2]);
  const startDay = Number(start[3]);
  const endYear = Number(end[1]);
  const endMonth = Number(end[2]);
  const endDay = Number(end[3]);
  let months = (endYear - startYear) * 12 + (endMonth - startMonth);
  if (endDay < startDay) months -= 1;
  return months;
}

export function validateFilters(values = {}, reportType = null) {
  const errors = {};
  const {
    from, to, granularity, comparisonEnabled, compareFrom, compareTo,
    projectIds = [], teamIds = [], employeeIds = [], sprintId,
  } = values;

  if (!from) errors.from = 'Start date is required.';
  if (!to) errors.to = 'End date is required.';
  if (from && to) {
    if (from > to) {
      errors.from = 'Start date must be on or before end date.';
    } else if (monthsBetween(from, to) > MAX_RANGE_MONTHS) {
      errors.to = 'The reporting range cannot exceed 24 months.';
    }
  }

  if (!GRANULARITIES.includes(granularity)) {
    errors.granularity = 'Select WEEK or MONTH.';
  }

  if (comparisonEnabled) {
    const hasCompareFrom = Boolean(compareFrom);
    const hasCompareTo = Boolean(compareTo);
    if (!hasCompareFrom || !hasCompareTo) {
      errors.compareFrom = 'Provide both comparison dates.';
    } else {
      if (compareFrom > compareTo) {
        errors.compareFrom = 'Comparison start must be on or before comparison end.';
      }
      if (monthsBetween(compareFrom, compareTo) > MAX_RANGE_MONTHS) {
        errors.compareTo = 'The comparison range cannot exceed 24 months.';
      }
      if (compareTo >= from) {
        errors.compareTo = 'Comparison must end before the reporting period starts.';
      }
    }
  }

  for (const [key, label] of [
    ['projectIds', 'projects'],
    ['teamIds', 'teams'],
    ['employeeIds', 'employees'],
  ]) {
    const ids = Array.isArray(values[key]) ? values[key] : [];
    if (ids.length > MAX_SELECTIONS[key]) {
      errors[key] = `Select at most ${MAX_SELECTIONS[key]} ${label}.`;
    } else if (ids.some((id) => !Number.isInteger(Number(id)) || Number(id) <= 0)) {
      errors[key] = 'All selections must be positive numeric IDs.';
    }
  }

  if (reportType === 'SPRINT_BURNOWN') {
    if (!Array.isArray(projectIds) || projectIds.length !== 1) {
      errors.sprintId = 'Select exactly one project to choose a sprint.';
    } else if (!Number.isInteger(Number(sprintId)) || Number(sprintId) <= 0) {
      errors.sprintId = 'Select a valid sprint.';
    }
  }

  return errors;
}

function extractItems(response) {
  if (Array.isArray(response)) return response;
  if (Array.isArray(response?.content)) return response.content;
  return [];
}

function SelectionList({
  title,
  options,
  selectedIds,
  max,
  labelFor,
  onChange,
  loading,
  error,
  onRetry,
  emptyLabel,
  searchPlaceholder,
}) {
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounce(query, 200);
  const selected = useMemo(() => new Set(selectedIds.map(Number)), [selectedIds]);

  const filtered = options.filter((option) =>
    labelFor(option).toLowerCase().includes(debouncedQuery.trim().toLowerCase())
  );

  const toggle = (id) => {
    const numericId = Number(id);
    if (!Number.isInteger(numericId) || numericId <= 0) return;
    const next = new Set(selected);
    if (next.has(numericId)) {
      next.delete(numericId);
    } else if (next.size < max) {
      next.add(numericId);
    }
    onChange(Array.from(next));
  };

  return (
    <div className="rounded-lg border border-line bg-canvas/60 p-3.5">
      <div className="mb-2.5 flex items-center justify-between gap-2">
        <p className="text-xs font-medium text-ink">{title}</p>
        <Badge tone="neutral" mono>{selected.size}/{max}</Badge>
      </div>
      <Input
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={searchPlaceholder}
        leftIcon={<Search className="h-3.5 w-3.5" />}
        inputClassName="h-8 text-xs"
      />
      <div className="mt-2.5 max-h-44 overflow-y-auto rounded-lg border border-line bg-surface divide-y divide-line">
        {loading ? (
          <div className="space-y-2 p-3">
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-3/4" />
          </div>
        ) : error ? (
          <div className="flex flex-col items-start gap-2 p-3" role="alert">
            <p className="text-xs text-red-600">{error}</p>
            <Button variant="ghost" size="sm" onClick={onRetry} leftIcon={<RefreshCw className="h-3 w-3" />}>
              Retry
            </Button>
          </div>
        ) : filtered.length === 0 ? (
          <p className="px-3 py-4 text-xs text-ink-faint">{emptyLabel}</p>
        ) : (
          filtered.map((option) => {
            const id = Number(option.id);
            const checked = selected.has(id);
            return (
              <div key={id} className="px-3 py-2">
                <Checkbox
                  id={`${title.toLowerCase().replace(/[^a-z0-9]/g, '-')}-${id}`}
                  label={labelFor(option)}
                  checked={checked}
                  disabled={!checked && selected.size >= max}
                  onChange={() => toggle(id)}
                />
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}

export function ReportFilters({ reportType, value, onChange, errors = {} }) {
  const { hasRole } = useAuth();
  const canListEmployees = hasRole([ROLES.ADMIN, ROLES.PROJECT_MANAGER]);
  const [projects, setProjects] = useState([]);
  const [teams, setTeams] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [optionsLoading, setOptionsLoading] = useState(true);
  const [optionErrors, setOptionErrors] = useState({});
  const [optionsToken, setOptionsToken] = useState(0);

  const [sprints, setSprints] = useState([]);
  const [sprintsLoading, setSprintsLoading] = useState(false);
  const [sprintsError, setSprintsError] = useState(null);
  const [sprintsToken, setSprintsToken] = useState(0);

  const isBurndown = reportType === 'SPRINT_BURNOWN';
  const sprintProjectId = isBurndown && value.projectIds.length === 1 ? value.projectIds[0] : null;

  const update = (patch) => onChange({ ...value, ...patch });

  useEffect(() => {
    let active = true;
    setOptionsLoading(true);
    setOptionErrors({});

    Promise.allSettled([
      projectService.listProjects({ size: 100 }),
      teamService.listTeams({ size: 100 }),
      canListEmployees
        ? userService.listUsers({ size: 100 })
        : Promise.resolve({ content: [] }),
    ]).then((results) => {
      if (!active) return;
      const keys = ['projects', 'teams', 'employees'];
      const setters = [setProjects, setTeams, setEmployees];
      const nextErrors = {};
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          setters[index](extractItems(result.value));
        } else {
          nextErrors[keys[index]] = parseApiError(result.reason).message;
        }
      });
      setOptionErrors(nextErrors);
    }).finally(() => {
      if (active) setOptionsLoading(false);
    });

    return () => { active = false; };
  }, [optionsToken, canListEmployees]);

  useEffect(() => {
    if (!sprintProjectId) {
      setSprints([]);
      setSprintsError(null);
      setSprintsLoading(false);
      return undefined;
    }

    let active = true;
    setSprintsLoading(true);
    setSprintsError(null);
    sprintService.listProjectSprints(sprintProjectId)
      .then((data) => {
        if (active) setSprints(Array.isArray(data) ? data : extractItems(data));
      })
      .catch((err) => {
        if (active) {
          setSprints([]);
          setSprintsError(parseApiError(err).message);
        }
      })
      .finally(() => {
        if (active) setSprintsLoading(false);
      });

    return () => { active = false; };
  }, [sprintProjectId, sprintsToken]);

  const selectedSprint = sprints.find((sprint) => Number(sprint.id) === Number(value.sprintId));
  const errorCount = Object.keys(errors).length;

  return (
    <Card>
      <CardHeader
        title="Report filters"
        description="Filters are sent to the reporting service with your organization and project scope."
        action={errorCount > 0 ? <Badge tone="red">{errorCount} to review</Badge> : <Badge tone="emerald">Valid</Badge>}
      />
      <CardBody className="space-y-5">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input
            id="report-from"
            type="date"
            label="From"
            value={value.from}
            onChange={(event) => update({ from: event.target.value })}
            error={errors.from}
            helperText="Maximum 24-month range."
            required
          />
          <Input
            id="report-to"
            type="date"
            label="To"
            value={value.to}
            onChange={(event) => update({ to: event.target.value })}
            error={errors.to}
            required
          />
          <Select
            id="report-granularity"
            label="Granularity"
            value={value.granularity}
            onChange={(event) => update({ granularity: event.target.value })}
            options={GRANULARITIES.map((option) => ({ value: option, label: option }))}
            error={errors.granularity}
          />
        </div>

        <div className="rounded-lg border border-line bg-subtle/40 p-3.5">
          <Checkbox
            id="report-comparison-enabled"
            label="Compare with an earlier period"
            description="Adds a prior period to the generated report when supported."
            checked={value.comparisonEnabled}
            onChange={(event) => update({ comparisonEnabled: event.target.checked })}
          />
          {value.comparisonEnabled && (
            <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input
                id="report-compare-from"
                type="date"
                label="Comparison from"
                value={value.compareFrom}
                onChange={(event) => update({ compareFrom: event.target.value })}
                error={errors.compareFrom}
                helperText="Must end before the primary range."
              />
              <Input
                id="report-compare-to"
                type="date"
                label="Comparison to"
                value={value.compareTo}
                onChange={(event) => update({ compareTo: event.target.value })}
                error={errors.compareTo}
              />
            </div>
          )}
        </div>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <SelectionList
            title="Projects"
            options={projects}
            selectedIds={value.projectIds}
            max={MAX_SELECTIONS.projectIds}
            labelFor={(project) => `${project.projectKey || project.id} · ${project.name || 'Project'}`}
            onChange={(projectIds) => {
              const patch = { projectIds };
              if (isBurndown) patch.sprintId = null;
              update(patch);
            }}
            loading={optionsLoading}
            error={optionErrors.projects}
            onRetry={() => setOptionsToken((token) => token + 1)}
            emptyLabel="No projects available."
            searchPlaceholder="Search projects…"
          />
          <SelectionList
            title="Teams"
            options={teams}
            selectedIds={value.teamIds}
            max={MAX_SELECTIONS.teamIds}
            labelFor={(team) => team.name || `Team #${team.id}`}
            onChange={(teamIds) => update({ teamIds })}
            loading={optionsLoading}
            error={optionErrors.teams}
            onRetry={() => setOptionsToken((token) => token + 1)}
            emptyLabel="No teams available."
            searchPlaceholder="Search teams…"
          />
          <SelectionList
            title="Employees"
            options={employees}
            selectedIds={value.employeeIds}
            max={MAX_SELECTIONS.employeeIds}
            labelFor={(employee) => employee.fullName || `Employee #${employee.id}`}
            onChange={(employeeIds) => update({ employeeIds })}
            loading={optionsLoading}
            error={optionErrors.employees}
            onRetry={() => setOptionsToken((token) => token + 1)}
            emptyLabel="No employees available."
            searchPlaceholder="Search employees…"
          />
        </div>
        {errors.projectIds && <p className="-mt-3 text-xs text-red-600">{errors.projectIds}</p>}
        {errors.teamIds && <p className="-mt-3 text-xs text-red-600">{errors.teamIds}</p>}
        {errors.employeeIds && <p className="-mt-3 text-xs text-red-600">{errors.employeeIds}</p>}

        {isBurndown && (
          <div className="rounded-lg border border-line bg-canvas/60 p-3.5">
            <div className="mb-2.5 flex items-center justify-between gap-2">
              <p className="text-xs font-medium text-ink">Sprint</p>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSprintsToken((token) => token + 1)}
                disabled={!sprintProjectId || sprintsLoading}
                leftIcon={<RefreshCw className="h-3 w-3" />}
              >
                Refresh
              </Button>
            </div>
            {value.projectIds.length !== 1 ? (
              <p className="text-xs text-ink-muted">
                Select exactly one project to load its sprints. The sprint must belong to the
                selected project.
              </p>
            ) : (
              <Select
                id="report-sprint"
                label="Sprint"
                value={value.sprintId == null ? '' : String(value.sprintId)}
                onChange={(event) => update({ sprintId: event.target.value ? Number(event.target.value) : null })}
                options={sprints.map((sprint) => ({
                  value: sprint.id,
                  label: `${sprint.name || sprint.id}${sprint.status ? ` · ${sprint.status}` : ''}`,
                }))}
                placeholder={sprintsLoading ? 'Loading sprints…' : 'Select a sprint'}
                disabled={sprintsLoading || Boolean(sprintsError)}
                error={errors.sprintId || sprintsError}
              />
            )}
            {selectedSprint && (
              <p className="mt-2 text-[11px] text-ink-faint">
                Selected sprint belongs to {selectedSprint.projectKey || `project ${selectedSprint.projectId}`}.
              </p>
            )}
          </div>
        )}

        <div className="rounded-lg border border-line bg-subtle/50 p-3.5">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Applied filters</p>
          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            <Badge tone="neutral">{formatDateRange(value.from, value.to)}</Badge>
            <Badge tone="neutral" mono>{value.granularity}</Badge>
            {value.comparisonEnabled && value.compareFrom && value.compareTo && (
              <Badge tone="neutral">Compare {formatDateRange(value.compareFrom, value.compareTo)}</Badge>
            )}
            <Badge tone="neutral">{value.projectIds.length} projects</Badge>
            <Badge tone="neutral">{value.teamIds.length} teams</Badge>
            <Badge tone="neutral">{value.employeeIds.length} employees</Badge>
            {isBurndown && selectedSprint && (
              <Badge tone="brand">{selectedSprint.name || value.sprintId}</Badge>
            )}
          </div>
        </div>
      </CardBody>
    </Card>
  );
}
