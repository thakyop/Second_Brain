import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { describeError } from '../api/client.ts'
import { askQuestion, listChatMessages, listChatSessions } from '../api/endpoints.ts'
import type { ChatMessage, ChatSession } from '../api/types.ts'
import { useToken } from '../auth/AuthContext.ts'
import { AnswerText } from '../components/AnswerText.tsx'
import { BackToWorkspaces } from '../components/BackToWorkspaces.tsx'
import { ChatSessionList } from '../components/ChatSessionList.tsx'
import { SourceList } from '../components/SourceList.tsx'
import { WorkspaceTabs } from '../components/WorkspaceTabs.tsx'
import { useNow } from '../hooks/useNow.ts'
import { useWorkspace } from '../hooks/useWorkspace.ts'

// The backend's limit (ChatRequests.AskRequest).
const MESSAGE_MAX = 4000

/** Ask questions about a workspace's documents. */
export function ChatPage() {
  const { workspaceId = '', sessionId } = useParams()
  // A new key per workspace resets everything when you switch workspaces.
  return <ChatView key={workspaceId} workspaceId={workspaceId} sessionId={sessionId ?? null} />
}

function ChatView({ workspaceId, sessionId }: { workspaceId: string; sessionId: string | null }) {
  const token = useToken()
  const workspace = useWorkspace(token, workspaceId)
  const [sessions, setSessions] = useState<ChatSession[] | null>(null)
  const [sessionsVersion, setSessionsVersion] = useState(0)
  const [pageError, setPageError] = useState<unknown>(null)

  // Your chats, for the list on the side. Loaded again when a chat starts.
  useEffect(() => {
    const controller = new AbortController()
    listChatSessions(token, workspaceId, controller.signal)
      .then(setSessions)
      .catch((error) => {
        if (!controller.signal.aborted) {
          setPageError(error)
        }
      })
    return () => controller.abort()
  }, [token, workspaceId, sessionsVersion])

  if (pageError) {
    return (
      <>
        <BackToWorkspaces />
        <div className="state-box state-error" role="alert">
          <p>{describeError(pageError, {
            400: "This workspace doesn't exist.",
            403: "You're not a member of this workspace.",
            404: "This workspace doesn't exist.",
          })}</p>
          <Link to="/workspaces" className="button">Back to workspaces</Link>
        </div>
      </>
    )
  }

  return (
    <>
      <BackToWorkspaces />
      <div className="page-header">
        <h1>{workspace?.name ?? 'Workspace'}</h1>
        <p className="page-subtitle">Ask questions. Answers come from this workspace's documents.</p>
      </div>
      <WorkspaceTabs workspaceId={workspaceId} />

      <div className="chat-layout">
        <ChatSessionList workspaceId={workspaceId} sessions={sessions} />
        {/* One Conversation per chat: opening another chat starts a fresh one. */}
        <Conversation
          key={sessionId ?? 'new'}
          token={token}
          workspaceId={workspaceId}
          sessionId={sessionId}
          onNewChat={() => setSessionsVersion((version) => version + 1)}
        />
      </div>
    </>
  )
}

/** Sent along when a new chat gets its address, so its messages show at once. */
interface ChatLocationState {
  messages?: ChatMessage[]
}

interface ConversationProps {
  token: string
  workspaceId: string
  /** null for a new chat */
  sessionId: string | null
  onNewChat: () => void
}

