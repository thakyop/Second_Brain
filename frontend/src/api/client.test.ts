import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NO_RESPONSE, apiRequest, describeError, setUnauthorizedHandler } from './client.ts'

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

// fetch rejects like this when the request fails before any response.
function stubFetchFailure(error: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(error))
}

describe('apiRequest', () => {
  afterEach(() => setUnauthorizedHandler(null))

  it('sends the token and a JSON body, and returns the parsed response', async () => {
    const fetchMock = stubFetch(Response.json({ id: 'w1' }, { status: 201 }))

    const result = await apiRequest('/api/workspaces', { method: 'POST', token: 'abc', json: { name: 'Notes' } })

    expect(result).toEqual({ id: 'w1' })
    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/workspaces')
    expect(init.method).toBe('POST')
    expect(init.headers).toMatchObject({ Authorization: 'Bearer abc', 'Content-Type': 'application/json' })
    expect(init.body).toBe('{"name":"Notes"}')
  })

  it('sends no Authorization header without a token', async () => {
    const fetchMock = stubFetch(Response.json({ status: 'UP' }))

    await apiRequest('/actuator/health')

    expect(fetchMock.mock.calls[0][1].headers).not.toHaveProperty('Authorization')
  })

  it('leaves the Content-Type of a file upload to the browser', async () => {
    const fetchMock = stubFetch(Response.json({ id: 'd1' }, { status: 201 }))
    const formData = new FormData()

    await apiRequest('/api/workspaces/w1/documents', { method: 'POST', token: 'abc', formData })

    const init = fetchMock.mock.calls[0][1]
    expect(init.headers).not.toHaveProperty('Content-Type')
    expect(init.body).toBe(formData)
  })

  it('throws an ApiError with the status of an error response', async () => {
    stubFetch(Response.json({ status: 409, error: 'Conflict' }, { status: 409 }))

    const request = apiRequest('/api/auth/register', { method: 'POST', json: {} })

    await expect(request).rejects.toBeInstanceOf(ApiError)
    await expect(request).rejects.toMatchObject({ status: 409, message: 'Request failed (HTTP 409)' })
  })

  it('uses the message from the error body when there is one', async () => {
    stubFetch(Response.json({ status: 400, message: 'Name is required' }, { status: 400 }))

    await expect(apiRequest('/api/workspaces', { token: 'abc' })).rejects.toMatchObject({
      status: 400,
      message: 'Name is required',
    })
  })

  it('reports a 401 on a request with a token, so the session can end', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    stubFetch(Response.json({ status: 401 }, { status: 401 }))

    await expect(apiRequest('/api/workspaces', { token: 'expired' })).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not end the session when a login without a token gets 401', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    stubFetch(Response.json({ status: 401 }, { status: 401 }))

    await expect(apiRequest('/api/auth/login', { method: 'POST', json: {} })).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('turns a network failure into "no response"', async () => {
    stubFetchFailure(new TypeError('Failed to fetch'))

    await expect(apiRequest('/api/workspaces')).rejects.toMatchObject({ status: NO_RESPONSE })
  })

  it('treats a 5xx without a JSON body as "no response" (the dev server could not reach the backend)', async () => {
    stubFetch(new Response('', { status: 500 }))

    await expect(apiRequest('/api/workspaces')).rejects.toMatchObject({ status: NO_RESPONSE })
  })

  it('keeps a JSON 500 from the backend as a server error', async () => {
    stubFetch(Response.json({ status: 500, error: 'Internal Server Error' }, { status: 500 }))

    await expect(apiRequest('/api/workspaces')).rejects.toMatchObject({ status: 500 })
  })

  it('passes an abort through unchanged', async () => {
    const controller = new AbortController()
    controller.abort()
    stubFetchFailure(new DOMException('The operation was aborted.', 'AbortError'))

    await expect(apiRequest('/api/workspaces', { signal: controller.signal })).rejects.toMatchObject({
      name: 'AbortError',
    })
  })
})

describe('describeError', () => {
  it('uses the wording given for the status', () => {
    expect(describeError(new ApiError(409, 'Conflict'), { 409: 'Already registered.' })).toBe('Already registered.')
  })

  it('explains a missing response', () => {
    expect(describeError(new ApiError(NO_RESPONSE, ''))).toBe("Can't reach the server. Is the backend running?")
  })

  it('points to the backend log for server errors', () => {
    expect(describeError(new ApiError(500, 'Request failed'))).toContain('backend log')
  })

  it('falls back to the error message', () => {
    expect(describeError(new ApiError(418, 'Request failed (HTTP 418)'))).toBe('Request failed (HTTP 418)')
  })

  it('has a general message for other errors', () => {
    expect(describeError(new Error('boom'))).toBe('Something went wrong. Please try again.')
  })
})
