import { describe, expect, it } from 'vitest'
import { formatBytes, timeAgo } from './format.ts'

describe('formatBytes', () => {
  it.each([
    [0, '0 B'],
    [1023, '1023 B'],
    [1536, '1.5 KB'],
    [250 * 1024, '250 KB'],
    [20 * 1024 * 1024, '20 MB'],
  ])('%i bytes is %s', (bytes, text) => {
    expect(formatBytes(bytes)).toBe(text)
  })
})

describe('timeAgo', () => {
  const now = Date.parse('2026-11-04T12:00:00Z')
  const before = (ms: number) => new Date(now - ms).toISOString()

  it.each([
    [10_000, 'just now'],
    [5 * 60_000, '5 minutes ago'],
    [2 * 3_600_000, '2 hours ago'],
    [24 * 3_600_000, 'yesterday'],
    [3 * 24 * 3_600_000, '3 days ago'],
  ])('%i ms ago is "%s"', (ms, text) => {
    expect(timeAgo(before(ms), now)).toBe(text)
  })

  it('shows the date for anything older than a week', () => {
    expect(timeAgo('2026-10-01T09:00:00Z', now)).toBe('1 Oct 2026')
  })

  it('treats a time slightly in the future as just now', () => {
    expect(timeAgo(new Date(now + 5_000).toISOString(), now)).toBe('just now')
  })
})
