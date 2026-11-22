import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Bug, RefreshCw, Search } from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody } from '../../components/common/Card';
import { DataTable } from '../../components/common/DataTable';
import { Select } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { Pagination } from '../../components/common/Pagination';
import { useAuth } from '../../hooks/useAuth';
import { useDebounce } from '../../hooks/useDebounce';
import { useToast } from '../../hooks/useToast';
import { bugService } from '../../services/bugService';
import { projectService } from '../../services/projectService';
import { ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateTime } from '../../utils/formatters';

const STATUS_OPTIONS = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'REOPENED', 'CLOSED'];
const SEVERITY_OPTIONS = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

const SEVERITY_TONE = { CRITICAL: 'red', HIGH: 'amber', MEDIUM: 'neutral', LOW: 'neutral' };
const PRIORITY_TONE = { CRITICAL: 'red', HIGH: 'amber', MEDIUM: 'neutral', LOW: 'neutral' };
const STATUS_TONE = {
  OPEN: 'red',
  IN_PROGRESS: 'amber',
  RESOLVED: 'emerald',
  VERIFIED: 'brand',
  REOPENED: 'amber',
  CLOSED: 'neutral',
};

const EMPTY_FORM = {
  title: '',
  description: '',
  severity: 'MEDIUM',
  priority: 'MEDIUM',
  assigneeId: '',
};

/**
 * Project-scoped Bugs tab mounted inside ProjectDetailPage (?tab=bugs).
 * Backed by GET /api/v1/bugs?projectId={project.id}, which is authorized
 * server-side by BugServiceImpl (project + organization isolation).
 */
