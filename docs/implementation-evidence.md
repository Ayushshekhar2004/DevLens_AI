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

## v1.2 Step 04 — deterministic repository metadata scanner

### Scope and changed files

- `RepositoryScanService` performs the bounded, deterministic scan over immutable snapshot content. It verifies stored hashes before parsing, applies hard exclusions before ignore rules, parses bounded ignore/manifests as untrusted data, detects modules/stacks, records unsupported/partial outcomes, extracts heuristic symbols/imports, resolves only local lexical imports, and redacts likely secrets without logging matched values. It has no AI, HTTP/network, process, shell, Git, compiler, or package-manager dependency.
- `RepositoryScanProperties`, `application.properties`, both `.env.example` files, and `docker-compose.yml` add validated byte and per-file extraction bounds: `REPOSITORY_SCAN_MAX_TEXT_BYTES`, `REPOSITORY_SCAN_MAX_MANIFEST_BYTES`, `REPOSITORY_SCAN_MAX_SYMBOLS_PER_FILE`, and `REPOSITORY_SCAN_MAX_IMPORTS_PER_FILE`.
- `RepositoryScan`, the six `Repository*Record` embeddables, and `RepositoryScanRepository` persist the owner/snapshot association, parser version/status, stack, modules, file path/language/hash/line/context metadata, symbols, imports, dependency edges, and safe skip path/reason metadata. No excluded file contents are placed in skip metadata.
- `RepositoryScanResponse`, `RepositoryImportController`, `RepositoryScanException`, and `GlobalExceptionHandler` add authenticated owner-scoped scan creation/read routes and safe error mapping. Public responses omit private storage locations and redacted stored context.
- `RepositoryContentPolicy` now permits `.devlensignore` to reach the private snapshot as a bounded control file. Scanner hard exclusions still cannot be negated.
- `pom.xml` adds Flyway/PostgreSQL migration support plus test-only JPA/H2 support. `V1__add_repository_scan_metadata.sql` additively creates only scanner-owned tables and indexes. Existing non-empty installations are baselined at version 0; Hibernate continues its existing `update` behavior for older v1 tables and supplies mapped foreign keys after migration.
- `RepositoryScanServiceTest`, `RepositoryScanMigrationTest`, `RepositoryScanPersistenceTest`, `RepositoryImportControllerTest`, and `SecurityConfigTest` cover scanner fixtures, migration execution, JPA persistence/ownership, API mapping, and unauthenticated denial. `README.md` documents the endpoints, ignore policy, migration boundary, configuration, and limitations.

### Deterministic scanning and safety behavior

- Mandatory exclusions cover VCS/dependency/vendor/build/cache directories, generated/minified/source-map content, lock files, archives, unsanitized `.env` variants, credential/key/certificate stores, database/dump/backup files, and other sensitive filename patterns. These decisions occur before language parsing or safe-context persistence.
- Nested `.gitignore` and `.devlensignore` files are evaluated in deterministic parent-to-child order. At the same directory `.gitignore` is applied first and `.devlensignore` second. Negation can restore an ordinarily ignored path but never overrides mandatory exclusions. Ignore files remain inert data and are recorded only with the safe `IGNORE_CONTROL_FILE` reason.
- Java and JavaScript/TypeScript including JSX/TSX receive bounded lexical symbol/import extraction labeled `HEURISTIC`. Local relative imports are resolved only against snapshot paths; aliases, dynamic imports, missing local targets, Java external packages, and package dependencies are explicitly unresolved. Versions and runtime/call-graph behavior are not inferred.
- `pom.xml`, Gradle, and `package.json` plus conservative directory roots identify modules without executing build tooling. Package dependency names and Maven group/artifact identifiers are retained without resolving or transmitting them. Malformed manifests produce `PARTIAL`; unknown extensions produce `UNSUPPORTED`; safe SQL/config files receive metadata-only parsing.
- Eligible UTF-8 text is bounded and secret-screened before persistence. Assignment-like credentials, access-key identifiers, and JWT-shaped values are redacted; private-key blocks are excluded when safe redaction is uncertain. Filtering reduces accidental exposure risk but cannot guarantee discovery of every secret.
- A unique scan per snapshot makes repeated requests return identical persisted metadata. Lists and summaries are sorted before persistence/response. Snapshot hashes are checked before any eligible file is parsed.

