# DevLens implementation evidence

## v1.1 Step 01 — local/trusted-LAN Ollama adapter

### Baseline inspected

- v1 has `AiCodeReviewProvider` and `CodeReviewService`, OpenAI-compatible and mock providers, `CodeReviewResult` with review, tests, and security findings, JWT-authenticated analysis routes, and PostgreSQL `Analysis` persistence with a stored result. Existing `AI_PROVIDER=auto` uses mock without a cloud key; this default is unchanged.
- The backend uses JDK `HttpClient`, Jackson, Maven and Spring Boot; the frontend uses React/TypeScript/Vite and bearer-token API services. No migration framework exists; JPA uses local `ddl-auto=update`. No schema or migration change was needed.
- The checkout already had an uncommitted History null-filter fix in `AnalysisService.java` and `AnalysisServiceTest.java`; it was preserved.
- No repository `AGENTS.md` was present.

### Changes and purpose

| Files | Purpose |
| --- | --- |
| `backend/.../ai/OllamaConnections.java`, `OllamaAiProvider.java` | Validate administrator-configured loopback/private-LAN profiles; use one bounded, no-redirect HTTP adapter for connection tests and review. |
| `backend/.../ai/AiCodeReviewProvider.java`, `service/CodeReviewService.java`, `DefaultCodeReviewService.java` | Add optional profile/model selection while retaining existing two-argument review methods and provider abstraction. |
| `backend/.../config/AiProviderConfig.java`, `application.properties`, `backend/.env.example`, root `.env.example`, `docker-compose.yml` | Opt-in provider wiring and environment-backed limits/profiles; preserve existing provider defaults. |
| `backend/.../controller/OllamaConnectionController.java`, `AnalysisController.java`, `config/SecurityConfig.java` | Authenticated profile discovery and connection test; optional profile/model query parameters without changing analysis request or response JSON. |
| `backend/.../service/AnalysisService.java`, `exception/OllamaSelectionException.java`, `GlobalExceptionHandler.java` | Carry selection to the provider and map missing/invalid models to safe errors. The pre-existing History fix is unchanged. |
| `backend/src/test/.../ai/OllamaAiProviderTest.java`, `config/SecurityConfigTest.java` | Synthetic localhost stub tests for success, configuration rejection, missing model, unavailable service, redirect refusal, timeout, malformed JSON, and oversized output; verify new profile routes require a token. |
| `frontend/src/services/ollamaApi.ts`, `analysisApi.ts`, `components/AnalysisForm.tsx`, `AnalysisForm.test.tsx` | Load authenticated server profiles, block submission until discovery completes, test one, select its installed model, and submit the selection; cover interaction without a browser. |
| `README.md`, this file | Terminal setup and evidence. |

### Configuration and security

- `AI_PROVIDER=ollama` explicitly enables this adapter; `auto` and `mock` remain unchanged. `OLLAMA_PROFILES` uses `id|display name|URL` entries; `OLLAMA_DEFAULT_PROFILE` and `OLLAMA_MODEL` select a configured profile and exact installed model. `OLLAMA_CONNECT_TIMEOUT_SECONDS`, `OLLAMA_READ_TIMEOUT_SECONDS`, `OLLAMA_OUTPUT_LIMIT`, and `OLLAMA_CONTEXT_BUDGET` are bounded at startup. Existing `AI_API_KEY`/cloud settings are not used by Ollama.
- Backend accepts only configured profile IDs from requests, not URLs. URLs require HTTP, explicit port, no credentials/path/query/fragment, and loopback, `host.docker.internal`, or RFC1918 private IPv4. Redirects are not followed. DNS names other than the explicit Docker alias and IPv6 LAN addresses are intentionally unsupported to reduce SSRF/rebinding risk. Administrators must keep the LAN private and trusted.
- `/api/ai/ollama/profiles` and `POST /api/ai/ollama/profiles/{id}/test` require JWT. Test reads bounded `/api/tags` data and returns only model names. Each review rechecks that the selected model is installed on that profile; it never downloads a model or falls back to a cloud provider. The JSON result is shape-checked and size-bounded before persistence. Provider errors do not include source or raw Ollama response bodies.
- Repository import and repository workflows are not in Step 01. No imported code or repository-supplied tools were executed.

