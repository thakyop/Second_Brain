import { useEffect, useState } from 'react'
import { listWorkspaces } from '../api/endpoints.ts'
import type { Workspace } from '../api/types.ts'

/**
 * One of your workspaces, or null while loading (or if it isn't yours).
 * There's no endpoint for a single workspace, so it's found in your list.
 */
export function useWorkspace(token: string, workspaceId: string): Workspace | null {
  const [workspace, setWorkspace] = useState<Workspace | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    listWorkspaces(token, controller.signal)
      .then((list) => setWorkspace(list.find((w) => w.id === workspaceId) ?? null))
      .catch(() => {
        // Ignored: the page's own request shows any problem with this workspace.
      })
    return () => controller.abort()
  }, [token, workspaceId])

  return workspace
}