### Migration and acceptance evidence

- `mvn -q -Dtest=RepositoryScanServiceTest,RepositoryScanMigrationTest,RepositoryScanPersistenceTest,RepositoryImportControllerTest,SecurityConfigTest test -DargLine=-javaagent:/Users/ayushshekharsingh/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.11/byte-buddy-agent-1.18.11.jar`: passed focused scanner, migration, persistence, ownership, controller, and authentication tests.
- `mvn -q test -DargLine=-javaagent:/Users/ayushshekharsingh/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.11/byte-buddy-agent-1.18.11.jar`: **89 backend tests passed**, zero failures/errors/skips. The initial sandboxed run failed only because existing AI-provider stub tests could not bind localhost; the approved localhost-only rerun passed. Scanner tests use no sockets.
- `RepositoryScanMigrationTest` baselined an existing PostgreSQL-mode H2 schema at version 0 and successfully applied the single additive Flyway migration. `RepositoryScanPersistenceTest` created the mapped schema, persisted every scanner collection type, and proved the owner query returns no cross-user metadata.
- Synthetic fixtures passed for Java/Maven, TypeScript/React, mixed frontend/backend modules, SQL metadata, malformed package manifest, unsupported language, nested ignore plus negation, hard-exclusion precedence, generated/minified content, assignment redaction, unsafe embedded private-key exclusion, deterministic repeated scan, exact symbol/import line/path references, internal/alias/dynamic/external resolution labels, and inert package hooks.
- `npm test -- --run`: **22 frontend tests passed** across 6 files. `npm run build`: passed with 41 modules transformed. Step 04 adds no frontend UI.
- `mvn -q -DskipTests package`, `docker compose config --quiet`, `git diff --check`, and static prohibited-call inspection passed. Static inspection found no AI service/provider, HTTP client, process execution, Git clone, build, or package-install call in scanner production paths.
- Docker Desktop was not running (`docker compose ps` could not connect to its socket), so the migration was not executed against the live Compose PostgreSQL service. The H2 PostgreSQL-mode migration check and independent JPA persistence check do not substitute for a live PostgreSQL smoke test; this remains the precise environment verification gap.
- No browser, visual UI check, external repository, imported tool/script, AI call, remote dependency resolution, network repository fetch, commit, or push was used. No visual verification is applicable because Step 04 changes no frontend UI.

### Limitations and risks

- Symbol and import extraction is intentionally lexical and may be partial around unusual syntax, comments, generated syntax, conditional imports, Java nested types, or advanced TypeScript constructs. It is labeled heuristic and is not a complete AST, call graph, or runtime model.
- Ignore matching covers the documented nested path, wildcard, directory, anchoring, and negation behavior used by DevLens fixtures, but does not claim every platform-specific edge case of Git's native implementation.
- Parser bounds can produce a `PARTIAL` file record with no persisted context. Unsupported files retain path/hash metadata only. The scanner never attempts to execute or repair them.
- Flyway currently owns only new scanner tables. Older v1 tables remain under the pre-existing Hibernate update convention; production deployment still needs a complete reviewed baseline migration and live PostgreSQL migration rehearsal.

## v1.2 Step 05 — private repository inventory and lifecycle

### Changed files and purposes

