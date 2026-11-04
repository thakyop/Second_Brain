import { Fragment } from 'react'
import type { ChatMessage } from '../api/types.ts'
import { sourceElementId, splitCitations } from '../lib/citations.ts'

/**
 * An answer's text, with citations like [1] turned into links to its
 * sources. Everything is rendered as text, never as HTML, so nothing the
 * model writes can run in the page.
 */
export function AnswerText({ message }: { message: ChatMessage }) {
  const numbers = new Set(message.sources.map((source) => source.number))
  // Paragraphs are separated by blank lines; single newlines become line breaks.
  const paragraphs = message.content.split(/\n\s*\n/)

  return (
    <div className="answer-text">
      {paragraphs.map((paragraph, p) => (
        <p key={p}>
          {paragraph.split('\n').map((line, l) => (
            <Fragment key={l}>
              {l > 0 && <br />}
              {splitCitations(line, numbers).map((part, i) =>
                part.kind === 'text' ? (
                  <Fragment key={i}>{part.text}</Fragment>
                ) : (
                  <a
                    key={i}
                    className="citation"
                    href={`#${sourceElementId(message.id, part.number)}`}
                    aria-label={`Source ${part.number}`}
                    onClick={() => openSource(message.id, part.number)}
                  >
                    {part.number}
                  </a>
                ),
              )}
            </Fragment>
          ))}
        </p>
      ))}
    </div>
  )
}

// Unfold the source's excerpt; the link itself scrolls to it.
function openSource(messageId: string, number: number) {
  const source = document.getElementById(sourceElementId(messageId, number))
  if (source instanceof HTMLDetailsElement) {
    source.open = true
  }
}
