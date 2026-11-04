# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

"Second Brain": a Spring Boot REST backend for a workspace-based RAG (retrieval-augmented generation) app. Users register, create workspaces, and upload documents (PDF / plain text / Markdown). Uploaded documents are text-extracted, chunked, embedded with a local Ollama model, and stored in Postgres with pgvector. Users then ask questions in a workspace chat. The answers come from a local LLM that sees only the most similar chunks, and they cite those chunks (see "Chat" below). A React frontend in `frontend/` covers all of this (see "Frontend" below). Phases 1–5 of the original plan are done; phase 6 (hardening) is next.

## Stack

- Java 21, Spring Boot **4.1.1** (uses the Boot 4 split starters, e.g. `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`)
- Spring AI 2.0.1:
  - `spring-ai-starter-model-ollama` provides embeddings (`nomic-embed-text`) and chat (`llama3.2:3b`), through `ChatClient`.
  - `spring-ai-starter-vector-store-pgvector` and `spring-ai-vector-store-advisor` are on the classpath but unused: the app writes and searches vectors through its own JPA entity, not the `VectorStore` API.
  - In 2.0 the Ollama properties are `spring.ai.ollama.chat.model` / `...embedding.model`; the old `...options.model` names are deprecated.
- PostgreSQL 17 + pgvector (Docker), Flyway migrations, Hibernate `ddl-auto=validate`
- `hibernate-vector` maps `DocumentChunk.embedding` (`@JdbcTypeCode(SqlTypes.VECTOR) @Array(length = 768)`), so HQL can use `cosine_distance()` and similar functions. Without it, a `float[]` is sent as `real[]`: inserts work only through a pgvector cast, and reads and similarity queries fail.
- Spring Security as an OAuth2 resource server with self-issued HS256 JWTs (Nimbus)
- Apache PDFBox 2.0.27 for PDF text extraction

## Running locally

```bash
docker compose up -d                 # pgvector/pgvector:pg17 on localhost:5433 (db/user: secondbrain, pw: dev_password)
ollama pull nomic-embed-text         # Ollama must be running on localhost:11434
ollama pull llama3.2:3b              # chat model (~2 GB); the dev Mac has 8 GB RAM, so stay at ~3B
export JWT_SECRET=$(openssl rand -base64 32)   # REQUIRED: Base64, must decode to >= 32 bytes
./mvnw spring-boot:run
```

Other commands:

```bash
./mvnw compile
./mvnw test                          # everything; SecondBrainApplicationTests needs DB, Ollama and JWT_SECRET
./mvnw test -Dtest='TextChunkerTest,TextExtractionServiceTest,LocalFileStorageServiceTest,IngestionServiceTest'  # unit tests, no DB/Ollama
./mvnw test -Dtest=DocumentChunkRepositoryTest   # needs the Docker DB; each test is rolled back
./mvnw test -Dtest=SecurityErrorResponsesTest    # real server on a random port; needs the Docker DB (writes nothing), not JWT_SECRET
./mvnw test -Dtest='PromptBuilderTest,LlmServiceTest,RetrievalServiceTest,ChatServiceTest'   # chat unit tests, no DB/Ollama
./mvnw test -Dtest='ChatRepositoryTest,ChatControllerTest'   # need the Docker DB (rolled back); Ollama calls are mocked
./mvnw test -Dtest=ClassName#method  # single test
```

