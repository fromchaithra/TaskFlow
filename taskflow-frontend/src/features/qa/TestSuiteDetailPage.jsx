import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Plus, RefreshCw, Trash2 } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { parseApiError } from '../../utils/errorHandler';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { DataTable } from '../../components/common/DataTable';
import { EmptyState, ErrorState, Skeleton } from '../../components/common/Loading';
import { Modal } from '../../components/common/Modal';
import { Select } from '../../components/common/FormControls';
import { PageHeader } from '../../components/common/PageHeader';
import { qaService } from '../../services/qaService';
import { projectService } from '../../services/projectService';

export function TestSuiteDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const [suite, setSuite] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [addOpen, setAddOpen] = useState(false);
  const [availableCases, setAvailableCases] = useState([]);
  const [selected, setSelected] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await qaService.getSuite(id);
      setSuite(result);
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [id]);

  const openAdd = async () => {
    setAddOpen(true);
    setSelected([]);
    try {
      const data = await qaService.listTestCases({ projectId: suite?.project?.id, size: 100 });
      setAvailableCases(data?.content || []);
    } catch (err) {
      toast.error(parseApiError(err).message, 'Failed to load cases');
    }
  };

  const toggle = (caseId) => {
    setSelected((prev) =>
      prev.includes(caseId) ? prev.filter((x) => x !== caseId) : [...prev, caseId]
    );
  };

  const handleAdd = async () => {
    if (submitting) return;
    setSubmitting(true);
    try {
      for (const cid of selected) {
        try {
          await qaService.addCaseToSuite(id, cid);
        } catch (err) {
          // ignore duplicate errors
        }
      }
      toast.success('Test cases added.', 'Updated');
      setAddOpen(false);
      load();
    } catch (err) {
      toast.error(parseApiError(err).message, 'Add failed');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = async (caseId) => {
    try {
      await qaService.removeCaseFromSuite(id, caseId);
      toast.success('Removed.', 'Updated');
      load();
    } catch (err) {
      toast.error(parseApiError(err).message, 'Remove failed');
    }
  };

  if (loading) {
    return (
      <div className="py-12 flex items-center justify-center">
        <Skeleton className="h-10 w-64" />
      </div>
    );
  }
  if (error || !suite) {
    return (
      <div className="py-12 flex flex-col items-center justify-center text-center">
        <ErrorState title="Unable to load suite" description={error || 'Suite not found.'} onRetry={load} retrying={loading} />
        <Link to="/qa/test-suites" className="mt-4 text-sm text-brand-600 hover:underline">Back</Link>
      </div>
    );
  }

  // Suite detail doesn't return members in DTO; show placeholder count
  return (
    <div className="space-y-6">
      <PageHeader
        title={suite.name}
        description={suite.description}
        meta={<Badge tone="neutral">{suite.archived ? 'Archived' : 'Active'}</Badge>}
        actions={
          <div className="flex gap-2">
            <Button variant="primary" size="sm" onClick={openAdd} leftIcon={<Plus className="h-3.5 w-3.5" />}>
              Add Test Cases
            </Button>
            <Link to="/qa/test-suites" className="inline-flex items-center gap-1.5 text-sm text-ink-muted hover:text-brand-600">
              Back
            </Link>
          </div>
        }
      />
      <Card>
        <CardHeader title="Suite details" />
        <CardBody>
          <p className="text-sm text-ink-muted">Project: {suite.project?.projectKey}</p>
          <p className="text-sm text-ink-muted mt-1">Created by: {suite.createdBy?.fullName}</p>
        </CardBody>
      </Card>

      <Modal isOpen={addOpen} onClose={() => setAddOpen(false)} title="Add Test Cases" size="lg">
        <div className="space-y-4">
          {availableCases.length === 0 ? (
            <EmptyState title="No test cases available" description="No test cases are available for this project yet." />
          ) : (
            <ul className="space-y-2 max-h-80 overflow-y-auto">
              {availableCases.map((tc) => (
                <li key={tc.id} className="flex items-center gap-3 rounded-lg border border-line bg-subtle/40 px-3 py-2">
                  <input
                    type="checkbox"
                    checked={selected.includes(tc.id)}
                    onChange={() => toggle(tc.id)}
                    className="h-4 w-4"
                  />
                  <span className="text-sm text-ink">{tc.testKey} · {tc.title}</span>
                </li>
              ))}
            </ul>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setAddOpen(false)} disabled={submitting}>Cancel</Button>
            <Button variant="primary" size="sm" onClick={handleAdd} loading={submitting} disabled={selected.length === 0}>
              Add {selected.length > 0 ? `(${selected.length})` : ''}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