- `RepositoryJob`, `RepositoryJobType`, `RepositoryJobStatus`, `RepositoryJobRepository`, and `V2__add_repository_lifecycle_jobs.sql` add durable owner/snapshot-scoped import and scan job state with optimistic versioning and lookup indexes.
- `RepositoryJobStateService` owns short, isolated job-state transactions, owner checks, cancellation flags, duplicate active/completed scan lookup, and startup recovery that changes stale `QUEUED`/`RUNNING` jobs to `FAILED` with retry guidance.
- `RepositoryLifecycleConfig` and `RepositoryLifecycleProperties` configure a bounded in-process executor and retention schedule. `RepositoryLifecycleService` coordinates synchronous bounded ZIP ingestion, asynchronous bounded scanning, polling, cooperative cancellation, pagination, inventory projection, deletion, and retention without messaging infrastructure.
- `RepositoryImportController`, `RepositoryImportLifecycleResponse`, `RepositoryJobResponse`, and `RepositoryInventoryResponse` expose authenticated lifecycle endpoints. Inventory responses contain snapshot identity, stack/modules, counts, safe aggregated skip reasons, parser coverage, and paginated file metadata; they omit source bodies, private storage keys, absolute paths, and secrets. The older v1.2 import/scan endpoints remain compatible.
- `RepositorySnapshotRepository`, `RepositoryScanRepository`, `RepositoryImportService`, and `RepositoryScanService` add owner pagination, retention lookup, cascade coordination, private-storage cleanup, and cooperative scan cancellation while preserving the existing deterministic scanner behavior.
- `application.properties`, root/backend `.env.example`, `docker-compose.yml`, and `README.md` document bounded worker/queue settings, retention, cleanup frequency, lifecycle APIs, and the deletion boundary.
- `RepositoriesPage`, `repositoryApi`, and repository frontend types provide authenticated ZIP import, scan polling/cancellation, paginated inventory/detail, stack/modules/counts/skip reasons/parser coverage/errors, and confirmed deletion. `App`, `HomePage`, `HistoryPage`, `AnalyticsPage`, and `styles.css` add consistent navigation and responsive presentation without a new UI dependency.
- `RepositoryLifecycleApiTest`, updated repository/security/controller/migration tests, and the inert `repository-fixtures/mixed-project` data cover the lifecycle, authorization, migration, recovery, and safety requirements. `RepositoriesPage.test.tsx` covers import validation/completion/deletion plus metadata rendering with no source body.

### Lifecycle and retention behavior

- Import records move through durable `QUEUED` to `RUNNING`, then `COMPLETED` or `FAILED`; a successful import queues a scan. Scans run only in the configured local bounded executor and move through the same terminal states, including `CANCELLED`. Cancellation sets a persisted flag, interrupts the local future where available, and is checked cooperatively by the scanner.
- A repeated scan request for a snapshot reuses its active or completed scan job, preventing concurrent duplicate scans in the existing single backend process. Re-uploading the same ZIP deliberately creates another immutable snapshot, giving duplicate submissions deterministic separate identities rather than silently overwriting data.
- On application startup, jobs left active by a restart are marked failed and can be retried. Queue rejection is terminal with actionable retry text, so work is not left indefinitely running.
- Owner-checked deletion cancels local work and deletes job rows, scan metadata, the snapshot row, and private snapshot storage. `REPOSITORY_RETENTION_DAYS=0` disables automatic deletion; a positive value applies the same deletion boundary on `REPOSITORY_CLEANUP_INTERVAL_HOURS`. Old failed import jobs without snapshots are also removed. Future derived artifacts must be snapshot-owned/cascaded or explicitly added to this boundary.
- Temporary import paths retain the Step 03 cleanup guarantees on success, validation failure, cancellation/interruption, and runtime failure. Import and inventory have no AI/provider dependency and work while providers are disabled.

### Configuration and migration

- Added `REPOSITORY_WORKER_COUNT` (default `2`, accepted `1..8`), `REPOSITORY_QUEUE_CAPACITY` (default `20`, accepted `1..1000`), `REPOSITORY_RETENTION_DAYS` (default `0`, non-negative), and `REPOSITORY_CLEANUP_INTERVAL_HOURS` (default `24`, positive).
- Flyway migration `V2__add_repository_lifecycle_jobs.sql` creates `repository_jobs` and owner/created plus snapshot/type indexes. It follows the existing scanner-only additive migration boundary; Hibernate still supplies relationships to older v1-managed tables under the documented local `ddl-auto=update` convention.

