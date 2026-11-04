# Second Brain frontend

A React + TypeScript app, built with Vite, for the Second Brain backend. It covers what the backend does so far:
- sign up and log in;
- create workspaces;
- upload PDF, text and Markdown files, and watch each one go from Queued to Processing to Ready (or Failed);
- **chat** with each workspace's documents (the Chat tab): ask questions, get answers with numbered sources you can open, and come back to earlier chats.

## Run it

Start the backend first: Postgres in Docker, Ollama with `nomic-embed-text` and `llama3.2:3b`, and `./mvnw spring-boot:run` with `JWT_SECRET` set (see `../CLAUDE.md`). Then:

```bash
cd frontend
npm install     # first time only
npm run dev     # open http://localhost:5173
```

The dev server forwards `/api` and `/actuator` to the backend at http://localhost:8080. The browser only talks to one address, so the backend needs no CORS setup. For a backend on another port, run `BACKEND_URL=http://localhost:8089 npm run dev`.

`npm install` may warn that it skipped the install script of `fsevents`. That's fine: it's an optional macOS file-watching helper.

## Commands

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run lint` | Lint with Oxlint |
| `npm run build` | Type-check with TypeScript, then build into `dist/` |
| `npm test` | Unit tests (Vitest and Testing Library). No backend needed. |
| `npm run test:e2e` | Browser test (Playwright, using your installed Chrome). Needs the backend, database and Ollama (both models) running. Each run signs up a new test account, uploads a file and asks one question. |

## Where things are

| Path | Contents |
|---|---|
| `src/api/` | `types.ts` (copies of the backend's response records; keep them in sync), `client.ts` (fetch wrapper and error messages), `endpoints.ts` (one function per endpoint) |
| `src/auth/` | The login session (kept in `localStorage`), `AuthProvider`, `RequireAuth` |
| `src/pages/` | Log in / sign up, the workspace list, one workspace (documents), its chat, not found |
| `src/components/` | Header, server status, workspace tabs, upload area, document list, status badge, chat list, answer text with citations, source list |
| `src/hooks/` | `useDocuments` (re-checks every 2 s while a document is processing), `useWorkspace`, `useNow` |
| `src/lib/` | Upload rules (the same as the backend's), citation parsing, formatting |
| `e2e/` | The Playwright test and its sample file |

## Good to know

- The backend's error responses have no message yet, so the pages pick their wording from the HTTP status (for example 409 means "already registered").
- When the backend restarts with a new `JWT_SECRET`, old tokens stop working, so you'll be logged out with "Your session has ended".
- A document that stays Queued or Processing for more than 2 minutes was probably cut off by a backend restart; nothing retries it. Upload it again.
- Answers come from a small local model, so they take a few seconds to half a minute, and "Thinking… n s" shows the wait. If Ollama isn't running (or the model isn't pulled), you'll see "The language model isn't available" and your question stays in the box.
