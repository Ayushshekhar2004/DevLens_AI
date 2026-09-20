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
