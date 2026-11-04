import type { DocumentStatus } from '../api/types.ts'

const LABELS: Record<DocumentStatus, string> = {
  UPLOADED: 'Queued',
  PROCESSING: 'Processing',
  READY: 'Ready',
  FAILED: 'Failed',
}

export function StatusBadge({ status }: { status: DocumentStatus }) {
  return (
    // aria-live: screen readers announce it when the status changes.
    <span className={`badge badge-${status.toLowerCase()}`} aria-live="polite">
      {status === 'PROCESSING' && <span className="spinner spinner-small" aria-hidden="true" />}
      {LABELS[status]}
    </span>
  )
}
