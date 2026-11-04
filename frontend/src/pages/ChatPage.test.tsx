import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ChatAnswer, ChatMessage } from '../api/types.ts'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import { saveSession, sessionFrom } from '../auth/session.ts'
import { ChatPage } from './ChatPage.tsx'

const question: ChatMessage = {
  id: 'q1',
  role: 'USER',
  content: 'What is round-robin?',
  sources: [],
  createdAt: '2026-11-04T10:00:00Z',
}

const answer: ChatMessage = {
  id: 'a1',
  role: 'ASSISTANT',
  content: 'Each process gets a time slice [1].',
  sources: [{
    number: 1,
    documentId: 'd1',
    filename: 'notes.md',
    chunkIndex: 0,
    excerpt: 'Round-robin gives each process a time slice.',
  }],
  createdAt: '2026-11-04T10:00:12Z',
}

const chatAnswer: ChatAnswer = { sessionId: 's1', question, answer }

type Handler = () => Response | Promise<Response>

// A fake backend: answers "METHOD path" with the given handler.
function fakeBackend(handlers: Record<string, Handler>) {
  const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${path}`
    const handler = handlers[key]
    if (!handler) {
      throw new Error(`Unexpected request: ${key}`)
    }
    return handler()
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/workspaces/:workspaceId/chat" element={<ChatPage />} />
          <Route path="/workspaces/:workspaceId/chat/:sessionId" element={<ChatPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

const workspaceList = () => Response.json([
  { id: 'w1', name: 'OS notes', ownerId: 'u1', createdAt: '2026-11-04T09:00:00Z', role: 'OWNER' },
])

describe('ChatPage', () => {
  beforeEach(() => {
    saveSession(sessionFrom({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 3600, userId: 'u1', email: 'me@example.com' }))
  })

  it('asks a question and shows the answer with its sources', async () => {
    let sessionLists = 0
    let deliverAnswer: (response: Response) => void = () => {}
    const fetchMock = fakeBackend({
      'GET /api/workspaces': workspaceList,
      'GET /api/workspaces/w1/chat/sessions': () => Response.json(sessionLists++ === 0
        ? []
        : [{ id: 's1', title: 'What is round-robin?', createdAt: '2026-11-04T10:00:00Z' }]),
      'POST /api/workspaces/w1/chat': () => new Promise<Response>((resolve) => {
        deliverAnswer = resolve
      }),
      'GET /api/workspaces/w1/chat/sessions/s1/messages': () => Response.json([question, answer]),
    })
    renderAt('/workspaces/w1/chat')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Your question'), 'What is round-robin?{Enter}')

    expect(await screen.findByText('Thinking…')).toBeInTheDocument()
    expect(screen.getByLabelText('Your question')).toBeDisabled()
    deliverAnswer(Response.json(chatAnswer))

    const citation = await screen.findByRole('link', { name: 'Source 1' })
    expect(citation).toHaveAttribute('href', '#source-a1-1')
    expect(screen.getByText(/Each process gets a time slice/)).toBeInTheDocument()
    expect(screen.getByText('notes.md')).toBeInTheDocument()
    expect(screen.getByText('part 1')).toBeInTheDocument()
    // The new chat appears in the list.
    const chats = await screen.findByRole('complementary', { name: 'Your chats' })
    expect(await within(chats).findByText('What is round-robin?')).toBeInTheDocument()
    // The question was sent without a sessionId, which starts a new chat.
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(post![1]!.body as string)).toEqual({ message: 'What is round-robin?' })
  })

  it('keeps the question and explains when the model is not available', async () => {
    fakeBackend({
      'GET /api/workspaces': workspaceList,
      'GET /api/workspaces/w1/chat/sessions': () => Response.json([]),
      'POST /api/workspaces/w1/chat': () => Response.json({ status: 503 }, { status: 503 }),
    })
    renderAt('/workspaces/w1/chat')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Your question'), 'What is round-robin?{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent("The language model isn't available")
    expect(screen.getByLabelText('Your question')).toHaveValue('What is round-robin?')
  })

  it('shows a saved chat with its sources', async () => {
    fakeBackend({
      'GET /api/workspaces': workspaceList,
      'GET /api/workspaces/w1/chat/sessions': () => Response.json([
        { id: 's1', title: 'What is round-robin?', createdAt: '2026-11-04T10:00:00Z' },
      ]),
      'GET /api/workspaces/w1/chat/sessions/s1/messages': () => Response.json([question, answer]),
    })
    renderAt('/workspaces/w1/chat/s1')

    expect(await screen.findByRole('link', { name: 'Source 1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'OS notes' })).toBeInTheDocument()
    expect(screen.getByText('Round-robin gives each process a time slice.')).toBeInTheDocument()
  })

  it('says so when a chat does not exist', async () => {
    fakeBackend({
      'GET /api/workspaces': workspaceList,
      'GET /api/workspaces/w1/chat/sessions': () => Response.json([]),
      'GET /api/workspaces/w1/chat/sessions/nope/messages': () => Response.json({ status: 404 }, { status: 404 }),
    })
    renderAt('/workspaces/w1/chat/nope')

    expect(await screen.findByRole('alert')).toHaveTextContent("This chat doesn't exist.")
  })
})
