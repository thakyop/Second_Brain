// One function per backend endpoint the frontend uses.
import { apiRequest } from './client.ts'
import type { AuthResponse, ChatAnswer, ChatMessage, ChatSession, DocumentInfo, Workspace } from './types.ts'

export function register(email: string, password: string) {
  return apiRequest<AuthResponse>('/api/auth/register', { method: 'POST', json: { email, password } })
}

export function login(email: string, password: string) {
  return apiRequest<AuthResponse>('/api/auth/login', { method: 'POST', json: { email, password } })
}

export function listWorkspaces(token: string, signal?: AbortSignal) {
  return apiRequest<Workspace[]>('/api/workspaces', { token, signal })
}

export function createWorkspace(token: string, name: string) {
  return apiRequest<Workspace>('/api/workspaces', { method: 'POST', token, json: { name } })
}

/** Newest first. */
export function listDocuments(token: string, workspaceId: string, signal?: AbortSignal) {
  return apiRequest<DocumentInfo[]>(documentsPath(workspaceId), { token, signal })
}

export function uploadDocument(token: string, workspaceId: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiRequest<DocumentInfo>(documentsPath(workspaceId), { method: 'POST', token, formData })
}

/**
 * Asks a question about the workspace's documents. Without a sessionId a new
 * chat is started. Slow: the local model takes seconds to answer.
 */
export function askQuestion(token: string, workspaceId: string, message: string, sessionId?: string) {
  return apiRequest<ChatAnswer>(chatPath(workspaceId), {
    method: 'POST',
    token,
    json: sessionId ? { sessionId, message } : { message },
  })
}

/** Your chats in the workspace, newest first. */
export function listChatSessions(token: string, workspaceId: string, signal?: AbortSignal) {
  return apiRequest<ChatSession[]>(`${chatPath(workspaceId)}/sessions`, { token, signal })
}

/** One chat's messages, oldest first. */
export function listChatMessages(token: string, workspaceId: string, sessionId: string, signal?: AbortSignal) {
  return apiRequest<ChatMessage[]>(
    `${chatPath(workspaceId)}/sessions/${encodeURIComponent(sessionId)}/messages`,
    { token, signal },
  )
}

/** False if the backend doesn't answer within this time. */
const HEALTH_TIMEOUT_MS = 5_000

export async function isServerUp(signal?: AbortSignal): Promise<boolean> {
  const timeout = AbortSignal.timeout(HEALTH_TIMEOUT_MS)
  try {
    const health = await apiRequest<{ status?: string }>('/actuator/health', {
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
    })
    return health.status === 'UP'
  } catch {
    return false
  }
}

function documentsPath(workspaceId: string) {
  return `/api/workspaces/${encodeURIComponent(workspaceId)}/documents`
}

function chatPath(workspaceId: string) {
  return `/api/workspaces/${encodeURIComponent(workspaceId)}/chat`
}