### Checks

- `mvn -q -DskipTests compile`: passed.
- `mvn -q test -DargLine=-javaagent:/Users/ayushshekharsingh/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.11/byte-buddy-agent-1.18.11.jar`: 62 tests passed, including the existing OpenAI provider and API/controller tests, six new Ollama adapter tests, and a new auth boundary test. The Java 26 host needs the explicit local Mockito/Byte Buddy agent. The suite uses localhost synthetic stubs and required sandbox localhost socket permission.
- `npm test`: 17 tests passed (one new Ollama form interaction test). `npm run build`: passed.
- `git diff --check`: passed.
- `docker compose config --quiet`: passed; container rebuild and live Ollama integration were not performed.
- The final backend test log was searched for synthetic source and response markers; none appeared. This is a targeted leak check, not a proof for every runtime path.
- Initial runs exposed three mocked-controller compatibility failures, three frontend tests affected by profile discovery, and a later readiness-race test failure; these were fixed, and the final suites passed.
- No live Ollama service/model, LAN transfer, visual browser UI, or production deployment was verified. The adapter tests prove stub behavior, not live model quality or reachability. Visual UI checks are deferred under the working contract.

### Risks and limits

- Ollama can return syntactically valid but poor-quality or misleading review content; security findings remain advisory. No arbitrary submitted code is executed.
- A host Ollama listener may not be reachable from Docker's `host.docker.internal` unless the local Ollama setup permits that connection; configure a trusted reachable address yourself. DevLens does not change listener or firewall settings.
- Successful model listing does not guarantee sufficient memory/context for review. Output and context budgets are bounded, but model capability varies.
- No database migration was required. No commit, push, deployment, or model installation was performed.

## v1.1 Step 02 — authenticated Ollama compatibility verification

### Scope completed

- The existing authenticated `POST /api/analyses` path forwards only the selected server-managed profile ID and installed model name. The public analysis request body and stored/result JSON shape remain unchanged; profile URLs are never returned.
- Service tests exercise selected-Ollama completion through the same `Analysis` persistence mapping and owner-scoped history conversion. A failed selected-local request is saved as `FAILED` and the default provider method is never invoked. Configuration tests also verify that explicit `AI_PROVIDER=ollama` selects `OllamaAiProvider` even when cloud credentials exist.
- Controller tests verify the optional profile/model query parameters reach the analysis service without adding provider metadata to the response. Existing authentication tests cover analysis/history denial and both Ollama metadata endpoints return `401` without a token.
- Frontend recovery text now distinguishes missing model/selection (`400`), invalid model output (`502`), offline/overloaded service (`503`), and timeout (`504`). It tells the user to retest the connection, check the trusted machine, choose an installed model, reduce the sample, or try another installed model as appropriate. Existing structured-result rendering is unchanged.
- `scripts/ollama-smoke.sh` is a terminal-only end-to-end check using a tiny synthetic Java method. It verifies health, unauthenticated history denial, registration/login, safe profile metadata, installed-model discovery, analysis creation, completed result, persisted owner history, and analysis cleanup. It uses temporary response files and does not print the JWT, password, source, or raw provider response.

### Host and Compose settings

- Host-run backend: configure a profile such as `local|This machine|http://localhost:11434`; loopback refers to the host process.
- Existing Docker Compose backend: container loopback refers to the backend container. Use the explicitly allowed `host.docker.internal:11434` for host Ollama on Mac, or an administrator-configured RFC1918 IPv4 endpoint for a trusted LAN machine. No public address, firewall change, new container, remote fallback, or automatic model pull was added.
- Required smoke inputs are `DEVLENS_SMOKE_MODEL` (exact installed name) and optionally `DEVLENS_SMOKE_PROFILE` and `DEVLENS_SMOKE_API_URL`. These are metadata/configuration, not secrets.

### Acceptance evidence