### Acceptance evidence

- `mvn -q -Dtest=RepositoryLifecycleApiTest test`: **1 integration scenario passed** after correcting the snapshot-deletion transaction boundary. It authenticates two users, rejects unauthenticated import, rejects invalid and oversized uploads, cancels queued work, imports/scans fixture ZIPs, polls terminal state, reuses a duplicate scan request, paginates, checks cross-owner job/detail/delete denial, verifies sensitive/excluded data absence, deletes, and asserts database/private-storage cleanup.
- `mvn -q test -DargLine=-javaagent:/Users/ayushshekharsingh/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.11/byte-buddy-agent-1.18.11.jar`: **91 backend tests passed**, zero failures/errors/skips across 19 test classes. The first sandboxed run failed only because pre-existing AI HTTP-stub tests could not bind localhost; the approved localhost-only rerun passed. Existing analysis creation, history/ownership, provider, authentication, analytics, importer, and scanner coverage remains green.
- `npm test -- --run`: **24 frontend tests passed** across 7 files, including repository inventory/import actions and existing authentication, analysis, result, history, and analytics interactions.
- `npm run build`: passed; TypeScript and Vite production build completed with 43 modules transformed. `mvn -q -DskipTests package`, `docker compose config --quiet`, and `git diff --check` passed.
- Live terminal/API smoke used the existing local PostgreSQL server with an isolated temporary database and port `18080`: register `201`, login `200`, unauthenticated inventory `401`, fixture import `201`, scan `COMPLETED`, inventory/detail `200`, excluded secret/path/source-field check passed, delete `204`, deleted lookup `404`, database snapshot/scan/snapshot-job counts all zero, and private storage empty. Flyway V1 and V2 plus Hibernate mappings started successfully against PostgreSQL 18.6. The temporary database and smoke directory (including synthetic token/response files) were removed afterward; the normal `devlens` database was not modified.
- Synthetic acceptance fixtures are data only. No fixture build script, package hook, test, macro, or tool was invoked. Static inspection found no AI invocation, network fetch, package installation, process execution, or imported-code execution in the import/inventory flow.
- No browser, screenshot, screen recording, or desktop automation was used. Visual checks for responsive layout, focus appearance, file-picker presentation, long names/skip labels, and live polling transitions remain explicitly unverified.

### Limitations and risks

- In-process scan jobs are durable as state but are not resumed after restart; interrupted jobs fail clearly and require an explicit retry. This avoids pretending exactly-once execution without messaging infrastructure.
- Cancellation is cooperative and may arrive after a fast scan already completed. ZIP upload/import remains synchronous and bounded because an HTTP multipart stream cannot safely survive beyond its request; cancellation is useful for queued/running scan work.
- Duplicate ZIP submissions intentionally produce separate immutable snapshots; duplicate scan requests for one snapshot are coalesced in the existing process. Multi-instance global scan locking is outside this single-process scope.
- Inventory exposes bounded metadata only. Full symbol/import/dependency detail remains available through the preserved scanner contract, not duplicated into this page. Source bodies remain private and are not returned by default.
- Secret filtering and safe skip aggregation reduce accidental exposure but cannot guarantee detection of every secret. Docker Desktop was not running, so Compose service startup was not repeated; Compose configuration validation passed and the required live workflow was instead verified against the existing host PostgreSQL service.

### Verification status

- **v1.2 Step 05 acceptance checks are complete. v1.2 is marked complete.** The only deferred check is manual visual review, recorded above as unverified rather than performed with a browser.

## v1.3 Step 06 — bounded local repository analysis orchestration

### Implementation and policy boundary

