import { describe, expect, it } from 'vitest'
import { sourceElementId, splitCitations } from './citations.ts'

const twoSources = new Set([1, 2])

describe('splitCitations', () => {
  it('leaves text without citations as it is', () => {
    expect(splitCitations('No citations here.', twoSources)).toEqual([{ kind: 'text', text: 'No citations here.' }])
  })

  it('splits out a citation', () => {
    expect(splitCitations('Each process gets a time slice [1].', twoSources)).toEqual([
      { kind: 'text', text: 'Each process gets a time slice ' },
      { kind: 'citation', number: 1 },
      { kind: 'text', text: '.' },
    ])
  })

  it('handles citations next to each other and in a list', () => {
    expect(splitCitations('[1][2]', twoSources)).toEqual([
      { kind: 'citation', number: 1 },
      { kind: 'citation', number: 2 },
    ])
    expect(splitCitations('See [1, 2]', twoSources)).toEqual([
      { kind: 'text', text: 'See ' },
      { kind: 'citation', number: 1 },
      { kind: 'citation', number: 2 },
    ])
  })

  it('keeps numbers that are not sources as plain text', () => {
    expect(splitCitations('Item [3] and [1, 5] stay text, but [2] does not.', twoSources)).toEqual([
      { kind: 'text', text: 'Item [3] and [1, 5] stay text, but ' },
      { kind: 'citation', number: 2 },
      { kind: 'text', text: ' does not.' },
    ])
  })

  it('finds nothing when the answer has no sources', () => {
    expect(splitCitations('Something [1].', new Set())).toEqual([{ kind: 'text', text: 'Something [1].' }])
  })
})

describe('sourceElementId', () => {
  it('is unique per message and source number', () => {
    expect(sourceElementId('msg-1', 2)).toBe('source-msg-1-2')
  })
})
