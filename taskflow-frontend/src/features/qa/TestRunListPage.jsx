import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Plus, RefreshCw } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { parseApiError } from '../../utils/errorHandler';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody } from '../../components/common/Card';
import { DataTable } from '../../components/common/DataTable';
import { ErrorState } from '../../components/common/Loading';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { Select } from '../../components/common/FormControls';
import { PageHeader } from '../../components/common/PageHeader';
import { qaService } from '../../services/qaService';
import { projectService } from '../../services/projectService';
import { userService } from '../../services/userService';

const STATUS_TONE = { PLANNED: 'neutral', IN_PROGRESS: 'amber', COMPLETED: 'emerald', CANCELLED: 'red' };

export function TestRunListPage() {
  const { hasRole } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [projects, setProjects] = useState([]);
  const [suites, setSuites] = useState([]);
  const [cases, setCases] = useState([]);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ projectId: '', name: '', suiteId: '', assignedToId: '', selectedCases: [] });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await qaService.listRuns({ page, size: 20 });
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

  const loadSuitesAndCases = async (projectId) => {
    if (!projectId) return;
    try {
      const s = await qaService.listSuites({ projectId, size: 100 });
      setSuites(s?.content || []);
      const c = await qaService.listTestCases({ projectId, size: 100 });
      setCases(c?.content || []);
    } catch (err) {
      toast.error(parseApiError(err).message, 'Failed to load suites/cases');
    }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    if (submitting) return;
    setFormError(null);
    if (!form.projectId || !form.name.trim()) {
      setFormError('Project and name are required.');
      return;
    }
    setSubmitting(true);
    try {
      const run = await qaService.createRun({
        projectId: Number(form.projectId),
        suiteId: form.suiteId ? Number(form.suiteId) : null,
        name: form.name.trim(),
        assignedToId: form.assignedToId ? Number(form.assignedToId) : null,
        testCaseIds: form.selectedCases.length ? form.selectedCases : undefined,
      });
      toast.success('Test run created.', 'Created');
      setCreateOpen(false);
      setForm({ projectId: '', name: '', suiteId: '', assignedToId: '', selectedCases: [] });
      navigate(`/qa/test-runs/${run.id}`);
    } catch (err) {
      setFormError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'name', label: 'Run', cellClassName: 'text-ink' },
    { key: 'projectKey', label: 'Project', cellClassName: 'font-mono text-xs text-ink-muted', render: (r) => r.project?.projectKey },
    { key: 'status', label: 'Status', render: (r) => <Badge tone={STATUS_TONE[r.status] || 'neutral'} dot>{r.status}</Badge> },
    { key: 'assignedTo', label: 'Assigned', render: (r) => r.assignedTo?.fullName || '—' },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Test Runs"
        description="Execute and track test runs."
        actions={
          hasRole(['ADMIN', 'PROJECT_MANAGER', 'QA_TESTER']) && (
            <Button variant="primary" size="sm" onClick={openCreate} leftIcon={<Plus className="h-3.5 w-3.5" />}>
              New Test Run
            </Button>
          )
        }
      />
      {error ? (
        <ErrorState title="Unable to load test runs" description={error} onRetry={load} retrying={loading} />
      ) : (
        <DataTable
          columns={columns}
          data={data?.content || []}
          loading={loading}
          rowKey="id"
          onRowClick={(row) => { navigate(`/qa/test-runs/${row.id}`); }}
          emptyState={{ title: 'No test runs yet', description: 'Create a run to start executing.' }}
          footer={
            data && data.totalPages > 1 ? (
              <div className="px-4 py-3 text-xs text-ink-muted">
                Page {data.page + 1} of {data.totalPages} · {data.totalElements} total
              </div>
            ) : null
          }
        />
      )}

      <Modal isOpen={createOpen} onClose={() => setCreateOpen(false)} title="Create Test Run" size="lg">
        <form onSubmit={handleCreate} className="space-y-4">
          {formError && (
            <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700">{formError}</div>
          )}
          <Select
            label="Project"
            value={form.projectId}
            onChange={(e) => {
              setForm((f) => ({ ...f, projectId: e.target.value, suiteId: '', selectedCases: [] }));
              loadSuitesAndCases(e.target.value);
            }}
            options={projects.map((p) => ({ value: p.id, label: `${p.projectKey} · ${p.name}` }))}
            placeholder="Select project"
            required
          />
          <Input
            label="Run Name"
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            required
          />
          <Select
            label="Suite (optional)"
            value={form.suiteId}
            onChange={(e) => setForm((f) => ({ ...f, suiteId: e.target.value }))}
            options={suites.map((s) => ({ value: s.id, label: s.name }))}
            placeholder="None"
          />
          <Select
            label="Assign To"
            value={form.assignedToId}
            onChange={(e) => setForm((f) => ({ ...f, assignedToId: e.target.value }))}
            options={users.map((u) => ({ value: u.id, label: u.fullName }))}
            placeholder="Unassigned"
          />
          {form.suiteId === '' && (
            <div>
              <label className="block text-xs font-medium text-ink-muted mb-1.5">Test Cases</label>
              <div className="max-h-48 overflow-y-auto rounded-lg border border-line bg-subtle/40 p-2 space-y-1">
                {cases.map((c) => (
                  <label key={c.id} className="flex items-center gap-2 text-sm text-ink">
                    <input
                      type="checkbox"
                      checked={form.selectedCases.includes(c.id)}
                      onChange={() => setForm((f) => ({
                        ...f,
                        selectedCases: f.selectedCases.includes(c.id)
                          ? f.selectedCases.filter((x) => x !== c.id)
                          : [...f.selectedCases, c.id],
                      }))}
                    />
                    {c.testKey} · {c.title}
                  </label>
                ))}
              </div>
            </div>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setCreateOpen(false)} disabled={submitting}>Cancel</Button>
            <Button type="submit" variant="primary" size="sm" loading={submitting}>Create Run</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