- `RepositoryAnalysisOrchestrator`, its preparation/state services, and the repository-analysis controller add owner-scoped start, status, cancellation, and paginated unit-result APIs over completed v1.2 snapshots. Starts validate snapshot ownership/state plus the configured Ollama profile and installed model before a job is persisted or queued. Equivalent active or terminal work is returned idempotently.
- `RepositoryAnalysisProvider` is a repository-specific local-provider boundary. Its only production adapter is `OllamaRepositoryAnalysisProvider`; it wraps the existing Ollama implementation and never references the cloud-compatible provider. Repository requests accept only a server-managed profile identifier and validated installed model name, never a URL. A selected-local failure is terminal and cannot fall through to snippet defaults or cloud providers.
- `RepositoryAnalysisJob`, stage, and unit entities plus Flyway `V3__add_repository_analysis_orchestration.sql` persist status/checkpoints, immutable snapshot aggregate hash, profile/model/provider identity, prompt/parser/schema versions, deadlines, progress, analyzed/skipped counts, call/input/output usage, safe errors, and exact chunk provenance. Snapshot deletion now cancels work and deletes these derived rows before deleting the snapshot.
- `RepositoryAnalysisStateService` uses short independent transactions for transitions and checkpoints. No database transaction spans a model call. Unique job-stage and job-chunk constraints plus optimistic job versioning prevent duplicate checkpoint rows.
- Startup recovery marks active work interrupted, verifies snapshot hash and version checkpoints, and resumes only persisted valid units before the original deadline. Invalid or absent checkpoints remain explicitly interrupted or fail with safe retry guidance.

### Bounds, chunking, and configuration

- `RepositoryChunker` uses deterministic stable line windows with bounded overlap, including splitting an oversized single line. Every chunk retains a stable identifier, snapshot file hash, relative path, sequence, and original inclusive line range. Eligible Java, JavaScript/TypeScript/JSX/TSX, Python, and C++ records are processed; unsupported records become explicit skipped units.
- Input capacity is the configured model context minus system, schema, output, and safety reserves. In the absence of a project tokenizer, the implementation conservatively estimates at most three UTF-16 characters per token plus fixed prompt-metadata overhead and handles overflow by splitting or skipping. Parameter count is never used as context size.
- A bounded job executor and a separate bounded local-inference executor enforce worker count, queue capacity, and inference concurrency; defaults are one job worker and one local inference. Each call has a timeout and each job has an absolute deadline. Configured file, call, input-token, and output-byte budgets are checked before calls; exhausted remainder is recorded as budget-specific skipped units and yields `PARTIAL`, not fabricated completion.
- Environment-backed settings are `REPOSITORY_ANALYSIS_WORKERS`, `REPOSITORY_ANALYSIS_QUEUE_CAPACITY`, `REPOSITORY_ANALYSIS_INFERENCE_CONCURRENCY`, `REPOSITORY_ANALYSIS_CALL_TIMEOUT_SECONDS`, `REPOSITORY_ANALYSIS_JOB_DEADLINE_SECONDS`, `REPOSITORY_ANALYSIS_MAX_CALLS`, `REPOSITORY_ANALYSIS_MAX_INPUT_TOKENS`, `REPOSITORY_ANALYSIS_MAX_OUTPUT_BYTES`, `REPOSITORY_ANALYSIS_MAX_FILES`, `REPOSITORY_ANALYSIS_CONTEXT_TOKENS`, `REPOSITORY_ANALYSIS_SYSTEM_RESERVE_TOKENS`, `REPOSITORY_ANALYSIS_SCHEMA_RESERVE_TOKENS`, `REPOSITORY_ANALYSIS_OUTPUT_RESERVE_TOKENS`, `REPOSITORY_ANALYSIS_SAFETY_RESERVE_TOKENS`, and `REPOSITORY_ANALYSIS_CHUNK_OVERLAP_LINES`. Defaults and validation are documented in both example environment files, application properties, Compose, and README.

### Acceptance evidence

