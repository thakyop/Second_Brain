import { Link, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext.ts'
import { HealthIndicator } from './HealthIndicator.tsx'
import { Logo } from './Logo.tsx'

/** The header and page frame around every logged-in page. */
export function AppLayout() {
  const { session, logOut } = useAuth()
  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-inner">
          <Link to="/workspaces" className="brand">
            <Logo />
            <span>Second Brain</span>
          </Link>
          <div className="header-actions">
            <HealthIndicator />
            <span className="user-email" title={session?.email}>{session?.email}</span>
            <button type="button" className="button button-quiet" onClick={() => logOut()}>
              Log out
            </button>
          </div>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