Env vars and their defaults (in `src/main/resources/application.properties`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (no default; startup fails without it), `JWT_EXPIRATION_SECONDS` (3600), `STORAGE_ROOT` (`./storage`), `CHAT_MODEL` (`llama3.2:3b`). The Ollama URL and embedding model are hard-coded in the properties file. Uploads are limited to 20MB.

## Frontend (`frontend/`)

React 19 + TypeScript 7, built with Vite 8. Also React Router 8 (import everything from `react-router`; there is no `react-router-dom` in v8), Vitest 5 with Testing Library, Playwright, and Oxlint. `frontend/README.md` has the details.

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173; proxies /api and /actuator to BACKEND_URL (default http://localhost:8080)
npm run lint       # oxlint
npm run build      # tsc -b (type-checks src, tests and e2e) + vite build
npm test           # Vitest unit tests, no backend needed
BACKEND_URL=http://localhost:8089 npm run test:e2e   # Playwright in the installed Chrome; needs backend + DB + Ollama (both models); signs up a new account per run
```

- The dev-server proxy is why the backend has no CORS configuration. When the backend is down, the proxy answers 502 with an empty body. `api/client.ts` treats any 5xx without a JSON body as "can't reach the server".
- `src/api/types.ts` copies the Java response records (`AuthResponse`, `WorkspaceResponse`, `DocumentResponse`). Update it when a DTO changes. `src/lib/uploadRules.ts` mirrors `DocumentService.isSupportedContentType` and the 20MB multipart limit. Change both sides together.
- Error messages come from the HTTP status, because the backend's error JSON has no message (see Known issues). Pass a per-status map to `describeError`.
- The JWT session lives in `localStorage` (`auth/session.ts`). Any 401 on a request that carries a token logs the user out. Never log the token, and never render user content as HTML.
- tsconfig sets `erasableSyntaxOnly` (no enums or constructor parameter properties) and `verbatimModuleSyntax` (use `import type`). Imports include the `.ts`/`.tsx` extension, as the Vite template does.
- Oxlint's React rules reject `Date.now()` during render (use the `useNow` hook) and reassigning variables inside async functions. Polling loops use `while` with `sleep(ms, signal)` from `lib/sleep.ts`, and an `AbortController` stops them on unmount.
- Unit tests stub `fetch` with `vi.stubGlobal` (see `src/test/setup.ts`). In the jsdom environment a `DOMException` is not `instanceof Error`.
- **Chat screen** (`pages/ChatPage.tsx`, routes `/workspaces/:id/chat[/:sessionId]`):
  - Each chat is a `Conversation` component keyed by its id, which loads its messages on mount. When a new chat gets its address, its first messages travel in the router's location state, so they show at once.
  - `AnswerText` turns `[n]` into links to the sources (`lib/citations.ts`) and renders everything as text, never HTML.
  - A long flex/grid item needs `min-width: 0` / `minmax(0, 1fr)` to shrink with an ellipsis. The source list was overflowing its bubble without it.

## Layout

Base package: `com.Second_Brain.Second_Brain` (the casing is unusual; keep it).

| Package | Contents |
|---|---|
| `auth/` | `AuthController` (`/api/auth/register`, `/api/auth/login`), `AuthService` (BCrypt, issues JWTs: `sub` = user UUID, claim `email`, issuer `second-brain`) |
| `User/` | `User` entity → `app_users` table |
| `workspace/` | Workspace entity (the class is named lowercase **`workspace`**), `WorkspaceMember` with composite `WorkspaceMemberId`, roles `OWNER`/`MEMBER`; endpoints `POST`/`GET /api/workspaces` |
| `Document/` | `Document` and `DocumentChunk` entities, `DocumentController`, `DocumentService` (upload/list/get with membership checks), `EmbeddingService` (wraps Spring AI `EmbeddingModel` and requires 768 dimensions), `EmbeddingTestController` (`GET /api/test/embedding`, public) |
| `Document/service/` | Ingestion pipeline: `IngestionService` (handles `DocumentUploadedEvent` after commit, `@Async`), `TextExtractionService`, `TextChunker` |
| `Document/storage/` | `FileStorageService` interface plus `LocalFileStorageService` (files go to `{root}/{workspaceId}/{documentId}/{filename}`, with path-traversal guards) |
| `chat/` | Phase 5 RAG chat: `ChatController` (`/api/workspaces/{id}/chat`), `ChatService`, `RetrievalService`, `LlmService` (wraps Spring AI `ChatClient`), `PromptBuilder`, entities `ChatSession` / `ChatMessage` (`sources` is JSONB), `ChatSource` |
| `common/config/` | `SecurityConfig` (stateless, CSRF off, all routes need auth except `/api/auth/**`, `/api/test/embedding`, actuator health and Swagger paths; error forwards to `/error` (`DispatcherType.ERROR`) are permitted so errors keep their real status), `JwtConfig` (encoder/decoder/BCrypt beans) |
| `config/` | `AsyncConfig` (`@EnableAsync`, default executor) |
| `frontend/` (repo root) | The React app; see "Frontend" above |

## Conventions

- Controllers read the caller's identity with `@AuthenticationPrincipal Jwt jwt` and `UUID.fromString(jwt.getSubject())`.
- Request DTOs are nested records in a `*Requests` holder class (`AuthRequests.RegisterRequest`), validated with `@Valid` and Jakarta constraints. Response DTOs are records with a static `from(entity)` factory.
- Errors are thrown as `ResponseStatusException`. There is no global exception handler.
- Services use constructor injection and `@Transactional` (with `readOnly = true` on reads). Entities use protected no-arg constructors and `@PrePersist` to set `createdAt`.
- Every workspace-scoped operation must check membership via `workspaceMemberRepository.existsById(new WorkspaceMemberId(workspaceId, userId))` and return 403 if the caller is not a member.
- Log with SLF4J (`LoggerFactory.getLogger`), not `System.err`. Never log document contents, passwords, or tokens.
- Unit tests build the class under test directly, with no Spring context. They use AssertJ, `@TempDir` for files, and `MockMultipartFile` for uploads.
- **Schema changes go in a new Flyway migration** (`src/main/resources/db/migration/V{n}__desc.sql`). Never edit an existing migration. Because Hibernate only validates the schema, entity mappings must match the migrations exactly.

## Data model (migrations V1–V3)

`app_users`, `workspaces` (owner_id), `workspace_members` (PK workspace_id+user_id, role), `documents` (status `UPLOADED|PROCESSING|READY|FAILED`, storage_path), `document_chunks` (document_id, chunk_index unique per doc, content, `embedding vector(768)`), `chat_sessions` (workspace_id, user_id; no title), `chat_messages` (role `USER|ASSISTANT`, `sources jsonb` mapped with `@JdbcTypeCode(SqlTypes.JSON)`; Hibernate uses Jackson 3, the only Jackson on the classpath). Postgres creates the UUIDs with `gen_random_uuid()`. The exception is `Document`, whose id is assigned in Java before the file is stored.

## Ingestion flow

`POST /api/workspaces/{id}/documents` (multipart field `file`). `DocumentService.upload` validates the file, checks membership, stores the file, persists the `Document` as `UPLOADED`, and publishes a `DocumentUploadedEvent`. `IngestionService.onDocumentUploaded` (`@Async @TransactionalEventListener`) receives it only after the upload transaction commits. `processDocument` then runs these steps, using `TransactionOperations` for the short transactions:
1. In a short transaction, set the status to `PROCESSING`. Clients can see this status.
2. With no transaction open, extract the text, split it into chunks, and embed every chunk. Chunks use `document.ingestion.chunk-size`=1000 characters and `chunk-overlap`=200. They end on whitespace, and only words longer than a chunk get cut.
3. In a short transaction, delete the old chunks, save all the new ones, and set `READY`. This step is all or nothing.

Any `Exception` or `Error`, or text that gives no chunks (such as a scanned PDF), logs the full stack trace. A separate transaction then deletes the chunks and sets `FAILED`. Don't hold a transaction open across extraction or embedding calls.

`TextExtractionService` decodes text files using the byte-order mark if there is one, then strict UTF-8, then Windows-1252. Files with NUL bytes are rejected as binary. NUL characters are removed from all extracted text, because Postgres TEXT rejects them. PDFs are limited to 64 MB of PDFBox memory and 200,000 characters per page, so a small "PDF bomb" fails as `FAILED` instead of exhausting the heap. Keep these limits.

## Chat (phase 5)

`POST /api/workspaces/{id}/chat` takes `{ "sessionId"?, "message" }`. Leaving out `sessionId` starts a new chat. The response is `{ sessionId, question, answer }`; `answer.sources` is a list of `{ number, documentId, filename, chunkIndex, excerpt }`, and the answer text cites them as `[n]`. Two read endpoints: `GET .../chat/sessions` lists your chats, newest first, titled by their first question (the schema has no title column), and `GET .../chat/sessions/{sessionId}/messages` returns one chat's messages. Chats are private to their user, even in a shared workspace. Someone else's chat returns 404, not 403.

`ChatService.ask` follows the same shape as ingestion, and is deliberately not `@Transactional`:
1. In a short transaction: check the workspace exists (404) and the caller is a member (403); load and check the session (404); read the last `app.chat.history-messages` (6) messages.
2. With no transaction open:
   - Retrieval (`RetrievalService`): embed the search text, then call `DocumentChunkRepository.findClosestInWorkspace`. That is HQL `cosine_distance`, limited to READY documents of that workspace, with no vector index. Keep the top `app.chat.top-k` (5) chunks that are within `app.chat.max-distance` (0.5) and within `app.chat.max-distance-from-best` (0.1) of the best match.
   - The search text is the message plus the previous question, so follow-ups still find the passages.
   - `PromptBuilder` numbers the passages. `LlmService` sends system + history + user as `Message` objects; not `ChatClient.user(String)`, which may treat `{braces}` in documents as template placeholders.
3. In a short transaction: create the session if it is new, then save the question and the answer together.

Behavior details:
- **Fixed replies, no model call:** the workspace has no READY documents, or no passage is close enough ("I couldn't find anything about that…").
- **Failures:** any failure in embedding or the model returns **503** and saves nothing. The cause is logged, but never the question text.
- **Timestamps:** messages carry microsecond timestamps; the answer is always after the question.

Notes:
- **Thresholds:** measured with nomic-embed-text and no task prefixes. Related questions had a best distance of 0.26–0.46; unrelated ones 0.53+. The margin is small, so re-measure if documents, the embedding model or prefixes change.
- **Speed and citations:** on the 8 GB M1, answers take ~5–40 s depending on memory pressure (Ollama keeps both models loaded). The 3B model sometimes leaves out `[n]` markers or lists the references at the end; `sources` is always complete.
- **Settings:** `spring.ai.ollama.chat.num-ctx=4096`, because Ollama silently drops the start of longer prompts. Also `num-predict=512`, `temperature=0.2`, and `spring.http.clients.read-timeout=180s` (Boot's RestClient has no read timeout by default).
- **Testing:**
  - `LlmServiceTest` uses the real `ChatClient` over a mocked `ChatModel`. Stub `chatModel.getOptions()`: ChatClient 2.0 calls `getOptions().mutate()`, and a mock returns null.
  - `ChatControllerTest` uses `@MockitoBean` for `EmbeddingService` and `LlmService`, and `jwt()` from spring-security-test.
- **Not built yet:** streaming answers, rewriting follow-up questions with the LLM, renaming or deleting chats.

## Known issues / gotchas (as of this writing)

- Check the build with `./mvnw clean compile`. A plain `compile` has reported success from stale `target/classes` while the sources were broken.
- `DocumentChunkRepository.deleteByDocumentId` must stay a bulk `@Modifying @Query` delete. A derived `deleteBy...` method removes rows one entity at a time, and Hibernate runs those DELETEs after pending INSERTs at flush, so re-ingesting a document would violate `uq_document_chunk_index`.
- `documents.storage_path` is relative to the storage root. Turn it into a file with `FileStorageService.resolve()`, never `Path.of()`. The default root `./storage` is resolved against the working directory at startup, so run the app from the repo root or set `STORAGE_ROOT` to an absolute path.
- Nothing retries ingestion. A document that is `UPLOADED` or `PROCESSING` when the app stops stays in that state.
- Keep `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` in `SecurityConfig`. Spring renders errors by forwarding to `/error`, and without that rule the forward needs a token, so every error on a public endpoint (409 duplicate email, 400 validation, 405) came back as 401. `SecurityErrorResponsesTest` guards this. MockMvc doesn't perform the forward, so such tests need a real server (`RANDOM_PORT`).
- Error responses are Spring Boot's default JSON (`timestamp`, `status`, `error`, `path`), without the `ResponseStatusException` reason. Clients must go by the status code.
- `DocumentService.isSupportedContentType` (and extraction) compare content types exactly, so `text/plain; charset=utf-8` is rejected with 400.
- Migration V2 added a `documents.filename` column, but the entity's `filename` field maps to `original_filename`, so the `filename` column is never written.
- `logging.level.org.springframework.security=TRACE` is on, so logs are noisy.
- `springdoc.swagger-ui.path` is configured, but springdoc is not a dependency.
- The `postman/` directory holds Postman workspace scaffolding. It currently contains no collections.
