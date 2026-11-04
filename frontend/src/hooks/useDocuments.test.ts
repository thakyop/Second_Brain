import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client.ts'
import { listDocuments } from '../api/endpoints.ts'
import type { DocumentInfo, DocumentStatus } from '../api/types.ts'
import { POLL_EVERY_MS, SLOW_POLL_EVERY_MS, STUCK_AFTER_MS, useDocuments } from './useDocuments.ts'

vi.mock('../api/endpoints.ts', () => ({ listDocuments: vi.fn() }))
const listMock = vi.mocked(listDocuments)

function doc(status: DocumentStatus, createdAt = new Date().toISOString()): DocumentInfo {
  return {
    id: 'doc-1',
    workspaceId: 'ws-1',
    uploadedBy: 'user-1',
    filename: 'notes.md',
    contentType: 'text/markdown',
    sizeBytes: 120,
    status,
    createdAt,
  }
}

// Moves the fake clock forward and lets the mocked requests finish.
async function wait(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
}

describe('useDocuments', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    listMock.mockReset()
  })

  it('checks again while a document is processing, and stops once it is ready', async () => {
    listMock
      .mockResolvedValueOnce([doc('UPLOADED')])
      .mockResolvedValueOnce([doc('PROCESSING')])
      .mockResolvedValueOnce([doc('READY')])

    const { result } = renderHook(() => useDocuments('token', 'ws-1'))
    await wait(0)
    expect(result.current.documents?.[0].status).toBe('UPLOADED')

    await wait(POLL_EVERY_MS)
    expect(result.current.documents?.[0].status).toBe('PROCESSING')

    await wait(POLL_EVERY_MS)
    expect(result.current.documents?.[0].status).toBe('READY')

    await wait(POLL_EVERY_MS * 10)
    expect(listMock).toHaveBeenCalledTimes(3)
  })

  it('does not check again when nothing is being processed', async () => {
    listMock.mockResolvedValue([doc('READY'), { ...doc('FAILED'), id: 'doc-2' }])

    renderHook(() => useDocuments('token', 'ws-1'))
    await wait(POLL_EVERY_MS * 10)

    expect(listMock).toHaveBeenCalledTimes(1)
  })

  it('stops checking when you leave the page', async () => {
    listMock.mockResolvedValue([doc('PROCESSING')])

    const { unmount } = renderHook(() => useDocuments('token', 'ws-1'))
    await wait(0)
    unmount()
    await wait(POLL_EVERY_MS * 5)

    expect(listMock).toHaveBeenCalledTimes(1)
  })

  it('checks stuck documents less often', async () => {
    const longAgo = new Date(Date.now() - STUCK_AFTER_MS - 1_000).toISOString()
    listMock.mockResolvedValue([doc('PROCESSING', longAgo)])

    renderHook(() => useDocuments('token', 'ws-1'))
    await wait(SLOW_POLL_EVERY_MS - 1)
    expect(listMock).toHaveBeenCalledTimes(1)

    await wait(1)
    expect(listMock).toHaveBeenCalledTimes(2)
  })

  it('reports a failed request', async () => {
    listMock.mockRejectedValue(new ApiError(403, 'Forbidden'))

    const { result } = renderHook(() => useDocuments('token', 'ws-1'))
    await wait(0)

    expect(result.current.documents).toBeNull()
    expect(result.current.error).toMatchObject({ status: 403 })
  })

  it('shows an uploaded document straight away and starts checking on it', async () => {
    listMock.mockResolvedValueOnce([]).mockResolvedValue([doc('PROCESSING')])

    const { result } = renderHook(() => useDocuments('token', 'ws-1'))
    await wait(0)
    expect(result.current.documents).toEqual([])

    act(() => result.current.addDocument(doc('UPLOADED')))
    expect(result.current.documents?.[0].status).toBe('UPLOADED')

    await wait(0)
    expect(result.current.documents?.[0].status).toBe('PROCESSING')
    expect(listMock).toHaveBeenCalledTimes(2)
  })
})
