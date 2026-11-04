/** The HTML id of a source, so a citation can link to it. */
export function sourceElementId(messageId: string, number: number) {
  return `source-${messageId}-${number}`
}

/** A piece of an answer: plain text, or a citation such as [2]. */
export type AnswerPart =
  | { kind: 'text'; text: string }
  | { kind: 'citation'; number: number }

// [1], or a list like [1, 2]
const CITATION = /\[(\d{1,3}(?:\s*,\s*\d{1,3})*)\]/g

/**
 * Splits an answer into text and citations. Only numbers that belong to the
 * answer's sources become citations; anything else, such as [7] when there
 * are two sources, stays as plain text.
 */
export function splitCitations(text: string, sourceNumbers: ReadonlySet<number>): AnswerPart[] {
  const parts: AnswerPart[] = []
  let textStart = 0
  for (const match of text.matchAll(CITATION)) {
    const numbers = match[1].split(',').map((n) => Number(n.trim()))
    if (!numbers.every((n) => sourceNumbers.has(n))) {
      continue
    }
    if (match.index > textStart) {
      parts.push({ kind: 'text', text: text.slice(textStart, match.index) })
    }
    for (const number of numbers) {
      parts.push({ kind: 'citation', number })
    }
    textStart = match.index + match[0].length
  }
  if (textStart < text.length) {
    parts.push({ kind: 'text', text: text.slice(textStart) })
  }
  return parts
}