- `mvn -q test`: **98 backend tests passed** across 21 test classes, zero failures/errors/skips. The new fake-provider suite covers stable chunk boundaries/provenance, oversized-line splitting, file/call/input/output limits, per-call prompt bounds, concurrency limited to one, queue rejection, cancellation, timeout, installed-model rejection, idempotency, owner denial, valid restart recovery, and invalid-checkpoint interruption. Controller/security tests cover validation, bounded result paging, and unauthenticated denial.
- Existing provider and snippet tests remained green, including the pre-existing Ollama/cloud selection and public analysis-response contracts. The repository orchestrator has no `AiCodeReviewProvider` dependency, and its fake records prove repository source is delivered only through `RepositoryAnalysisProvider`; failure has no fallback path.
- `RepositoryScanMigrationTest` successfully baselined PostgreSQL-mode H2 and applied Flyway V1, V2, and V3. `mvn -q -DskipTests package`, `npm test -- --run` (**24 frontend tests across 7 files**), `npm run build` (**43 modules transformed**), `docker compose config --quiet`, and `git diff --check` passed.
- During implementation, the first focused orchestration run exposed shared-context job-count state and was corrected to assert relative persisted counts. The first full regression run exposed that a blank default Ollama model had accidentally become valid for the snippet constructor; the explicit-selection-only behavior was moved to a separate repository-adapter constructor and the focused provider test plus full suite then passed.
- No browser, screenshot, screen recording, desktop automation, imported repository execution, package installation, model download, cloud call, commit, or push was used. Step 06 has no frontend UI change, so no new visual check applies.

### Limitations and risks

- The token calculation is a documented conservative estimate rather than a model-specific tokenizer. The Ollama adapter also applies the configured output reserve as `num_predict`, while actual persisted output use is bounded by response bytes.
- Execution and idempotent start synchronization are process-local. Durable checkpoints make restart state explicit, but this is not a multi-instance distributed lock or exactly-once execution system.
- Results are persisted per bounded chunk. Cross-file synthesis or a final repository-wide report is intentionally deferred; the implementation never sends the whole repository in one prompt.
- Live Ollama inference was not required by this step and was not run. Fake-provider checks establish orchestration behavior but do not prove model-specific latency or output quality. The migration ran in PostgreSQL-mode H2 rather than a live PostgreSQL instance in this step.

### Verification status

- **Step 06 acceptance checks are complete.** No visual check is deferred because this step changes backend orchestration only.

## v1.3 Step 07 — hierarchical summaries with provenance

### Implementation and persistence

- `RepositoryHierarchySummaryService` now reduces validated local summaries through `CHUNK`, `FILE`, `MODULE`, and `REPOSITORY` levels. Child summaries are placed into bounded batches and recursively reduced; no level concatenates all repository content into one call. The existing call/input/output/file/deadline/concurrency/cancellation limits remain authoritative, and budget exhaustion persists explicit failed/skipped coverage rather than claiming completion.
- `RepositorySummaryResult` is the strict provider contract for responsibilities, key symbols, dependencies, uncertainty, and path/line evidence. `OllamaAiProvider` requests only this exact JSON shape for repository summaries and rejects malformed/oversized output. The system message identifies code, comments, documentation, identifiers, and child summaries as untrusted, forbids following their instructions, and grants no shell, filesystem, network, or tool capability. Source and child data are explicitly delimited.
- `RepositorySummary`, its level enum/repository/DTOs, and additive Flyway migration `V4__add_repository_hierarchical_summaries.sql` persist summaries, uncertainty, cache status, and evidence separately from findings. `GET /api/repositories/analysis-jobs/{id}/summaries` is authenticated and owner-checked. It exposes hierarchy coverage and safe provenance without source bodies or server paths.
- Every evidence path and inclusive line range is checked against both the exact scan snapshot and the evidence supplied to that summary level. Invented paths, out-of-range lines, or citations outside child evidence are rejected. A summary without evidence must retain uncertainty. Summaries are advisory descriptions, not verified defects.
- Chunk input comes only from the scanner's already filtered/redacted `safeContent`; merge calls receive only validated structured summaries and bounded dependency metadata. No excluded raw content is reloaded. Selected evidence remains persisted so later retrieval can return to exact snapshot code instead of relying only on lossy text.
- The owner-private cache key contains user ID, safe content/dependency identity, selected model, prompt/schema/parser versions, context reserves, call/input/output/file budgets, and overlap configuration. Child identities feed ancestor identities, so file edits and dependency changes invalidate affected file/module/repository entries. Model, prompt, schema, parser, or budget changes invalidate all affected levels. Snapshot deletion removes cached summaries through the existing derived-artifact boundary.
- Malformed structured output and invalid evidence receive at most one repair retry. Exhausted retries mark the unit partial/failed with a safe error; raw repository content and raw model output are not logged.