function Conversation({ token, workspaceId, sessionId, onNewChat }: ConversationProps) {
  const navigate = useNavigate()
  const location = useLocation()
  // null while loading
  const [messages, setMessages] = useState<ChatMessage[] | null>(() =>
    sessionId === null ? [] : ((location.state as ChatLocationState | null)?.messages ?? null))
  const [loadError, setLoadError] = useState<unknown>(null)
  const [draft, setDraft] = useState('')
  const [pending, setPending] = useState<{ question: string; startedAt: number } | null>(null)
  const [sendError, setSendError] = useState<string | null>(null)
  // Becomes true when you open another chat while an answer is on its way.
  const left = useRef(false)
  const threadEnd = useRef<HTMLDivElement>(null)

  useEffect(() => {
    left.current = false
    return () => {
      left.current = true
    }
  }, [])

  // The chat's messages. Messages sent along with the address show first
  // and are refreshed here.
  useEffect(() => {
    if (sessionId === null) {
      return
    }
    const controller = new AbortController()
    listChatMessages(token, workspaceId, sessionId, controller.signal)
      .then(setMessages)
      .catch((error) => {
        if (!controller.signal.aborted) {
          setLoadError(error)
        }
      })
    return () => controller.abort()
  }, [token, workspaceId, sessionId])

  // Keep the newest message in view.
  const messageCount = messages?.length ?? 0
  useEffect(() => {
    threadEnd.current?.scrollIntoView?.({ block: 'nearest' })
  }, [messageCount, pending])

  async function send() {
    const question = draft.trim()
    if (!question || pending) {
      return
    }
    setPending({ question, startedAt: Date.now() })
    setDraft('')
    setSendError(null)
    try {
      const reply = await askQuestion(token, workspaceId, question, sessionId ?? undefined)
      if (sessionId === null) {
        onNewChat()
      }
      if (left.current) {
        // You opened another chat meanwhile. The answer is saved and shows
        // when you come back to this one.
        return
      }
      if (sessionId === null) {
        navigate(`/workspaces/${workspaceId}/chat/${reply.sessionId}`, {
          replace: true,
          state: { messages: [reply.question, reply.answer] } satisfies ChatLocationState,
        })
      } else {
        setMessages((current) => [...(current ?? []), reply.question, reply.answer])
      }
    } catch (error) {
      if (!left.current) {
        setDraft(question) // keep the question, so it can be sent again
        setSendError(askErrorText(error))
      }
    } finally {
      setPending(null)
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter sends and Shift+Enter starts a new line (not while an IME is composing).
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      send()
    }
  }

  return (
    <section className="chat-main" aria-label="Chat">
      <div className="thread" aria-live="polite" aria-relevant="additions">
        {loadError ? (
          <p className="state-box state-error" role="alert">
            {describeError(loadError, { 400: "This chat doesn't exist.", 404: "This chat doesn't exist." })}
          </p>
        ) : messages === null ? (
          <p className="state-box" role="status">
            <span className="spinner" aria-hidden="true" /> Loading chat…
          </p>
        ) : messages.length === 0 && !pending ? (
          <div className="chat-empty">
            <p className="state-title">Ask about your documents</p>
            <p>Answers use only this workspace's Ready documents, with numbered sources you can check.</p>
          </div>
        ) : (
          messages.map((message) => <MessageBubble key={message.id} message={message} />)
        )}
        {pending && (
          <>
            <div className="bubble bubble-question">{pending.question}</div>
            <ThinkingBubble startedAt={pending.startedAt} />
          </>
        )}
        <div ref={threadEnd} />
      </div>

      <form
        className="composer"
        onSubmit={(event) => {
          event.preventDefault()
          send()
        }}
      >
        <label htmlFor="question" className="visually-hidden">Your question</label>
        <textarea
          id="question"
          className="input composer-input"
          rows={2}
          maxLength={MESSAGE_MAX}
          placeholder="Ask about your documents…"
          value={draft}
          disabled={pending !== null}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
        />
        <button type="submit" className="button button-primary" disabled={pending !== null || !draft.trim()}>
          Send
        </button>
      </form>
      {sendError && <p className="form-error" role="alert">{sendError}</p>}
      <p className="composer-hint">Enter to send, Shift+Enter for a new line.</p>
    </section>
  )
}

function MessageBubble({ message }: { message: ChatMessage }) {
  if (message.role === 'USER') {
    return <div className="bubble bubble-question">{message.content}</div>
  }
  return (
    <div className="bubble bubble-answer">
      <AnswerText message={message} />
      <SourceList message={message} />
    </div>
  )
}

/** Shown while the model writes. A local model can take half a minute. */
function ThinkingBubble({ startedAt }: { startedAt: number }) {
  const now = useNow(1000)
  const seconds = Math.max(0, Math.round((now - startedAt) / 1000))
  return (
    <div className="bubble bubble-answer bubble-thinking">
      <span className="spinner" aria-hidden="true" />
      <span role="status">Thinking…</span>
      {/* Hidden from screen readers, so they don't announce every second. */}
      <span className="thinking-time" aria-hidden="true">{seconds} s</span>
    </div>
  )
}

function askErrorText(error: unknown): string {
  return describeError(error, {
    400: `Write a question of up to ${MESSAGE_MAX} characters.`,
    403: "You're not a member of this workspace.",
    404: 'This chat or workspace no longer exists.',
    503: "The language model isn't available. Is Ollama running, with the chat model (llama3.2:3b) pulled?",
  })
}