- `mvn -q test -DargLine=-javaagent:/Users/ayushshekharsingh/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.11/byte-buddy-agent-1.18.11.jar`: **66 tests passed**. Coverage includes authenticated routing/history, unauthorized denial, unknown/missing model, offline/overload, timeout, invalid and oversized output, redirect refusal, and explicit no-cloud-provider selection/fallback checks. Localhost socket permission was required only for synthetic DevLens HTTP stubs.
- `npm test`: **22 tests passed** across 6 files, including the Ollama selection flow and actionable `400`, `502`, `503`, and `504` states.
- `npm run build`: passed; 41 modules transformed.
- `mvn -q -DskipTests test-compile`, `bash -n scripts/ollama-smoke.sh`, and `git diff --check`: passed.
- Live probes on 2026-09-22: `http://127.0.0.1:11434/api/tags` and `http://127.0.0.1:8080/api/health` both returned connection failure (`HTTP 000`). The private root environment selected a non-Ollama provider and had no `OLLAMA_MODEL`; values and secrets were not printed. Running `./scripts/ollama-smoke.sh` stopped safely with the required missing-model message.
- No latency was measured because no live model request ran. No browser, screenshot, screen recording, desktop automation, cloud provider, model installation, or model download was used.

### Verification status and gap

- Stub-based Step 02 acceptance checks are complete and green.
- **v1.1 is not marked verified** because the required real local smoke check could not run: no Ollama service/model was reachable or configured, and DevLens/PostgreSQL were not running. The smallest remaining input is a running trusted Ollama endpoint with one installed model name, plus the normal running DevLens/PostgreSQL stack. Then run the documented smoke command and record its real result and sample-specific latency.

## v1.1 Step 03 — bounded local repository import

### Implementation and data model

- `RepositoryImportController` adds only authenticated ZIP upload and owner-scoped snapshot metadata lookup. There is no HTTP endpoint accepting a filesystem path, Git URL, or remote repository location.
- `RepositoryFolderImportCli` is disabled by default and runs only when an administrator explicitly configures the CLI flag, existing owner email, source folder, and allowed root. `RepositoryImportService` resolves the allowed root/source, rejects root escapes and symlinks, does not follow links, compares size and modification time around reads, and never mutates the source.
- `RepositorySnapshot` and `RepositorySnapshotFile` persist owner, private storage key, display name, ordered relative paths, sanitized/stored byte sizes, SHA-256 content hashes, and creation time. The public DTO omits storage paths/keys. JPA local `ddl-auto=update` creates the snapshot tables; this repository still has no migration framework, so no migration file was added.
- Snapshot files and a manifest are first written under a unique staging directory, then moved to the completed private directory. Stored files are made read-only and no mutation API exists. Failed, interrupted, runtime-failed, and transaction-rolled-back imports remove staging/completed content. Docker Compose uses a dedicated `repository_data` volume mounted outside served/static content.

### ZIP validation, limits, and content policy

- ZIP processing is sequential and bounded with Apache Commons Compress because Java's basic ZIP API does not reliably expose link/encryption metadata. Before writing, it rejects absolute, traversal, Windows drive/UNC/backslash, NUL, overly deep, duplicate, Unicode-normalized case-colliding, symbolic/ASi-linked (hard-link metadata), special, encrypted, and unsupported entries. Canonical destination checks prevent escape. Archive suffixes inside a ZIP are rejected instead of recursively unpacked.
- Both reported upload size and bytes actually consumed are bounded. Expanded bytes, individual bytes, encountered file count (including filtered files), per-entry compression ratio, path depth, and elapsed time are checked while streaming. Folder imports apply expanded/file/count/depth/time limits and stable-read checks.
- `RepositoryContentPolicy` default-denies VCS internals, dependency/vendor environments, build outputs, IDE/cache folders, binaries, archives, `.env` variants, credential/secret names, keys/certificates, database/dump/backup files, and unknown extensions. Supported source/text/manifest files are allowlisted. `.env.example`/sample/template variants are transformed before storage by replacing assignment values with a redacted template marker. Private-key headers and likely AWS access-key material cause rejection.
- Filter decisions occur before content is stored or indexed. Filtered ZIP entries are streamed only to advance and enforce safety limits; their bytes are not buffered. No imported executable or package hook is invoked.

