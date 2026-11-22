import { MessageSquare, Paperclip } from 'lucide-react';

/**
 * Displays verified backend counts (commentCount, attachmentCount) from TaskDetailResponse.
 * Note: TaskController does not currently expose task comment or attachment REST endpoints.
 */
export function TaskComments({ commentCount = 0, attachmentCount = 0 }) {
  return (
    <div className="rounded-lg border border-line bg-canvas p-4 space-y-3">
      <div className="flex flex-wrap items-center gap-4 text-xs text-ink-muted">
        <span className="inline-flex items-center gap-1.5 font-mono tabular-nums">
          <MessageSquare className="h-3.5 w-3.5 text-ink-muted" aria-hidden="true" />
          <span>
            {Number(commentCount)} {Number(commentCount) === 1 ? 'comment' : 'comments'}
          </span>
        </span>
        <span className="text-ink-faint" aria-hidden="true">
          ·
        </span>
        <span className="inline-flex items-center gap-1.5 font-mono tabular-nums">
          <Paperclip className="h-3.5 w-3.5 text-ink-muted" aria-hidden="true" />
          <span>
            {Number(attachmentCount)} {Number(attachmentCount) === 1 ? 'attachment' : 'attachments'}
          </span>
        </span>
      </div>

      <p className="text-xs text-ink-muted leading-relaxed">
        Task comment creation and file attachment endpoints are not yet exposed by the backend REST
        API. You can attach workflow remarks when updating task details, status transitions, or
        reassignments — remarks are recorded in the Activity History below.
      </p>
    </div>
  );
}
