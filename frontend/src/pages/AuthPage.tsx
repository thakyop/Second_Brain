import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { ApiError, describeError } from '../api/client.ts'
import { login, register } from '../api/endpoints.ts'
import { useAuth } from '../auth/AuthContext.ts'
import { HealthIndicator } from '../components/HealthIndicator.tsx'
import { Logo } from '../components/Logo.tsx'

// The same limits as AuthRequests.RegisterRequest on the backend.
const EMAIL_MAX = 320
const PASSWORD_MIN = 8
const PASSWORD_MAX = 72

type Mode = 'login' | 'register'

/** The log-in and sign-up page. */
export function AuthPage({ mode }: { mode: Mode }) {
  const { session, notice, logIn } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ReactNode>(null)
  const [submitting, setSubmitting] = useState(false)

  // Where to go after logging in: the page that sent you here, if any.
  const from = (location.state as { from?: string } | null)?.from
  if (session) {
    return <Navigate to={from ?? '/workspaces'} replace />
  }

  const isLogin = mode === 'login'

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const response = isLogin
        ? await login(email.trim(), password)
        : await register(email.trim(), password)
      logIn(response) // the next render redirects
    } catch (submitError) {
      setError(messageFor(mode, submitError))
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <Logo size={40} />
          <h1>Second Brain</h1>
        </div>
        <p className="auth-subtitle">
          {isLogin ? 'Log in to your workspaces.' : 'Create an account to start uploading documents.'}
        </p>

        {isLogin && notice && <p className="notice" role="status">{notice}</p>}

        <form className="form" onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="email" className="field-label">Email</label>
            <input
              id="email"
              className="input"
              type="email"
              autoComplete="email"
              required
              maxLength={EMAIL_MAX}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="password" className="field-label">Password</label>
            <input
              id="password"
              className="input"
              type="password"
              autoComplete={isLogin ? 'current-password' : 'new-password'}
              required
              minLength={isLogin ? undefined : PASSWORD_MIN}
              maxLength={PASSWORD_MAX}
              aria-describedby={isLogin ? undefined : 'password-hint'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            {!isLogin && (
              <p id="password-hint" className="field-hint">{PASSWORD_MIN} to {PASSWORD_MAX} characters.</p>
            )}
          </div>

          {error && <p className="form-error" role="alert">{error}</p>}

          <button type="submit" className="button button-primary button-block" disabled={submitting}>
            {isLogin
              ? (submitting ? 'Logging in…' : 'Log in')
              : (submitting ? 'Creating account…' : 'Create account')}
          </button>
        </form>

        <p className="auth-switch">
          {isLogin ? (
            <>New here? <Link to="/register" state={location.state}>Create an account</Link></>
          ) : (
            <>Already have an account? <Link to="/login" state={location.state}>Log in</Link></>
          )}
        </p>
      </div>
      <footer className="auth-footer">
        <HealthIndicator />
      </footer>
    </div>
  )
}

function messageFor(mode: Mode, error: unknown): ReactNode {
  if (mode === 'register' && error instanceof ApiError && error.status === 409) {
    return <>That email is already registered. <Link to="/login">Log in instead</Link>.</>
  }
  return describeError(error, mode === 'login'
    ? { 401: 'Wrong email or password.', 400: 'Enter a valid email and your password.' }
    : { 400: `Enter a valid email and a password of ${PASSWORD_MIN} to ${PASSWORD_MAX} characters.` })
}