### Isolation and prohibited behavior evidence

- Owner access uses `findByIdAndUserId`; another owner's lookup returns the same not-found outcome. Repository routes require JWT.
- Import production classes have no AI-provider/service dependency, HTTP client, Git client, process execution, shell, package manager, or dynamic class-loading path. A synthetic fixture contains malicious `package.json` install metadata and an `install.sh`; the manifest is stored as inert text, the script is filtered, and its marker is never created. No network fetch, AI request, imported test/build script, or package installation occurred during checks.
- Original synthetic folder fixtures remained unchanged. Tests and snapshots used only temporary DevLens-created files; no external repository was imported or executed.

### Configuration

- `REPOSITORY_STORAGE_ROOT`, `REPOSITORY_ALLOWED_FOLDER_ROOT`, `REPOSITORY_MAX_UPLOAD_BYTES`, `REPOSITORY_MAX_EXPANDED_BYTES`, `REPOSITORY_MAX_FILE_BYTES`, `REPOSITORY_MAX_FILES`, `REPOSITORY_MAX_PATH_DEPTH`, `REPOSITORY_MAX_DECOMPRESSION_RATIO`, and `REPOSITORY_MAX_ELAPSED_SECONDS` are environment-backed.
- `REPOSITORY_IMPORT_CLI_ENABLED=false` is the default. `REPOSITORY_IMPORT_CLI_SOURCE` and `REPOSITORY_IMPORT_CLI_OWNER_EMAIL` are used only when explicitly enabled. Example values contain no secrets.

### Acceptance checks

- `mvn -q -Dtest=RepositoryImportServiceTest,RepositoryImportControllerTest,SecurityConfigTest test -DargLine=-javaagent:/Users/ayushshekharsingh/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.11/byte-buddy-agent-1.18.11.jar`: passed focused importer, API, and authorization tests.
- `mvn -q test -DargLine=-javaagent:/Users/ayushshekharsingh/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.11/byte-buddy-agent-1.18.11.jar`: **80 backend tests passed**. The localhost permission is required only by pre-existing synthetic AI HTTP-stub tests; repository-import tests themselves perform no network calls.
- Synthetic importer fixtures passed for valid ZIP, valid trusted folder, ZIP traversal/absolute/drive/UNC paths, ZIP symlink metadata, folder symlink and allowed-root escape, case collision, streamed upload overflow, expanded/file overflow, compression ratio, excessive file count, nested archive, interrupted-stream cleanup, sensitive filtering/template sanitization, inert package hooks/scripts, and cross-owner denial.
- `npm test`: **22 frontend tests passed**. `npm run build`: passed with 41 modules transformed. Step 03 intentionally adds no repository-import frontend UI.
- `docker compose config --quiet`, `mvn -q -DskipTests compile`, `mvn -q -DskipTests test-compile`, and `git diff --check`: passed.
- Static inspection using `rg` found only deny/allowlist references to package/build tools; no AI, HTTP/network client, process execution, Git clone, or package-install invocation exists in importer production classes.
- No browser automation or visual UI check was used or needed. No live PostgreSQL/container import was run; database behavior is covered through repository/service/controller tests, while actual schema creation remains dependent on the existing local `ddl-auto=update` convention.

### Limitations and risks

- Archive formats other than ZIP are not accepted. ZIP filenames that use backslashes are rejected rather than normalized.
- Content screening is conservative and cannot guarantee discovery of every secret; users must still avoid uploading sensitive repositories. Sanitized templates deliberately have hashes for sanitized stored bytes, not the discarded original values.
- Filesystem read-only flags are defense-in-depth, not protection from an operating-system administrator. Snapshot immutability is primarily enforced by private storage, atomic publication, hashes, and absence of update endpoints.
- Local folder import is intentionally operationally minimal: it runs during backend startup when explicitly enabled and should be disabled after the one-shot import. Docker administrators must explicitly mount an allowed source root if they choose to use it.
