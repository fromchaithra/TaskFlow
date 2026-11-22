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
import { Modal } from '../../components/common/Modal';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/FormControls';
import { PageHeader } from '../../components/common/PageHeader';
import { qaService } from '../../services/qaService';
import { projectService } from '../../services/projectService';

export function TestSuiteListPage() {
  const { hasRole } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [projects, setProjects] = useState([]);
  const [form, setForm] = useState({ projectId: '', name: '', description: '' });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await qaService.listSuites({ page, size: 20 });
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
    if (!form.projectId || !form.name.trim()) {
      setFormError('Project and name are required.');
      return;
    }
    setSubmitting(true);
    try {
      await qaService.createSuite({
        projectId: Number(form.projectId),
        name: form.name.trim(),
        description: form.description.trim() || null,
      });
      toast.success('Suite created.', 'Created');
      setCreateOpen(false);
      setForm({ projectId: '', name: '', description: '' });
      load();
    } catch (err) {
      setFormError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'name', label: 'Name', cellClassName: 'text-ink' },
    { key: 'projectKey', label: 'Project', cellClassName: 'font-mono text-xs text-ink-muted', render: (r) => r.project?.projectKey },
    { key: 'archived', label: 'Status', render: (r) => <Badge tone={r.archived ? 'neutral' : 'emerald'}>{r.archived ? 'Archived' : 'Active'}</Badge> },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Test Suites"
        description="Group test cases into reusable suites."
        actions={
          hasRole(['ADMIN', 'PROJECT_MANAGER', 'QA_TESTER']) && (
            <Button variant="primary" size="sm" onClick={openCreate} leftIcon={<Plus className="h-3.5 w-3.5" />}>
              New Suite
            </Button>
          )
        }
      />

      {error ? (
        <ErrorState title="Unable to load suites" description={error} onRetry={load} retrying={loading} />
      ) : (
        <DataTable
          columns={columns}
          data={data?.content || []}
          loading={loading}
          rowKey="id"
          onRowClick={(row) => { navigate(`/qa/test-suites/${row.id}`); }}
          emptyState={{ title: 'No suites yet', description: 'Create a suite to group test cases.' }}
          footer={
            data && data.totalPages > 1 ? (
              <div className="px-4 py-3 text-xs text-ink-muted">
                Page {data.page + 1} of {data.totalPages} · {data.totalElements} total
              </div>
            ) : null
          }
        />
      )}

      <Modal isOpen={createOpen} onClose={() => setCreateOpen(false)} title="Create Test Suite" size="md">
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
            label="Name"
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            required
          />
          <div>
            <label htmlFor="suite-description" className="block text-xs font-medium text-ink-muted mb-1.5">Description</label>
            <textarea
              id="suite-description"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={3}
              className="w-full rounded-lg bg-canvas border border-line-strong px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:border-brand-500"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setCreateOpen(false)} disabled={submitting}>Cancel</Button>
            <Button type="submit" variant="primary" size="sm" loading={submitting}>Create</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
