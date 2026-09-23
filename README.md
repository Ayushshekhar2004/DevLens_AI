# DevLens AI

DevLens AI is a portfolio project for reviewing submitted source code and suggesting test cases. It provides a React interface, a Spring Boot API, PostgreSQL persistence, JWT authentication, and a replaceable AI provider. It **does not execute submitted code**.

## Problem and approach

Code reviews often need a first pass for possible bugs, edge cases, complexity, and test ideas. DevLens gathers these suggestions in one structured review that can be saved, revisited, compared with the original code, and searched later. AI output is advisory: it can be incomplete or wrong and requires human verification.

## Working features

- Register, log in, and log out. Analyses, history, and analytics are scoped to the authenticated user.
- Submit Java, Python, JavaScript, or C++ source text. Blank submissions are rejected.
- Store a review summary, potential bugs, time/space complexity notes, edge cases, suggestions, and AI-suggested code.
- Store generated test-case suggestions with category, input, expected output or uncertainty warning, and explanation.
- Store advisory security findings with severity, location when identifiable, remediation, and uncertainty notes.
- Browse personal history with server-side search, language filter, newest/oldest sorting, pagination, detail view, and confirmed deletion.
- View metrics derived from stored records: total analyses, counts by language, recent analyses, generated test-case count, and security findings by severity.
- Compare original and suggested code, with copy buttons for both. The editor and results include loading, error, and empty states.
- Use a clearly labeled mock provider for local development, or configure an OpenAI-compatible chat-completions provider.

The mock provider returns placeholder feedback, **not a real code review**. It deliberately leaves inferred expected output blank and does not report security findings.

## Tech stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite 8, plain CSS |
| Backend | Java 21, Spring Boot 4, Maven, Spring Web, Validation, Security, Data JPA |
| Database | PostgreSQL |
| Authentication | BCrypt password hashing, signed JWT bearer tokens |
| AI integration | Replaceable provider interface; mock or OpenAI-compatible HTTP provider |
| Tests | JUnit/MockMvc/Mockito; Vitest and Testing Library |
| Containers | Docker Compose, Nginx, PostgreSQL named volume |

## System architecture

```text
Browser
  └─ React UI ── HTTP /api + Bearer JWT ──> Spring Boot API
                                          ├─ Auth / analysis / analytics services
                                          ├─ JPA repositories ──> PostgreSQL
                                          └─ CodeReviewService ──> AI provider interface
                                                                   ├─ mock
                                                                   └─ OpenAI-compatible API
```

In Docker, Nginx serves the built frontend and proxies `/api` to the `backend` service. The backend reaches PostgreSQL through the Compose service name `db`. In non-Docker development, Vite runs on port 5173 and calls the backend on port 8080 using `VITE_API_BASE_URL` and configured CORS.

## Backend architecture

The Java code separates `controller`, `service`, `repository`, `entity`, `dto`, `exception`, `config`, and `ai` packages. Controllers accept validated DTOs and return DTOs rather than JPA entities. Services own workflow and user-scoping rules. Repositories perform persistence and owner-scoped queries. A centralized exception handler formats validation, authentication-related, not-found, and provider-failure responses.

History search, filters, sorting, and pagination run in the backend database query. Analytics aggregates are derived from persisted rows rather than fabricated metrics. `GET /api/analyses` is still an unpaginated list; use `/history` for larger histories.

## AI workflow

1. `POST /api/analyses` validates the language and source text, then stores an analysis as `PENDING` for the authenticated user.
2. `CodeReviewService` calls the selected `AiCodeReviewProvider`; controllers do not call a provider directly.
3. The real provider sends one structured review request, asks for strict JSON, and validates the returned schema. The mock provider supplies clearly labeled placeholders without an external request.
4. The service persists the structured review and marks the analysis `COMPLETED`. On provider timeout, API failure, unavailability, or invalid response, it marks the saved record `FAILED` and returns an explanatory API error.
5. Later reads return the stored result or the stored failure reason. No submitted program is compiled or run.

