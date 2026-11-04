import type { DocumentInfo } from '../api/types.ts'
import { isStuck } from '../hooks/useDocuments.ts'
import { useNow } from '../hooks/useNow.ts'
import { formatBytes, timeAgo } from '../lib/format.ts'
import { StatusBadge } from './StatusBadge.tsx'

function fileKind(document: DocumentInfo): string {
  const name = document.filename.toLowerCase()
  if (document.contentType === 'application/pdf' || name.endsWith('.pdf')) {
    return 'PDF'
  }
  if (document.contentType === 'text/markdown' || name.endsWith('.md')) {
    return 'MD'
  }
  return 'TXT'
}

function hintFor(document: DocumentInfo, now: number): string | null {
  if (document.status === 'FAILED') {
    return 'No text could be read from this file (a scanned PDF, for example), '
      + 'or the embedding model was not reachable. The backend log has the details.'
  }
  if (isStuck(document, now)) {
    return 'This is taking much longer than usual. If the backend was restarted, '
      + 'processing stopped; upload the file again.'
  }
  return null
}

export function DocumentList({ documents }: { documents: DocumentInfo[] }) {
  const now = useNow()
  return (
    <ul className="doc-list">
      {documents.map((document) => {
        const hint = hintFor(document, now)
        return (
          <li key={document.id} className="doc-row">
            <span className={`doc-kind doc-kind-${fileKind(document).toLowerCase()}`} aria-hidden="true">
              {fileKind(document)}
            </span>
            <div className="doc-main">
              <span className="doc-name" title={document.filename}>{document.filename}</span>
              <span className="doc-meta">
                {formatBytes(document.sizeBytes)} · uploaded {timeAgo(document.createdAt, now)}
              </span>
              {hint && <span className="doc-hint">{hint}</span>}
            </div>
            <StatusBadge status={document.status} />
          </li>
        )
      })}
    </ul>
  )
}
