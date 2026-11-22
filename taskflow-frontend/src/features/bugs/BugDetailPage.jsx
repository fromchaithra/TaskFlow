import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Clock } from 'lucide-react';
import { useToast } from '../../hooks/useToast';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateTime } from '../../utils/formatters';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { Select } from '../../components/common/FormControls';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { bugService } from '../../services/bugService';

const SEVERITY_TONE = { CRITICAL: 'red', HIGH: 'amber', MEDIUM: 'neutral', LOW: 'neutral' };
const STATUS_TONE = { OPEN: 'red', IN_PROGRESS: 'amber', RESOLVED: 'emerald', VERIFIED: 'brand', REOPENED: 'amber', CLOSED: 'neutral' };

export function BugDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const [bug, setBug] = useState(null);
  const [comments, setComments] = useState([]);
  const [attachments, setAttachments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [commentText, setCommentText] = useState('');
  const [submittingComment, setSubmittingComment] = useState(false);
  const [editingCommentId, setEditingCommentId] = useState(null);
  const [editingCommentText, setEditingCommentText] = useState('');
  const [uploading, setUploading] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editForm, setEditForm] = useState({ title: '', description: '', severity: 'MEDIUM', priority: 'MEDIUM', assigneeId: '' });
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editError, setEditError] = useState(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await bugService.getBugById(id);
      setBug(result);
      setEditForm({
        title: result.title || '',
        description: result.description || '',
        severity: result.severity || 'MEDIUM',
        priority: result.priority || 'MEDIUM',
        assigneeId: result.assignee?.id ? String(result.assignee.id) : '',
      });
      const c = await bugService.listComments(id);
      setComments(Array.isArray(c) ? c : []);
      const a = await bugService.listAttachments(id);
      setAttachments(Array.isArray(a) ? a : []);
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [id]);

  if (loading) {
    return (
      <div className="py-12 flex items-center justify-center">
        <Skeleton className="h-10 w-64" />
      </div>
    );
  }
  if (error || !bug) {
    return (
      <div className="py-12 flex flex-col items-center justify-center text-center">
        <ErrorState title="Unable to load bug" description={error || 'Bug not found.'} onRetry={load} retrying={loading} />
        <Link to="/bugs" className="mt-4 text-sm text-brand-600 hover:underline">
          Back to bugs
        </Link>
      </div>
    );
  }

  const handleStatus = async (status) => {
    try {
      const updated = await bugService.updateStatus(bug.id, status);
      setBug(updated);
      toast.success(`Status changed to ${status}.`, 'Bug updated');
    } catch (err) {
      toast.error(parseApiError(err).message, 'Update failed');
    }
  };

  return (
    <div className="space-y-6">
      <section className="rounded-xl border border-line bg-surface p-5 shadow-card">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0 space-y-2.5">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="cyan" mono>{bug.bugKey}</Badge>
              <Badge tone={SEVERITY_TONE[bug.severity] || 'neutral'}>{bug.severity}</Badge>
              <Badge tone={SEVERITY_TONE[bug.priority] || 'neutral'}>{bug.priority}</Badge>
              <Badge tone={STATUS_TONE[bug.status] || 'neutral'} dot>{bug.status}</Badge>
            </div>
            <h1 className="text-2xl font-semibold tracking-tight text-ink">{bug.title}</h1>
            {bug.description?.trim() ? (
              <p className="max-w-3xl text-sm leading-relaxed text-ink-muted line-clamp-2">
                {bug.description}
              </p>
            ) : (
              <p className="text-sm italic text-ink-faint">No description provided.</p>
            )}
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 pt-1 text-xs text-ink-muted">
              <span>{bug.projectName} ({bug.projectKey})</span>
              <span>Reporter: {bug.reporter?.fullName || '—'}</span>
              <span>Assignee: {bug.assignee?.fullName || 'Unassigned'}</span>
              <span className="inline-flex items-center gap-1">
                <Clock className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                Updated {formatDateTime(bug.updatedAt || bug.createdAt)}
              </span>
            </div>
          </div>
          <div className="flex shrink-0 gap-2">
            <Button variant="secondary" size="sm" onClick={() => setEditOpen(true)}>
              Edit
            </Button>
            <Link to="/bugs" className="inline-flex items-center gap-1.5 text-sm text-ink-muted hover:text-brand-600">
              <ArrowLeft className="h-4 w-4" /> Back
            </Link>
          </div>
        </div>
      </section>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="lg:col-span-2">
          <CardHeader title="Details" />
          <CardBody>
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Project</dt>
                <dd className="mt-1 text-ink">{bug.projectName} ({bug.projectKey})</dd>
              </div>
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Reporter</dt>
                <dd className="mt-1 text-ink">{bug.reporter?.fullName}</dd>
              </div>
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Assignee</dt>
                <dd className="mt-1 text-ink">{bug.assignee?.fullName || 'Unassigned'}</dd>
              </div>
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Priority</dt>
                <dd className="mt-1 text-ink">{bug.priority}</dd>
              </div>
              {bug.stepsToReproduce && (
                <div className="sm:col-span-2">
                  <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Steps to reproduce</dt>
                  <dd className="mt-1 text-ink whitespace-pre-wrap">{bug.stepsToReproduce}</dd>
                </div>
              )}
            </dl>

            <div className="mt-6 flex flex-wrap gap-2">
              {['IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'CLOSED', 'REOPENED'].map((s) => (
                <Button key={s} variant="secondary" size="sm" onClick={() => handleStatus(s)} disabled={bug.status === s}>
                  {s.replace('_', ' ')}
                </Button>
              ))}
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Activity" description="Chronological history of changes." />
          <CardBody>
            {bug.history?.length ? (
              <ul className="space-y-3">
                {bug.history.map((h) => (
                  <li key={h.id} className="text-xs">
                    <p className="text-ink">
                      <span className="font-medium">{h.actorName || 'System'}</span>{' '}
                      <span className="text-ink-muted">{String(h.action || '').toLowerCase().replace(/_/g, ' ')}</span>
                    </p>
                    <p className="text-[11px] text-ink-faint mt-0.5 flex items-center gap-1">
                      <Clock className="h-3 w-3" /> {formatDateTime(h.createdAt)}
                    </p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-ink-muted">No history yet.</p>
            )}
          </CardBody>
        </Card>
      </div>

      <Card>
        <CardHeader title="Attachments" description="Files attached to this bug." />
        <CardBody>
          <div className="space-y-3">
            {attachments.length === 0 ? (
              <p className="text-sm text-ink-muted">No attachments yet.</p>
            ) : (
              <ul className="space-y-2">
                {attachments.map((a) => (
                  <li key={a.id} className="flex items-center justify-between gap-3 rounded-lg border border-line bg-subtle/40 px-3 py-2">
                    <div className="min-w-0">
                      <p className="text-sm text-ink truncate">{a.originalFilename}</p>
                      <p className="text-[10px] text-ink-faint">{a.contentType} · {a.fileSizeBytes} bytes</p>
                    </div>
                    <a
                      href={a.downloadUrl}
                      className="text-xs text-brand-600 hover:underline shrink-0"
                      target="_blank"
                      rel="noreferrer"
                    >
                      Download
                    </a>
                  </li>
                ))}
              </ul>
            )}
            <div>
              <input
                type="file"
                onChange={async (e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  setUploading(true);
                  try {
                    const created = await bugService.uploadAttachment(bug.id, file);
                    setAttachments((prev) => [...prev, created]);
                  } catch (err) {
                    toast.error(parseApiError(err).message, 'Upload failed');
                  } finally {
                    setUploading(false);
                    e.target.value = '';
                  }
                }}
                className="text-xs text-ink-muted"
              />
              {uploading && <p className="text-xs text-ink-muted mt-1">Uploading…</p>}
            </div>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Comments" description="Collaboration on this bug." />
        <CardBody>
          <div className="space-y-4">
            {comments.length === 0 ? (
              <p className="text-sm text-ink-muted">No comments yet.</p>
            ) : (
              <ul className="space-y-3">
                {comments.map((c) => (
                  <li key={c.id} className="rounded-lg border border-line bg-subtle/40 p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs font-semibold text-ink">{c.author?.fullName}</p>
                      <p className="text-[10px] text-ink-faint">{formatDateTime(c.createdAt)}</p>
                    </div>
                    <p className="text-sm text-ink mt-1.5 whitespace-pre-wrap">{c.content}</p>
                  </li>
                ))}
              </ul>
            )}
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                if (!commentText.trim()) return;
                setSubmittingComment(true);
                try {
                  const created = await bugService.addComment(bug.id, commentText.trim());
                  setComments((prev) => [...prev, created]);
                  setCommentText('');
                } catch (err) {
                  toast.error(parseApiError(err).message, 'Comment failed');
                } finally {
                  setSubmittingComment(false);
                }
              }}
              className="flex gap-2"
            >
              <input
                value={commentText}
                onChange={(e) => setCommentText(e.target.value)}
                placeholder="Add a comment…"
                className="flex-1 h-9 rounded-lg bg-canvas border border-line-strong px-3 text-sm text-ink focus:outline-none focus:border-brand-500"
              />
              <Button type="submit" variant="primary" size="sm" loading={submittingComment}>
                Post
              </Button>
            </form>
          </div>
        </CardBody>
      </Card>

      <Modal isOpen={editOpen} onClose={() => setEditOpen(false)} title="Edit Bug" size="lg">
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setEditError(null);
            setEditSubmitting(true);
            try {
              const updated = await bugService.updateBug(bug.id, {
                title: editForm.title.trim(),
                description: editForm.description.trim() || null,
                severity: editForm.severity,
                priority: editForm.priority,
                assigneeId: editForm.assigneeId ? Number(editForm.assigneeId) : null,
              });
              setBug(updated);
              setEditOpen(false);
              toast.success('Bug updated.', 'Saved');
            } catch (err) {
              setEditError(parseApiError(err).message);
            } finally {
              setEditSubmitting(false);
            }
          }}
          className="space-y-4"
        >
          {editError && (
            <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700">{editError}</div>
          )}
          <Input
            label="Title"
            value={editForm.title}
            onChange={(e) => setEditForm((f) => ({ ...f, title: e.target.value }))}
            required
          />
          <div>
            <label htmlFor="bug-edit-description" className="block text-xs font-medium text-ink-muted mb-1.5">Description</label>
            <textarea
              id="bug-edit-description"
              value={editForm.description}
              onChange={(e) => setEditForm((f) => ({ ...f, description: e.target.value }))}
              rows={4}
              className="w-full rounded-lg bg-canvas border border-line-strong px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:border-brand-500"
            />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Select
              label="Severity"
              value={editForm.severity}
              onChange={(e) => setEditForm((f) => ({ ...f, severity: e.target.value }))}
              options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: s }))}
            />
            <Select
              label="Priority"
              value={editForm.priority}
              onChange={(e) => setEditForm((f) => ({ ...f, priority: e.target.value }))}
              options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: s }))}
            />
            <Input
              label="Assignee ID"
              value={editForm.assigneeId}
              onChange={(e) => setEditForm((f) => ({ ...f, assigneeId: e.target.value }))}
              placeholder="User ID"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setEditOpen(false)} disabled={editSubmitting}>Cancel</Button>
            <Button type="submit" variant="primary" size="sm" loading={editSubmitting}>Save</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