`AI_PROVIDER=auto` uses the real provider only when `AI_API_KEY` is present; otherwise it uses the mock. Set `AI_PROVIDER=mock` explicitly for predictable local demonstrations.

## Authentication flow

Registration validates the request, normalizes the email, and persists a BCrypt hash—not the plaintext password. Login checks the hash and returns a signed JWT plus public user fields. The frontend holds the session in browser `sessionStorage` (cleared on tab close, logout, expiry, or a protected API `401`) and sends `Authorization: Bearer <token>` with protected requests. The server validates the token and loads the current user before analysis or analytics logic runs. An analysis ID belonging to another user returns `404`, avoiding disclosure of its existence.

## Database model

- `users`: ID, name, unique email, BCrypt password hash, creation time.
- `analyses`: ID, owning user, programming language, source code, status, creation time, scalar review fields, and optional failure reason.
- JPA element-collection tables hold ordered potential bugs, edge cases, suggestions, generated test cases, and security findings. Test cases and findings are embedded values, not independently managed resources.

Hibernate currently uses `ddl-auto=update` for local development. No migration framework or production migration plan is included.

## Important API endpoints

| Method | Path | Purpose | Auth |
| --- | --- | --- | --- |
| GET | `/api/health` | Backend status | No |
| POST | `/api/auth/register` | Create account (`201`) | No |
| POST | `/api/auth/login` | Get JWT (`200`) | No |
| POST | `/api/analyses` | Create and review analysis (`201` on success) | Yes |
| GET | `/api/analyses/{id}` | Read own analysis | Yes |
| GET | `/api/analyses` | List own analyses, newest first | Yes |
| GET | `/api/analyses/history` | Paginated, searchable history | Yes |
| DELETE | `/api/analyses/{id}` | Delete own analysis (`204`) | Yes |
| GET | `/api/analytics/overview` | Owner-scoped metrics | Yes |
| POST | `/api/repositories/imports` | Import a bounded ZIP snapshot (`multipart/form-data`, field `file`) | Yes |
| GET | `/api/repositories/snapshots/{id}` | Read own snapshot metadata and content hashes | Yes |

History supports `page` (zero-based), `size` (1–100, default 20), `search` (source code or result summary), `language` (`JAVA`, `PYTHON`, `JAVASCRIPT`, `CPP`), and `sort` (`newest` or `oldest`). Missing/invalid credentials return `401`; validation errors return `400`; missing or unowned analyses return `404`.

Example after logging in and copying the returned JWT:

```bash
curl -X POST http://localhost:8080/api/analyses \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"language":"JAVA","sourceCode":"public class Main {}"}'
```

## Local installation

### Docker Compose (recommended for a full-stack run)

Install Docker Desktop or a Docker Engine with Compose. From the repository root:

```bash
cp .env.example .env
# Edit .env: replace DB_PASSWORD and JWT_SECRET with private values.
docker compose up --build
```

Open `http://localhost:5173`; check `http://localhost:8080/api/health`. Use `docker compose logs -f` for logs and `docker compose down` to stop. PostgreSQL data stays in the named `postgres_data` volume. **`docker compose down --volumes` permanently removes that local database data.** If host ports 5432, 8080, or 5173 are occupied, change `POSTGRES_PORT`, `BACKEND_PORT`, or `FRONTEND_PORT` in `.env`; keep `FRONTEND_ORIGIN` aligned with the browser URL.

### Run without Docker

