import { useEffect, useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useToast } from '../../hooks/useToast';
import { parseApiError } from '../../utils/errorHandler';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { Select } from '../../components/common/FormControls';
import { PageHeader } from '../../components/common/PageHeader';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { qaService } from '../../services/qaService';

const PRIORITY_TONE = { CRITICAL: 'red', HIGH: 'amber', MEDIUM: 'neutral', LOW: 'neutral' };

export function TestCaseDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const navigate = useNavigate();
  const [tc, setTc] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editForm, setEditForm] = useState({ title: '', description: '', preconditions: '', priority: 'MEDIUM' });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await qaService.getTestCase(id);
      setTc(result);
      try {
        const h = await qaService.listTestCaseExecutions(id);
        setHistory(Array.isArray(h) ? h : []);
      } catch { setHistory([]); }
      setEditForm({
        title: result.title || '',
        description: result.description || '',
        preconditions: result.preconditions || '',
        priority: result.priority || 'MEDIUM',
      });
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [id]);

  const handleArchive = async () => {
    try {
      await qaService.archiveTestCase(id);
      toast.success('Test case archived.', 'Archived');
      navigate('/qa/test-cases');
    } catch (err) {
      toast.error(parseApiError(err).message, 'Archive failed');
    }
  };

  const handleEdit = async (e) => {
    e.preventDefault();
    if (submitting) return;
    setFormError(null);
    setSubmitting(true);
    try {
      const updated = await qaService.updateTestCase(id, {
        title: editForm.title.trim(),
        description: editForm.description.trim() || null,
        preconditions: editForm.preconditions.trim() || null,
        priority: editForm.priority,
      });
      setTc(updated);
      setEditOpen(false);
      toast.success('Test case updated.', 'Saved');
    } catch (err) {
      setFormError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="py-12 flex items-center justify-center">
        <Skeleton className="h-10 w-64" />
      </div>
    );
  }
  if (error || !tc) {
    return (
      <div className="py-12 flex flex-col items-center justify-center text-center">
        <ErrorState title="Unable to load test case" description={error || 'Test case not found.'} onRetry={load} retrying={loading} />
        <Link to="/qa/test-cases" className="mt-4 text-sm text-brand-600 hover:underline">Back</Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={`${tc.testKey} · ${tc.title}`}
        description={tc.description}
        meta={<Badge tone={PRIORITY_TONE[tc.priority] || 'neutral'}>{tc.priority}</Badge>}
        actions={
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={() => setEditOpen(true)}>Edit</Button>
            <Button variant="danger" size="sm" onClick={handleArchive}>Archive</Button>
            <Link to="/qa/test-cases" className="inline-flex items-center gap-1.5 text-sm text-ink-muted hover:text-brand-600">
              <ArrowLeft className="h-4 w-4" /> Back
            </Link>
          </div>
        }
      />
      <Card>
        <CardHeader title="Preconditions" />
        <CardBody>
          <p className="text-sm text-ink whitespace-pre-wrap">{tc.preconditions || 'None'}</p>
        </CardBody>
      </Card>
      <Card>
        <CardHeader title="Steps" description="Ordered steps and expected results." />
        <CardBody>
          {tc.steps?.length ? (
            <ol className="space-y-3">
              {tc.steps.map((s) => (
                <li key={s.id} className="rounded-lg border border-line bg-subtle/40 p-3">
                  <p className="text-xs font-semibold text-ink-faint">Step {s.stepOrder}</p>
                  <p className="text-sm text-ink mt-1">{s.action}</p>
                  <p className="text-xs text-ink-muted mt-1.5">Expected: {s.expectedResult}</p>
                </li>
              ))}
            </ol>
          ) : (
            <p className="text-sm text-ink-muted">No steps defined.</p>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Execution history" description="Previous executions across runs." />
        <CardBody>
          {history.length === 0 ? (
            <p className="text-sm text-ink-muted">No executions yet.</p>
          ) : (
            <ul className="space-y-2">
              {history.map((h) => (
                <li key={h.id} className="rounded-lg border border-line bg-subtle/40 p-3 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-semibold text-ink">{h.run?.name || `Run #${h.runId}`}</span>
                    <span className="text-ink-muted">{h.result}</span>
                  </div>
                  <p className="text-[10px] text-ink-faint mt-1">
                    {h.executedBy?.fullName || '—'} · {h.executedAt ? new Date(h.executedAt).toLocaleString() : ''}
                  </p>
                  {h.actualResult && <p className="text-ink-muted mt-1">Actual: {h.actualResult}</p>}
                  {h.bugId && <p className="text-ink-muted mt-1">Bug: #{h.bugId}</p>}
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>

      <Modal isOpen={editOpen} onClose={() => setEditOpen(false)} title="Edit Test Case" size="lg">
        <form onSubmit={handleEdit} className="space-y-4">
          {formError && (
            <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700">{formError}</div>
          )}
          <Input
            label="Title"
            value={editForm.title}
            onChange={(e) => setEditForm((f) => ({ ...f, title: e.target.value }))}
            required
          />
          <div>
            <label htmlFor="test-case-edit-description" className="block text-xs font-medium text-ink-muted mb-1.5">Description</label>
            <textarea
              id="test-case-edit-description"
              value={editForm.description}
              onChange={(e) => setEditForm((f) => ({ ...f, description: e.target.value }))}
              rows={3}
              className="w-full rounded-lg bg-canvas border border-line-strong px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:border-brand-500"
            />
          </div>
          <div>
            <label htmlFor="test-case-edit-preconditions" className="block text-xs font-medium text-ink-muted mb-1.5">Preconditions</label>
            <textarea
              id="test-case-edit-preconditions"
              value={editForm.preconditions}
              onChange={(e) => setEditForm((f) => ({ ...f, preconditions: e.target.value }))}
              rows={2}
              className="w-full rounded-lg bg-canvas border border-line-strong px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:border-brand-500"
            />
          </div>
          <Select
            label="Priority"
            value={editForm.priority}
            onChange={(e) => setEditForm((f) => ({ ...f, priority: e.target.value }))}
            options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: s }))}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setEditOpen(false)} disabled={submitting}>Cancel</Button>
            <Button type="submit" variant="primary" size="sm" loading={submitting}>Save</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
