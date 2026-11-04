import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import { STORAGE_KEY } from '../auth/session.ts'
import { AuthPage } from './AuthPage.tsx'

// Answers each request path with the given response. The server-status dot
// on the page also asks for /actuator/health.
function respondTo(responses: Record<string, Response>) {
  vi.stubGlobal('fetch', vi.fn(async (path: string) => {
    if (path in responses) {
      return responses[path]
    }
    if (path === '/actuator/health') {
      return Response.json({ status: 'UP' })
    }
    throw new Error(`Unexpected request to ${path}`)
  }))
}

function renderPage(path: '/login' | '/register') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<AuthPage key="login" mode="login" />} />
          <Route path="/register" element={<AuthPage key="register" mode="register" />} />
          <Route path="/workspaces" element={<h1>Workspaces page</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function submit(email: string, password: string, buttonName: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: buttonName }))
}

describe('AuthPage', () => {
  it('says so when the email or password is wrong', async () => {
    respondTo({ '/api/auth/login': Response.json({ status: 401 }, { status: 401 }) })
    renderPage('/login')

    await submit('me@example.com', 'wrong-password', 'Log in')

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong email or password.')
  })

  it('offers to log in when the email is already registered', async () => {
    respondTo({ '/api/auth/register': Response.json({ status: 409 }, { status: 409 }) })
    renderPage('/register')

    await submit('me@example.com', 'password123', 'Create account')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('That email is already registered.')
    expect(within(alert).getByRole('link', { name: 'Log in instead' })).toHaveAttribute('href', '/login')
  })

  it('explains when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    renderPage('/login')

    await submit('me@example.com', 'password123', 'Log in')

    expect(await screen.findByRole('alert')).toHaveTextContent("Can't reach the server. Is the backend running?")
  })

  it('logs you in straight after signing up', async () => {
    respondTo({
      '/api/auth/register': Response.json({
        accessToken: 'new-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
        userId: 'user-1',
        email: 'me@example.com',
      }, { status: 201 }),
    })
    renderPage('/register')

    await submit('me@example.com', 'password123', 'Create account')

    expect(await screen.findByRole('heading', { name: 'Workspaces page' })).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toMatchObject({
      token: 'new-token',
      email: 'me@example.com',
    })
  })

  it('asks for a password of at least 8 characters when signing up', () => {
    respondTo({})
    renderPage('/register')

    expect(screen.getByLabelText('Password')).toHaveAttribute('minLength', '8')
    expect(screen.getByText('8 to 72 characters.')).toBeInTheDocument()
  })
})
