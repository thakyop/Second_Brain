import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { describeError } from '../api/client.ts'
import { createWorkspace, listWorkspaces } from '../api/endpoints.ts'
import type { Workspace } from '../api/types.ts'
import { useToken } from '../auth/AuthContext.ts'
import { formatDate } from '../lib/format.ts'

// The same limit as WorkspaceRequests.CreateWorkspaceRequest on the backend.
const NAME_MAX = 150

/** Your workspaces, and a form to create one. */
export function WorkspacesPage() {
  const token = useToken()
  const navigate = useNavigate()
  const [workspaces, setWorkspaces] = useState<Workspace[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    listWorkspaces(token, controller.signal)
      .then((list) => setWorkspaces([...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt))))
      .catch((error) => {
        if (!controller.signal.aborted) {
          setLoadError(describeError(error))
        }
      })
    return () => controller.abort()
  }, [token, attempt])

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setCreateError('Give the workspace a name.')
      return
    }
    setCreating(true)
    setCreateError(null)
    try {
      const workspace = await createWorkspace(token, trimmed)
      navigate(`/workspaces/${workspace.id}`)
    } catch (error) {
      setCreateError(describeError(error, { 400: `Use a name of 1 to ${NAME_MAX} characters.` }))
      setCreating(false)
    }
  }

  function retry() {
    setLoadError(null)
    setAttempt((count) => count + 1)
  }

  return (
    <>
      <div className="page-header">
        <h1>Workspaces</h1>
        <p className="page-subtitle">Each workspace keeps its own set of documents.</p>
      </div>

      <form className="card create-form" onSubmit={handleCreate}>
        <label htmlFor="workspace-name" className="field-label">Workspace name</label>
        <div className="create-row">
          <input
            id="workspace-name"
            className="input"
            maxLength={NAME_MAX}
            placeholder="For example: Operating systems notes"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <button type="submit" className="button button-primary" disabled={creating}>
            {creating ? 'Creating…' : 'Create workspace'}
          </button>
        </div>
        {createError && <p className="form-error" role="alert">{createError}</p>}
      </form>

      {loadError ? (
        <div className="state-box state-error" role="alert">
          <p>{loadError}</p>
          <button type="button" className="button" onClick={retry}>Try again</button>
        </div>
      ) : workspaces === null ? (
        <p className="state-box" role="status">
          <span className="spinner" aria-hidden="true" /> Loading workspaces…
        </p>
      ) : workspaces.length === 0 ? (
        <div className="state-box">
          <p className="state-title">No workspaces yet</p>
          <p>Create one above, then upload PDFs, text or Markdown files to it.</p>
        </div>
      ) : (
        <ul className="workspace-grid">
          {workspaces.map((workspace) => (
            <li key={workspace.id}>
              <Link to={`/workspaces/${workspace.id}`} className="workspace-card">
                <span className="workspace-name">{workspace.name}</span>
                <span className="workspace-meta">
                  <span className="role-badge">{workspace.role === 'OWNER' ? 'Owner' : 'Member'}</span>
                  <span>Created {formatDate(workspace.createdAt)}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
