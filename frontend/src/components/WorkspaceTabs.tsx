import { NavLink } from 'react-router'

/** Switches between a workspace's documents and its chat. */
export function WorkspaceTabs({ workspaceId }: { workspaceId: string }) {
  const base = `/workspaces/${workspaceId}`
  return (
    <nav className="tabs" aria-label="Workspace sections">
      {/* NavLink marks the current tab with class "active" and aria-current. */}
      <NavLink to={base} end className="tab">Documents</NavLink>
      <NavLink to={`${base}/chat`} className="tab">Chat</NavLink>
    </nav>
  )
}
