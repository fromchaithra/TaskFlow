import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Bug as BugIcon, Plus, RefreshCw, Search } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateTime } from '../../utils/formatters';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody } from '../../components/common/Card';
import { DataTable } from '../../components/common/DataTable';
import { ErrorState } from '../../components/common/Loading';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { Select } from '../../components/common/FormControls';
import { PageHeader } from '../../components/common/PageHeader';
import { bugService } from '../../services/bugService';
import { projectService } from '../../services/projectService';
import { userService } from '../../services/userService';

const SEVERITY_TONE = { CRITICAL: 'red', HIGH: 'amber', MEDIUM: 'neutral', LOW: 'neutral' };
const STATUS_TONE = { OPEN: 'red', IN_PROGRESS: 'amber', RESOLVED: 'emerald', VERIFIED: 'brand', REOPENED: 'amber', CLOSED: 'neutral' };

export function BugListPage() {
  const { hasRole } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [severity, setSeverity] = useState('');
  const [page, setPage] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [projects, setProjects] = useState([]);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ projectId: '', title: '', description: '', severity: 'MEDIUM', priority: 'MEDIUM', assigneeId: '' });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);

  const load = async (overrides = {}) => {
    setLoading(true);
    setError(null);
    try {
      const result = await bugService.listBugs({
        search, status, severity, page, size: 20, ...overrides,
      });
      setData(result);
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [page]);

  const openCreate = async () => {
    setCreateOpen(true);
    setFormError(null);
    try {
      const p = await projectService.listProjects({ size: 100 });
      setProjects(p?.content || []);
      const u = await userService.listUsers({ size: 100 });
      setUsers(u?.content || []);
    } catch (err) {
      toast.error(parseApiError(err).message, 'Failed to load form data');
    }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    if (submitting) return;
    setFormError(null);
    if (!form.projectId || !form.title.trim()) {
      setFormError('Project and title are required.');
      return;
    }
    if (form.description.trim().length < 5) {
      setFormError('Description is required (at least 5 characters).');
      return;
    }
    setSubmitting(true);
    try {
      await bugService.createBug({
        projectId: Number(form.projectId),
        title: form.title.trim(),
        description: form.description.trim() || null,
        severity: form.severity,
        priority: form.priority,
        assigneeId: form.assigneeId ? Number(form.assigneeId) : null,
      });
      toast.success('Bug reported.', 'Created');
      setCreateOpen(false);
      setForm({ projectId: '', title: '', description: '', severity: 'MEDIUM', priority: 'MEDIUM', assigneeId: '' });
      load();
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
      cellClassName: 'font-mono text-xs text-brand-600',
      render: (row) => <Link to={`/bugs/${row.id}`} className="hover:underline">{row.bugKey}</Link>,
    },
    { key: 'title', label: 'Title', cellClassName: 'text-ink' },
    {
      key: 'severity',
      label: 'Severity',
      render: (row) => <Badge tone={SEVERITY_TONE[row.severity] || 'neutral'}>{row.severity}</Badge>,
    },
    {
      key: 'status',
      label: 'Status',
      render: (row) => <Badge tone={STATUS_TONE[row.status] || 'neutral'} dot>{row.status}</Badge>,
    },
    {
      key: 'priority',
      label: 'Priority',
      render: (row) => <Badge tone={SEVERITY_TONE[row.priority] || 'neutral'}>{row.priority}</Badge>,
    },
    {
      key: 'assignee',
      label: 'Assignee',
      render: (row) => row.assignee?.fullName || '—',
    },
    {
      key: 'projectKey',
      label: 'Project',
      cellClassName: 'font-mono text-xs text-ink-muted',
    },
    {
      key: 'createdAt',
      label: 'Created',
      render: (row) => (
        <span className="text-xs text-ink-muted whitespace-nowrap tabular-nums">
          {formatDateTime(row.createdAt)}
        </span>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Bug Tracking"
        description="Track, assign and resolve defects across your projects."
        meta={
          <Badge tone="neutral" mono>
            {data?.totalElements ?? 0} {(data?.totalElements ?? 0) === 1 ? 'bug' : 'bugs'}
          </Badge>
        }
        actions={
          hasRole(['ADMIN', 'PROJECT_MANAGER', 'QA_TESTER']) && (
            <Button variant="primary" size="sm" onClick={openCreate} leftIcon={<Plus className="h-3.5 w-3.5" />}>
              Report Bug
            </Button>
          )
        }
      />

      <Card>
        <CardBody>
          <div className="flex flex-col sm:flex-row flex-wrap gap-3">
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search bugs…"
              leftIcon={<Search className="h-4 w-4" />}
              className="flex-1"
            />
            <Select
              aria-label="Filter bugs by status"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              options={[
                { value: '', label: 'All statuses' },
                ...['OPEN', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'REOPENED', 'CLOSED']
                  .map((s) => ({ value: s, label: s.replace('_', ' ') })),
              ]}
              className="w-full sm:w-44"
            />
            <Select
              aria-label="Filter bugs by severity"
              value={severity}
              onChange={(e) => setSeverity(e.target.value)}
              options={[
                { value: '', label: 'All severities' },
                ...['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
                  .map((s) => ({ value: s, label: s })),
              ]}
              className="w-full sm:w-44"
            />
            <Button variant="secondary" size="sm" onClick={() => load()} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
              Apply
            </Button>
          </div>
        </CardBody>
      </Card>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-ink-muted tabular-nums">
          Showing {(data?.content || []).length} of {data?.totalElements ?? 0} bugs
        </p>
        {(search || status || severity) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setSearch(''); setStatus(''); setSeverity(''); setPage(0);
              load({ search: '', status: '', severity: '', page: 0 });
            }}
          >
            Clear filters
          </Button>
        )}
      </div>

      {error ? (
        <ErrorState title="Unable to load bugs" description={error} onRetry={() => load()} retrying={loading} />
      ) : (
        <DataTable
          columns={columns}
          data={data?.content || []}
          loading={loading}
          rowKey="id"
          onRowClick={(row) => { navigate(`/bugs/${row.id}`); }}
          emptyState={{
            icon: BugIcon,
            title: search || status || severity ? 'No bugs match these filters' : 'No bugs found',
            description: search || status || severity
              ? 'Try adjusting or clearing the active filters.'
              : 'Bugs reported against your authorized projects will appear here.',
          }}
          footer={
            data && data.totalPages > 1 ? (
              <div className="px-4 py-3 text-xs text-ink-muted">
                Page {data.page + 1} of {data.totalPages} · {data.totalElements} total
              </div>
            ) : null
          }
        />
      )}

      <Modal isOpen={createOpen} onClose={() => setCreateOpen(false)} title="Report Bug" size="lg">
        <form onSubmit={handleCreate} className="space-y-4" aria-busy={submitting}>
          {formError && (
            <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700">{formError}</div>
          )}
          <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-faint">Bug details</p>
          <Select
            label="Project"
            value={form.projectId}
            onChange={(e) => setForm((f) => ({ ...f, projectId: e.target.value }))}
            options={projects.map((p) => ({ value: p.id, label: `${p.projectKey} · ${p.name}` }))}
            placeholder="Select project"
            required
          />
          <Input
            label="Title"
            value={form.title}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
            required
          />
          <div>
            <label htmlFor="bug-description" className="block text-xs font-medium text-ink-muted mb-1.5">
              Description <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <textarea
              id="bug-description"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={4}
              className="w-full rounded-lg bg-canvas border border-line-strong px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:border-brand-500"
            />
          </div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-faint">Classification &amp; assignment</p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Select
              label="Severity"
              value={form.severity}
              onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value }))}
              options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: s }))}
            />
            <Select
              label="Priority"
              value={form.priority}
              onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
              options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: s }))}
            />
            <Select
              label="Assignee"
              value={form.assigneeId}
              onChange={(e) => setForm((f) => ({ ...f, assigneeId: e.target.value }))}
              options={users.map((u) => ({ value: u.id, label: u.fullName }))}
              placeholder="Unassigned"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setCreateOpen(false)} disabled={submitting}>Cancel</Button>
            <Button type="submit" variant="primary" size="sm" loading={submitting}>Create Bug</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}