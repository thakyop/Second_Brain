import { Link } from 'react-router'
import { Logo } from '../components/Logo.tsx'

export function NotFoundPage() {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <Logo size={40} />
          <h1>Page not found</h1>
        </div>
        <p className="auth-subtitle">There's nothing at this address.</p>
        <Link to="/workspaces" className="button button-primary button-block">Go to your workspaces</Link>
      </div>
    </div>
  )
}
