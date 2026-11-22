import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Plus, RefreshCw, Search } from 'lucide-react';
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

const PRIORITY_TONE = { CRITICAL: 'red', HIGH: 'amber', MEDIUM: 'neutral', LOW: 'neutral' };

export function TestCaseListPage() {
  const { hasRole } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [projects, setProjects] = useState([]);
  const [form, setForm] = useState({ projectId: '', title: '', description: '', preconditions: '', priority: 'MEDIUM' });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await qaService.listTestCases({ search, page, size: 20 });
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
    } catch (err) {
      toast.error(parseApiError(err).message, 'Failed to load projects');
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
    setSubmitting(true);
    try {
      await qaService.createTestCase({
        projectId: Number(form.projectId),
        title: form.title.trim(),
        description: form.description.trim() || null,
        preconditions: form.preconditions.trim() || null,
        priority: form.priority,
        steps: [],
      });
      toast.success('Test case created.', 'Created');
      setCreateOpen(false);
      setForm({ projectId: '', title: '', description: '', preconditions: '', priority: 'MEDIUM' });
      load();
    } catch (err) {
      setFormError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'testKey', label: 'Key', cellClassName: 'font-mono text-xs text-brand-600' },
    { key: 'title', label: 'Title', cellClassName: 'text-ink' },
    { key: 'priority', label: 'Priority', render: (row) => <Badge tone={PRIORITY_TONE[row.priority] || 'neutral'}>{row.priority}</Badge> },
    { key: 'projectKey', label: 'Project', cellClassName: 'font-mono text-xs text-ink-muted', render: (row) => row.project?.projectKey },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Test Cases"
        description="Manage reusable test cases and their steps."
        actions={
          hasRole(['ADMIN', 'PROJECT_MANAGER', 'QA_TESTER']) && (
            <Button variant="primary" size="sm" onClick={openCreate} leftIcon={<Plus className="h-3.5 w-3.5" />}>
              New Test Case
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
              placeholder="Search test cases…"
              leftIcon={<Search className="h-4 w-4" />}
              className="flex-1"
            />
            <Button variant="secondary" size="sm" onClick={load} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
              Apply
            </Button>
          </div>
        </CardBody>
      </Card>

      {error ? (
        <ErrorState title="Unable to load test cases" description={error} onRetry={load} retrying={loading} />
      ) : (
        <DataTable
          columns={columns}
          data={data?.content || []}
          loading={loading}
          rowKey="id"
          onRowClick={(row) => { navigate(`/qa/test-cases/${row.id}`); }}
          emptyState={{ title: 'No test cases yet', description: 'Create a test case to get started.' }}
          footer={
            data && data.totalPages > 1 ? (
              <div className="px-4 py-3 text-xs text-ink-muted">
                Page {data.page + 1} of {data.totalPages} · {data.totalElements} total
              </div>
            ) : null
          }
        />
      )}

      <Modal isOpen={createOpen} onClose={() => setCreateOpen(false)} title="Create Test Case" size="lg">
        <form onSubmit={handleCreate} className="space-y-4">
          {formError && (
            <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700">{formError}</div>
          )}
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
            <label htmlFor="test-case-description" className="block text-xs font-medium text-ink-muted mb-1.5">Description</label>
            <textarea
              id="test-case-description"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={3}
              className="w-full rounded-lg bg-canvas border border-line-strong px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:border-brand-500"
            />
          </div>
          <div>
            <label htmlFor="test-case-preconditions" className="block text-xs font-medium text-ink-muted mb-1.5">Preconditions</label>
            <textarea
              id="test-case-preconditions"
              value={form.preconditions}
              onChange={(e) => setForm((f) => ({ ...f, preconditions: e.target.value }))}
              rows={2}
              className="w-full rounded-lg bg-canvas border border-line-strong px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:border-brand-500"
            />
          </div>
          <Select
            label="Priority"
            value={form.priority}
            onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
            options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: s }))}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setCreateOpen(false)} disabled={submitting}>Cancel</Button>
            <Button type="submit" variant="primary" size="sm" loading={submitting}>Create</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
