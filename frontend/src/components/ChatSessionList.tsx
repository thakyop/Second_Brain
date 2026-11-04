import { Link, NavLink } from 'react-router'
import type { ChatSession } from '../api/types.ts'
import { useNow } from '../hooks/useNow.ts'
import { timeAgo } from '../lib/format.ts'

interface Props {
  workspaceId: string
  sessions: ChatSession[] | null
}

/** Your earlier chats in this workspace, and a button to start a new one. */
export function ChatSessionList({ workspaceId, sessions }: Props) {
  const now = useNow()
  const base = `/workspaces/${workspaceId}/chat`
  return (
    <aside className="chat-sidebar" aria-label="Your chats">
      <Link to={base} className="button button-primary button-block">New chat</Link>
      {sessions === null ? (
        <p className="chat-sidebar-note">Loading chats…</p>
      ) : sessions.length === 0 ? (
        <p className="chat-sidebar-note">No chats yet.</p>
      ) : (
        <ul className="chat-session-list">
          {sessions.map((session) => (
            <li key={session.id}>
              <NavLink to={`${base}/${session.id}`} className="chat-session">
                <span className="chat-session-title">{session.title}</span>
                <span className="chat-session-time">{timeAgo(session.createdAt, now)}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      )}
    </aside>
  )
}
