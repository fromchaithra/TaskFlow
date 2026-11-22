import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { CheckCircle2, XCircle, AlertTriangle, Circle } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useToast } from '../../hooks/useToast';
import { parseApiError } from '../../utils/errorHandler';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { qaService } from '../../services/qaService';

const RESULT_TONE = { PASS: 'emerald', FAIL: 'red', BLOCKED: 'amber', NOT_RUN: 'neutral' };

export function TestRunDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const [run, setRun] = useState(null);
  const [executions, setExecutions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const r = await qaService.getRun(id);
      setRun(r);
      const e = await qaService.listExecutions(id);
      setExecutions(Array.isArray(e) ? e : []);
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [id]);

  const record = async (execId, result) => {
    try {
      const updated = await qaService.recordExecution(id, execId, { result, actualResult: '', notes: '' });
      setExecutions((prev) => prev.map((x) => (x.id === execId ? updated : x)));
    } catch (err) {
      toast.error(parseApiError(err).message, 'Update failed');
    }
  };

  const createBug = async (execId) => {
    try {
      const updated = await qaService.createBugFromExecution(id, execId);
      setExecutions((prev) => prev.map((x) => (x.id === execId ? updated : x)));
      toast.success('Bug created from failed execution.', 'Bug linked');
    } catch (err) {
      toast.error(parseApiError(err).message, 'Bug creation failed');
    }
  };

  if (loading) {
    return (
      <div className="py-12 flex items-center justify-center">
        <Skeleton className="h-10 w-64" />
      </div>
    );
  }
  if (error || !run) {
    return (
      <div className="py-12 flex flex-col items-center justify-center text-center">
        <ErrorState title="Unable to load test run" description={error || 'Run not found.'} onRetry={load} retrying={loading} />
        <Link to="/qa/test-runs" className="mt-4 text-sm text-brand-600 hover:underline">Back to runs</Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={run.name}
        description={`Project ${run.projectKey || run.project?.projectKey || 'N/A'} · Status ${run.status}`}
        actions={
          <Link to="/qa/test-runs" className="inline-flex items-center gap-1.5 text-sm text-ink-muted hover:text-brand-600">
            Back
          </Link>
        }
      />

      <div className="space-y-4">
        {executions.map((ex) => (
          <Card key={ex.id}>
            <CardHeader
              title={`${ex.testKey} · ${ex.testTitle}`}
              description={`Result: ${ex.result}`}
              action={<Badge tone={RESULT_TONE[ex.result] || 'neutral'} dot>{ex.result}</Badge>}
            />
            <CardBody>
              <div className="flex flex-wrap gap-2 mb-3">
                {['PASS', 'FAIL', 'BLOCKED', 'NOT_RUN'].map((r) => (
                  <Button
                    key={r}
                    variant={ex.result === r ? 'primary' : 'secondary'}
                    size="sm"
                    onClick={() => record(ex.id, r)}
                  >
                    {r}
                  </Button>
                ))}
                {ex.result === 'FAIL' && !ex.bugId && (
                  <Button variant="danger" size="sm" onClick={() => createBug(ex.id)}>
                    Create Bug
                  </Button>
                )}
                {ex.bugId && (
                  <span className="text-xs text-ink-muted self-center">Bug linked: #{ex.bugId}</span>
                )}
              </div>
              <div className="text-xs text-ink-muted space-y-1">
                {ex.executedBy?.fullName && <p>Executed by: {ex.executedBy.fullName}</p>}
                {ex.executedAt && <p>At: {new Date(ex.executedAt).toLocaleString()}</p>}
                {ex.actualResult && <p>Actual: {ex.actualResult}</p>}
                {ex.notes && <p>Notes: {ex.notes}</p>}
              </div>
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}
