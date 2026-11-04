import type { ChatMessage } from '../api/types.ts'
import { sourceElementId } from '../lib/citations.ts'

/** The passages an answer was based on. Each one unfolds to show its text. */
export function SourceList({ message }: { message: ChatMessage }) {
  if (message.sources.length === 0) {
    return null
  }
  return (
    <div className="sources">
      <p className="sources-title">Sources</p>
      <ol className="source-list">
        {message.sources.map((source) => (
          <li key={source.number}>
            <details id={sourceElementId(message.id, source.number)} className="source">
              <summary>
                <span className="source-number">{source.number}</span>
                <span className="source-name" title={source.filename}>{source.filename}</span>
                <span className="source-part">part {source.chunkIndex + 1}</span>
              </summary>
              <p className="source-excerpt">{source.excerpt}</p>
            </details>
          </li>
        ))}
      </ol>
    </div>
  )
}
