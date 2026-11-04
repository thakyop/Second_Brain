import { useCallback, useEffect, useState } from 'react'
import { listDocuments } from '../api/endpoints.ts'
import type { DocumentInfo } from '../api/types.ts'
import { sleep } from '../lib/sleep.ts'

/** How often to check while a document is being processed. */
export const POLL_EVERY_MS = 2_000

/** How often to check when every unfinished document looks stuck. */
export const SLOW_POLL_EVERY_MS = 15_000

/**
 * Processing normally takes seconds. Nothing retries ingestion, so a document
 * still unfinished after this long was probably cut off by a backend restart.
 */
export const STUCK_AFTER_MS = 2 * 60 * 1000

export function isPending(document: DocumentInfo): boolean {
  return document.status === 'UPLOADED' || document.status === 'PROCESSING'
}

export function isStuck(document: DocumentInfo, now = Date.now()): boolean {
  return isPending(document) && now - Date.parse(document.createdAt) > STUCK_AFTER_MS
}

/**
 * The workspace's documents. While any of them is still being processed, the
 * list is fetched again every few seconds, so statuses update by themselves.
 */
export function useDocuments(token: string, workspaceId: string) {
  const [documents, setDocuments] = useState<DocumentInfo[] | null>(null)
  const [error, setError] = useState<unknown>(null)
  // Changing this starts loading (and polling) again.
  const [generation, setGeneration] = useState(0)

  useEffect(() => {
    // Aborted when you leave the page: stops the request and the waiting.
    const controller = new AbortController()
    const { signal } = controller

    async function poll() {
      while (!signal.aborted) {
        const list = await listDocuments(token, workspaceId, signal)
        if (signal.aborted) {
          return
        }
        setDocuments(list)
        setError(null)
        const pending = list.filter(isPending)
        if (pending.length === 0) {
          return
        }
        const allStuck = pending.every((document) => isStuck(document))
        await sleep(allStuck ? SLOW_POLL_EVERY_MS : POLL_EVERY_MS, signal)
      }
    }

    poll().catch((pollError) => {
      if (!signal.aborted) {
        setError(pollError)
      }
    })
    return () => controller.abort()
  }, [token, workspaceId, generation])

  /** Shows a just-uploaded document straight away and starts watching it. */
  const addDocument = useCallback((document: DocumentInfo) => {
    setDocuments((current) => [document, ...(current ?? []).filter((d) => d.id !== document.id)])
    setGeneration((g) => g + 1)
  }, [])

  const reload = useCallback(() => setGeneration((g) => g + 1), [])

  return { documents, error, addDocument, reload }
}
