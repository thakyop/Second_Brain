import { Link } from 'react-router'

export function BackToWorkspaces() {
  return (
    <nav className="breadcrumb" aria-label="Breadcrumb">
      <Link to="/workspaces">← All workspaces</Link>
    </nav>
  )
}