Prerequisites: Java 21 (the project's target version), Maven, Node.js compatible with Vite 8, and PostgreSQL. Create a database, for example `createdb devlens`. In one terminal:

```bash
cd backend
export DB_HOST=localhost DB_PORT=5432 DB_NAME=devlens
export DB_USERNAME=your_postgres_user DB_PASSWORD=your_local_password
export JWT_SECRET=replace_with_a_private_random_value_at_least_32_bytes_long
export AI_PROVIDER=mock FRONTEND_ORIGIN=http://localhost:5173
mvn spring-boot:run
```

In another terminal:

```bash
cd frontend
cp .env.example .env
npm ci
npm run dev
```

The frontend example sets `VITE_API_BASE_URL=http://localhost:8080`. The backend and frontend `.env.example` files are reference templates; exporting environment variables (or loading a private local file yourself) supplies the non-Docker backend configuration.

## Environment variables

Never commit populated `.env` files. The root `.env.example` is for Compose; `backend/.env.example` and `frontend/.env.example` document standalone development.

| Variable | Purpose |
| --- | --- |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL database and credentials; required for Compose |
| `DB_HOST`, `DB_PORT` | Backend database host/port; Compose sets these to `db:5432` |
| `JWT_SECRET` | Private signing secret, at least 32 bytes |
| `JWT_EXPIRATION_MINUTES` | Token lifetime; default 60 |
| `AI_PROVIDER` | `mock`, `auto`, `openai-compatible`, or explicit local-first `ollama` |
| `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL`, `AI_TIMEOUT_SECONDS` | Real provider credentials, endpoint, model, and timeout |
| `VITE_API_BASE_URL` | Browser API origin; `/` for Docker's same-origin Nginx proxy |
| `FRONTEND_ORIGIN` | Allowed browser origin for backend CORS |
| `SERVER_PORT`, `FRONTEND_PORT`, `BACKEND_PORT`, `POSTGRES_PORT` | Runtime/container host ports as applicable |
| `REPOSITORY_STORAGE_ROOT`, `REPOSITORY_ALLOWED_FOLDER_ROOT` | Private snapshot storage and trusted folder-import boundary |
| `REPOSITORY_MAX_*` | Upload, expansion, file, count, depth, ratio, and elapsed-time bounds |

`VITE_API_BASE_URL` is embedded in frontend assets at build time; do not put secrets in any `VITE_*` variable. A real provider is optional; a configured key can cause submitted source text to be sent to that external service.

### Opt-in local or trusted-LAN Ollama

Keep the default `AI_PROVIDER=mock` or `auto` for existing snippet behavior. To use an already installed Ollama model, set `AI_PROVIDER=ollama`, `OLLAMA_MODEL` to that model's exact installed name, and configure `OLLAMA_PROFILES` as comma-separated `id|display name|http://trusted-host:port` entries. Example placeholders are in the root and backend `.env.example` files. The host-run default is `local|This machine|http://localhost:11434`; Docker users must explicitly configure a reachable trusted host address such as `host.docker.internal`, or a private-LAN IPv4 address. No model is downloaded by DevLens. Use `ollama list` and `curl http://localhost:11434/api/tags` on the Ollama machine to verify its installed models and connectivity before starting DevLens. A profile test in DevLens lists models from the selected machine; choose one before submitting code. If that model disappears, the analysis fails with a clear selection error and no cloud fallback.

Only loopback, the explicit Docker host alias, or RFC1918 private IPv4 endpoints are accepted; URL redirects are not followed. Profile URLs come solely from server environment configuration, never normal analysis requests. LAN mode is intended only for trusted private networks. Do not expose Ollama publicly; DevLens does not change firewall or Ollama listener settings.

For a host-run backend, `http://localhost:11434` reaches Ollama on that same host. Inside the existing Docker Compose backend, `localhost` refers to the backend container, not the Mac host; configure `http://host.docker.internal:11434` explicitly for host Ollama or a trusted RFC1918 address such as `http://192.168.1.50:11434` for a private-LAN instance. The Ollama listener and firewall remain administrator-managed.

With DevLens, PostgreSQL, Ollama, and an installed model already running, execute the authenticated terminal smoke check from the repository root:

```bash
DEVLENS_SMOKE_PROFILE=local \
DEVLENS_SMOKE_MODEL=your-exact-installed-model \
./scripts/ollama-smoke.sh
```

`DEVLENS_SMOKE_API_URL` defaults to `http://localhost:8080`. The script uses a tiny synthetic Java sample, verifies unauthenticated denial, registers a disposable user, tests the selected profile, creates an analysis, verifies it in owner-scoped history, and deletes the analysis. It never pulls or updates a model. The disposable user remains in the local database.

### Bounded repository import

Authenticated users may upload a ZIP to `/api/repositories/imports`. DevLens creates an owner-scoped, immutable snapshot in private application storage and exposes only relative paths, SHA-256 hashes, sizes, and summary metadata. It does not execute repository files, install packages, invoke AI, clone Git repositories, or fetch remote content. The importer rejects unsafe paths, links and special entries, encryption/unsupported ZIP entries, collisions, nested archives, excessive expansion, and likely sensitive content. It default-denies generated/vendor/VCS folders, binaries, secrets, keys, certificates, dumps, and unknown file types; `.env.example`-style templates are sanitized before storage.

Trusted local-folder import has no public path endpoint. An administrator may run it only from the backend process by setting `REPOSITORY_IMPORT_CLI_ENABLED=true`, an existing owner email, and a source below `REPOSITORY_ALLOWED_FOLDER_ROOT`. For a host-run backend:

```bash
export REPOSITORY_ALLOWED_FOLDER_ROOT=/absolute/trusted/import-root
export REPOSITORY_IMPORT_CLI_ENABLED=true
export REPOSITORY_IMPORT_CLI_SOURCE=/absolute/trusted/import-root/project
export REPOSITORY_IMPORT_CLI_OWNER_EMAIL=existing-user@example.com
cd backend && mvn spring-boot:run
```

The source is read without following symlinks and is never modified. Disable the CLI flag after the one-shot import. In Docker, the allowed root must be explicitly mounted/configured by the administrator; DevLens does not add an arbitrary host-folder mount.

## Testing

```bash
cd backend && mvn test
cd ../frontend && npm ci && npm test && npm run build
```

Backend tests cover validation, authentication, ownership, controller responses, service transitions, analytics, and AI-provider failure handling. Frontend tests cover login/session behavior, code submission states, structured review and comparison rendering, analytics, and history interactions. These are automated checks, not a full browser accessibility or production deployment test. Use Java 21 for the documented Maven command; on this machine's Java 26 installation, Mockito needs an explicit Byte Buddy agent to run the backend tests.

## Security considerations

- User-submitted source is stored in PostgreSQL and, with a real provider, transmitted to that configured AI endpoint. Do not submit secrets or proprietary code without authorization.
- Passwords are BCrypt-hashed; JWT, database, and provider secrets come from environment variables. Tokens are stored in per-tab `sessionStorage`, which limits persistence but remains accessible to JavaScript if an XSS vulnerability exists.
- Owner-scoped queries limit access to saved analyses. The app does not execute submitted code.
- AI bug, complexity, test, and security suggestions are **advisory**. Generated expected outputs may be uncertain; security findings can be false positives or miss vulnerabilities. Use human review, tests, static analysis, and professional audit where appropriate.
- Docker configuration is intended for local use, not production hardening. Use HTTPS, managed secrets, database backups, and a proper migration strategy before any public deployment.

## Known limitations

- The mock provider returns placeholders, not code-specific AI insights. A real provider needs a valid API key and compatible endpoint; responses can still be wrong or fail.
- Analysis creation calls the provider synchronously, so the request can wait until completion or timeout. There is no background job queue or live progress stream.
- There is no submitted-code execution, generated-test execution, static-analysis engine, or malware analysis.
- `GET /api/analyses` is unpaginated; the History endpoint is paginated.
- Authentication has no refresh-token, password-reset, email-verification, or account-recovery flow.
- Local schema evolution relies on Hibernate `update`; production migrations, deployment hardening, and full end-to-end browser coverage are not implemented.

## Future roadmap

Potential next steps—not current features—include database migrations, asynchronous review jobs, stronger automated browser/accessibility testing, account recovery, and deployment hardening. Any code-execution or test-runner capability would require a separate sandbox design and security review.
