// TypeScript copies of the backend's response records. Keep them in sync with
//   auth/AuthResponse.java
//   workspace/WorkspaceResponse.java
//   Document/DocumentResponse.java
//   chat/ChatSource.java, ChatMessageResponse.java, ChatAnswerResponse.java, ChatSessionResponse.java
// UUIDs and Instants arrive as strings.

export interface AuthResponse {
  accessToken: string
  tokenType: string
  /** Seconds until the token expires. */
  expiresIn: number
  userId: string
  email: string
}

export interface Workspace {
  id: string
  name: string
  ownerId: string
  createdAt: string
  role: 'OWNER' | 'MEMBER'
}

export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED'

// Named DocumentInfo because the browser already has a global `Document` type.
export interface DocumentInfo {
  id: string
  workspaceId: string
  uploadedBy: string
  filename: string
  contentType: string
  sizeBytes: number
  status: DocumentStatus
  createdAt: string
}

/** A passage an answer is based on. The answer cites it as [number]. */
export interface ChatSource {
  number: number
  documentId: string
  filename: string
  /** Counts from 0; shown to people as "part chunkIndex + 1". */
  chunkIndex: number
  excerpt: string
}

export interface ChatMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  /** Empty for questions. */
  sources: ChatSource[]
  createdAt: string
}

export interface ChatAnswer {
  sessionId: string
  question: ChatMessage
  answer: ChatMessage
}

export interface ChatSession {
  id: string
  /** The start of the chat's first question. */
  title: string
  createdAt: string
}