### Acceptance evidence

- `mvn -q test`: **102 backend tests passed** across 21 test classes, zero failures/errors/skips. The hierarchy fake-provider coverage includes tiny repositories, oversized module/chunk reduction, stable provenance, strict per-call bounds, budget exhaustion, cancellation, fake citations, malformed output with exactly one retry, injected repository instructions, and owner denial.
- Cache acceptance checks prove reuse for unchanged safe content, no cross-owner reuse, and invalidation after file hash/content edits, dependency changes, model changes, prompt-version changes, and budget/configuration changes. Ancestor identities incorporate child and dependency identities.
- The local Ollama adapter test inspected the synthetic request and confirmed that injected text remains inside explicit untrusted delimiters while the system instruction forbids following repository instructions or requesting tool execution. It also validated the strict structured response mapping.
- Flyway's PostgreSQL-mode H2 check baselined version 0 and applied V1 through V4, including the summary and evidence tables. Existing snippet-provider, authentication, ownership, repository lifecycle, scanner, migration, and public response tests remained green.
- `mvn -q -DskipTests package`, `npm test -- --run` (**24 tests across 7 files**), `npm run build` (**43 modules transformed**), `docker compose config --quiet`, and `git diff --check` passed.
- `curl --max-time 2 http://127.0.0.1:11434/api/tags` failed to connect on 2026-09-24. Therefore the optional real-Ollama fixture was unavailable: no model validity, omissions, or latency claim is made. Fake/stub checks do not prove real-model summary quality.
- No browser, screenshot, screen recording, desktop automation, imported code execution, package installation, cloud call, model installation/download, commit, or push was used. Step 07 changes no frontend UI, so no visual check applies.

### Corrections discovered during checks

- The first focused run exposed lazy loading while mapping cached element collections; the summary collections are now loaded within the bounded owner-scoped summary path, and the rerun passed.
- The first cache-invalidation assertion then exposed that detached cached children could stop the hierarchy before dependency-aware ancestors. Initializing the persisted structured cache entry fixed reuse and ensured dependency changes trigger new ancestor calls.
- The pre-existing Step 06 budget fixture initially completed after the larger Step 07 test budget was introduced. Its synthetic source was enlarged so it continues to prove explicit budget truncation rather than asserting a partial status without exhausting a bound.
- A final cancellation rerun exposed an optimistic-lock race between an owner cancellation and a worker checkpoint. Short job-state mutations now acquire a database row lock, cancellation becomes terminal in its owner transaction, and the focused plus complete 102-test suites passed afterward.

### Limitations and risks

- Summary quality and omissions depend on the selected installed local model. Validation proves structure and evidence bounds, not semantic correctness or exhaustive understanding.
- The conservative token estimate remains model-agnostic. Output and merge payloads are additionally size-bounded, but an exact model tokenizer is not available.
- Cache reuse is owner-private and safe-content based within the existing single-process/database design. It is not a distributed cache or a claim of identical behavior across different model builds that share the same administrator-supplied model name.
- Summary evidence is retained, but no targeted retrieval or cross-file finding workflow is added in this step.

### Verification status

- **Step 07 acceptance checks are complete with stub/fake providers.** The optional real-Ollama fixture is precisely recorded as unavailable and is not claimed as verified.