export function ProjectBugsTab({ project }) {
  const projectId = project?.id;
  const navigate = useNavigate();
  const toast = useToast();
  const { hasRole } = useAuth();

  const canCreateBug = hasRole([ROLES.ADMIN, ROLES.PROJECT_MANAGER, ROLES.QA_TESTER]);

  const [searchInput, setSearchInput] = useState('');
  const debouncedSearch = useDebounce(searchInput, 300);
  const [statusFilter, setStatusFilter] = useState('');
  const [severityFilter, setSeverityFilter] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sortBy, setSortBy] = useState('createdAt');
  const [sortDir, setSortDir] = useState('desc');

  const [pageData, setPageData] = useState({
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    last: true,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [members, setMembers] = useState([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const fetchBugs = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    setError(null);
    try {
      const response = await bugService.listBugs({
        projectId,
        search: debouncedSearch || undefined,
        status: statusFilter || undefined,
        severity: severityFilter || undefined,
        page,
        size,
        sortBy,
        sortDir,
      });
      setPageData({
        content: Array.isArray(response?.content) ? response.content : [],
        page: response?.page ?? page,
        size: response?.size ?? size,
        totalElements: response?.totalElements ?? 0,
        totalPages: response?.totalPages ?? 0,
        last: Boolean(response?.last),
      });
    } catch (err) {
      const parsed = parseApiError(err);
      setError({
        title: parsed.status === 403 ? 'Access denied to these bugs' : 'Unable to load project bugs',
        message: parsed.status === 403
          ? 'You do not have permission to view bugs for this project.'
          : parsed.message,
      });
    } finally {
      setLoading(false);
    }
  }, [projectId, debouncedSearch, statusFilter, severityFilter, page, size, sortBy, sortDir]);

  useEffect(() => {
    fetchBugs();
  }, [fetchBugs]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, statusFilter, severityFilter]);

  const assigneeOptions = useMemo(() => {
    const options = [{ value: '', label: 'Unassigned' }];
    for (const member of members) {
      if (member?.id != null) {
        options.push({ value: String(member.id), label: member.fullName || `User #${member.id}` });
      }
    }
    return options;
  }, [members]);

  const hasActiveFilters = Boolean(
    searchInput.trim() || statusFilter || severityFilter
  );

  const handleClearFilters = () => {
    setSearchInput('');
    setStatusFilter('');
    setSeverityFilter('');
    setPage(0);
  };

  const openCreate = async () => {
    setCreateOpen(true);
    setForm(EMPTY_FORM);
    setFormError(null);
    try {
      const result = await projectService.listProjectMembers(projectId);
      const list = Array.isArray(result) ? result : (result?.content || []);
      const map = new Map();
      if (project?.manager?.id) {
        map.set(project.manager.id, project.manager);
      }
      for (const item of list) {
        const user = item?.user || item;
        if (user?.id != null) map.set(user.id, user);
      }
      setMembers(Array.from(map.values()));
    } catch (err) {
      toast.error(parseApiError(err).message, 'Failed to load project members');
      setMembers([]);
    }
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setFormError(null);
    if (!form.title.trim()) {
      setFormError('Title is required.');
      return;
    }
    setSubmitting(true);
    try {
      await bugService.createBug({
        projectId: Number(projectId),
        title: form.title.trim(),
        description: form.description.trim() || null,
        severity: form.severity,
        priority: form.priority,
        assigneeId: form.assigneeId ? Number(form.assigneeId) : null,
      });
      toast.success('Bug reported.', 'Created');
      setCreateOpen(false);
      setForm(EMPTY_FORM);
      fetchBugs();
    } catch (err) {
      setFormError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      key: 'bugKey',
      label: 'Key',
      sortable: true,
      cellClassName: 'font-mono text-xs text-brand-600 whitespace-nowrap',
      render: (row) => (
        <Link
          to={`/bugs/${row.id}`}
          state={{ fromProject: projectId }}
          className="hover:underline"
          onClick={(e) => e.stopPropagation()}
        >
          {row.bugKey}
        </Link>
      ),
    },
    {
      key: 'title',
      label: 'Title',
      render: (row) => (
        <div className="min-w-[200px] max-w-md">
          <p className="text-sm font-medium text-ink truncate">{row.title}</p>
          {row.description?.trim() && (
            <p className="mt-0.5 text-[11px] text-ink-faint truncate">{row.description}</p>
          )}
        </div>
      ),
    },
    {
      key: 'severity',
      label: 'Severity',
      sortable: true,
      render: (row) => (
        <Badge tone={SEVERITY_TONE[row.severity] || 'neutral'}>{row.severity}</Badge>
      ),
    },
    {
      key: 'priority',
      label: 'Priority',
      sortable: true,
      render: (row) => (
        <Badge tone={PRIORITY_TONE[row.priority] || 'neutral'}>{row.priority}</Badge>
      ),
    },
    {
      key: 'status',
      label: 'Status',
      sortable: true,
      render: (row) => (
        <Badge tone={STATUS_TONE[row.status] || 'neutral'} dot>{row.status}</Badge>
      ),
    },
    {
      key: 'assignee',
      label: 'Assignee',
      render: (row) => <span className="text-xs text-ink">{row.assignee?.fullName || '—'}</span>,
    },
    {
      key: 'reporter',
      label: 'Reporter',
      render: (row) => <span className="text-xs text-ink">{row.reporter?.fullName || '—'}</span>,
    },
    {
      key: 'createdAt',
      label: 'Created',
      sortable: true,
      cellClassName: 'text-xs text-ink-muted whitespace-nowrap',
      render: (row) => formatDateTime(row.createdAt),
    },
  ];

  const paginationFooter = pageData.totalElements > 0 ? (
    <Pagination
      page={pageData.page}
      size={pageData.size}
      totalElements={pageData.totalElements}
      totalPages={pageData.totalPages}
      disabled={loading}
      onPageChange={(nextPage) => setPage(nextPage)}
      onSizeChange={(nextSize) => {
        setSize(nextSize);
        setPage(0);
      }}
    />
  ) : null;

  return (
    <div className="space-y-4">
      <Card>
        <CardBody>
          <div className="flex flex-col sm:flex-row flex-wrap gap-3">
            <Input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search bugs in this project…"
              leftIcon={<Search className="h-4 w-4" />}
              className="flex-1"
            />
            <select
              aria-label="Filter by status"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="h-9 rounded-lg bg-canvas border border-line-strong px-3 text-sm text-ink"
            >
              <option value="">All statuses</option>
              {STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>{status}</option>
              ))}
            </select>
            <select
              aria-label="Filter by severity"
              value={severityFilter}
              onChange={(e) => setSeverityFilter(e.target.value)}
              className="h-9 rounded-lg bg-canvas border border-line-strong px-3 text-sm text-ink"
            >
              <option value="">All severities</option>
              {SEVERITY_OPTIONS.map((severity) => (
                <option key={severity} value={severity}>{severity}</option>
              ))}
            </select>
            <Button
              variant="secondary"
              size="sm"
              onClick={fetchBugs}
              leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
            >
              Refresh
            </Button>
          </div>
          {hasActiveFilters && (
            <div className="mt-3">
              <Button variant="ghost" size="sm" onClick={handleClearFilters}>
                Clear filters
              </Button>
            </div>
          )}
        </CardBody>
      </Card>

      <DataTable
        columns={columns}
        data={pageData.content}
        rowKey="id"
        loading={loading}
        skeletonRows={6}
        error={error}
        onRetry={fetchBugs}
        sortBy={sortBy}
        sortDir={sortDir}
        onSort={(nextField, nextDir) => {
          setSortBy(nextField);
          setSortDir(nextDir);
          setPage(0);
        }}
        onRowClick={(row) => navigate(`/bugs/${row.id}`, { state: { fromProject: projectId } })}
        emptyState={{
          icon: Bug,
          title: hasActiveFilters ? 'No bugs match these filters' : 'No bugs in this project yet',
          description: hasActiveFilters
            ? 'Adjust or clear your active search and filter criteria.'
            : canCreateBug
              ? 'Report the first bug to start tracking defects for this project.'
              : 'Bugs reported by the project team will appear here.',
          action: hasActiveFilters ? (
            <Button variant="secondary" size="sm" onClick={handleClearFilters}>
              Clear Filters
            </Button>
          ) : canCreateBug ? (
            <Button variant="primary" size="sm" onClick={openCreate}>
              Report Bug
            </Button>
          ) : null,
        }}
        footer={paginationFooter}
      />

      <Modal
        isOpen={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Report Bug"
        description={`This bug will be reported in ${project?.name || 'this project'}.`}
        size="lg"
      >
        <form onSubmit={handleCreate} className="space-y-4">
          {formError && (
            <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700">
              {formError}
            </div>
          )}
          <Input
            label="Title"
            value={form.title}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
            required
          />
          <div>
            <label htmlFor="project-bug-description" className="block text-xs font-medium text-ink-muted mb-1.5">
              Description
            </label>
            <textarea
              id="project-bug-description"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={4}
              className="w-full rounded-lg bg-canvas border border-line-strong px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:border-brand-500"
            />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Select
              label="Severity"
              value={form.severity}
              onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value }))}
              options={SEVERITY_OPTIONS.map((s) => ({ value: s, label: s }))}
            />
            <Select
              label="Priority"
              value={form.priority}
              onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
              options={SEVERITY_OPTIONS.map((s) => ({ value: s, label: s }))}
            />
            <Select
              label="Assignee"
              value={form.assigneeId}
              onChange={(e) => setForm((f) => ({ ...f, assigneeId: e.target.value }))}
              options={assigneeOptions}
              placeholder="Unassigned"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" size="sm" onClick={() => setCreateOpen(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="sm" loading={submitting}>
              Create Bug
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
