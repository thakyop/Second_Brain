import { describe, expect, it } from 'vitest'
import { MAX_UPLOAD_BYTES, uploadProblem } from './uploadRules.ts'

// A File that reports `size` without allocating that much memory.
function file(name: string, type: string, size = 100): File {
  const result = new File(['x'], name, { type })
  Object.defineProperty(result, 'size', { value: size })
  return result
}

describe('uploadProblem', () => {
  it.each([
    ['notes.pdf', 'application/pdf'],
    ['notes.txt', 'text/plain'],
    ['notes.md', 'text/markdown'],
    ['notes.md', ''], // browsers don't always know .md
    ['NOTES.MD', 'application/octet-stream'],
    ['readme', 'TEXT/PLAIN'],
  ])('accepts %s (type "%s")', (name, type) => {
    expect(uploadProblem(file(name, type))).toBeNull()
  })

  it.each([
    ['report.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
    ['data.json', 'application/json'],
    ['photo.png', 'image/png'],
    ['notes.markdown', ''], // the backend only accepts the .md name
  ])('rejects %s (type "%s")', (name, type) => {
    expect(uploadProblem(file(name, type))).toBe('Only PDF, .txt and .md files can be uploaded.')
  })

  it('rejects an empty file', () => {
    expect(uploadProblem(file('notes.txt', 'text/plain', 0))).toBe('This file is empty.')
  })

  it('accepts exactly 20 MB and rejects anything larger', () => {
    expect(uploadProblem(file('big.pdf', 'application/pdf', MAX_UPLOAD_BYTES))).toBeNull()
    expect(uploadProblem(file('big.pdf', 'application/pdf', MAX_UPLOAD_BYTES + 1)))
      .toBe('This file is larger than 20 MB.')
  })
})
