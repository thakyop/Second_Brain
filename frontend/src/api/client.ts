// A small wrapper around fetch for the backend's JSON API.

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/** The status of an ApiError when the request never got an answer from the backend. */
export const NO_RESPONSE = 0

let unauthorizedHandler: (() => void) | null = null

/**
 * Called when a request sent with a token gets 401: the token has expired, or
 * the backend restarted with a new JWT_SECRET. AuthProvider logs out here.
 */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

export interface RequestOptions {
  method?: string
  token?: string
  json?: unknown
  formData?: FormData
  signal?: AbortSignal
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  let body: BodyInit | undefined
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.json)
  } else if (options.formData) {
    // No Content-Type here: the browser sets multipart/form-data with its boundary.
    body = options.formData
  }
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`
  }

  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body,
      signal: options.signal,
    })
  } catch (error) {
    if (options.signal?.aborted) {
      throw error
    }
    throw new ApiError(NO_RESPONSE, "Can't reach the server.")
  }

  if (!response.ok) {
    const errorBody = await readJson(response)
    // The dev server answers 5xx with no JSON body when the backend is down.
    // The backend itself always sends JSON with its errors.
    if (response.status >= 500 && errorBody === undefined) {
      throw new ApiError(NO_RESPONSE, "Can't reach the server.")
    }
    if (response.status === 401 && options.token) {
      unauthorizedHandler?.()
    }
    throw new ApiError(response.status, messageFrom(errorBody) ?? `Request failed (HTTP ${response.status})`)
  }
  return (await response.json()) as T
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json()
  } catch {
    return undefined
  }
}

// Spring Boot's default error body has no message, but a later global
// exception handler may add one.
function messageFrom(body: unknown): string | undefined {
  if (typeof body === 'object' && body !== null && 'message' in body) {
    const { message } = body
    if (typeof message === 'string' && message.trim()) {
      return message
    }
  }
  return undefined
}

/**
 * A message for the user. `byStatus` gives the wording for the statuses a
 * caller expects, such as 409 when signing up.
 */
export function describeError(error: unknown, byStatus: Partial<Record<number, string>> = {}): string {
  if (!(error instanceof ApiError)) {
    return 'Something went wrong. Please try again.'
  }
  const specific = byStatus[error.status]
  if (specific) {
    return specific
  }
  if (error.status === NO_RESPONSE) {
    return "Can't reach the server. Is the backend running?"
  }
  if (error.status >= 500) {
    return 'Something went wrong on the server. The backend log has the details.'
  }
  return error.message
}
