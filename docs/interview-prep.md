# DevLens AI — interview preparation

## Project story (about 60 seconds)

“I built DevLens AI as a portfolio project to make a first-pass code review easier to organize. A user signs in, submits Java, Python, JavaScript, or C++ source text, and gets a structured review with possible bugs, complexity notes, edge cases, suggestions, proposed code, test-case ideas, and advisory security findings. The result is saved in PostgreSQL and is available in a private, searchable history and a small analytics dashboard. I used React and TypeScript for the UI, Java 21/Spring Boot for the API, JWT and BCrypt for authentication, and an AI provider interface so local development can use a clearly labeled mock while a configured OpenAI-compatible service can provide real responses. The app never executes submitted code. I focused on a readable end-to-end workflow, ownership boundaries, failure handling, and tests, while leaving production hardening and asynchronous processing as future work.”

Do not describe the mock response as an actual AI review. The mock returns placeholders and intentionally does not infer expected outputs or security findings.

## Complete request flow

1. The browser renders the React app. After login, the UI retains the JWT and public user details in per-tab `sessionStorage`.
2. On “Analyze Code,” the form rejects blank source text locally. `analysisApi.createAnalysis` sends JSON with `language` and `sourceCode`, plus `Authorization: Bearer <token>`.
3. In Spring Security, `JwtAuthenticationFilter` validates the token, extracts its email subject, loads the current `User`, and places that user in the security context. Missing or invalid credentials produce `401` on protected routes.
4. `AnalysisController` receives a validated request DTO and the authenticated user, then delegates to `AnalysisService`.
5. The service saves an `Analysis` as `PENDING`, owned by that user. The JPA repository writes it to PostgreSQL.
6. `CodeReviewService` calls the selected `AiCodeReviewProvider`. The mock returns labeled placeholders; the real provider sends a structured request to an external OpenAI-compatible endpoint and validates its JSON response.
7. On success, the service stores the review fields and element collections, changes status to `COMPLETED`, and returns an `AnalysisResponse` DTO with `201 Created`. On provider failure, it stores `FAILED` and a safe reason, then returns a clear API error.
8. React parses the JSON, stops the loading state, and renders review cards and the original/suggested-code comparison. A failed request shows an error; history can retrieve the saved failed record.
9. Later history/detail/analytics requests use owner-scoped repository queries. A user cannot fetch or delete another user's analysis by guessing its ID; that read returns `404`.

For Docker deployment, Nginx serves React and proxies browser `/api` requests to the `backend` Compose service; the backend connects to PostgreSQL at service hostname `db`.

## Why each layer exists

| Layer | Role in DevLens AI |
| --- | --- |
| Controller | Defines HTTP routes/status codes, accepts validated request DTOs, delegates work, and returns response DTOs. It does not call the AI provider directly. |
| Service | Implements registration, login, analysis state transitions, owner-scoped history/deletion, and analytics rules. |
| Repository | Spring Data JPA access to PostgreSQL, including `findByIdAndUserId`, paginated specifications, and owner-scoped aggregate queries. |
| Entity | JPA persistence model: `User`, `Analysis`, and embedded ordered review collections. Not returned directly to clients. |
| DTO | Stable API shapes for requests, errors, analyses, generated tests, security findings, and analytics; prevents leaking password hashes or JPA internals. |
| AI provider | Replaceable boundary for mock or external HTTP review. Real-provider prompt, timeout, response parsing, and schema validation stay out of controllers. |
| JWT | Signed bearer token proves login on each protected request. The filter resolves its subject to a current user; it is not an encrypted data store. |
| BCrypt | One-way, salted password hash. Registration saves a hash; login verifies with `matches`. Passwords are not stored in plaintext. |
| PostgreSQL | Durable users, analyses, review fields, test cases, findings, and data-backed history/analytics. |

## 25 likely interview questions and honest answer outlines

1. **What problem does the project solve?** It organizes a first-pass, structured review and test ideas in one saved workflow. It assists developers; it cannot prove code correctness or security.
2. **Why did you build it?** To demonstrate an end-to-end product: React interaction states, Java API design, persistence, authentication, external-service boundaries, and tests—not just an AI API call.
3. **Walk me through submitting code.** Explain the browser-to-API-to-JWT-filter-to-controller-to-service-to-PostgreSQL/AI flow above, then the DTO response and React loading/success/error states.
4. **Why separate Controller and Service?** HTTP concerns belong in controllers; workflow, ownership, and state transitions belong in services. This keeps logic testable without HTTP and avoids vendor calls in controllers.
5. **Why use DTOs rather than expose entities?** DTOs control the API contract and avoid exposing `passwordHash`, lazy JPA relationships, or persistence-specific structure.
6. **What does the Repository layer do?** It uses `JpaRepository` for CRUD, `JpaSpecificationExecutor` for owner-scoped filtered history, and explicit aggregate queries for analytics.
7. **How is an analysis tied to a user?** `Analysis` has a many-to-one `User` relationship. Creation attaches the authenticated user; reads/deletes query by both analysis ID and user ID.
8. **What happens if user A requests user B's analysis?** The owner-scoped lookup finds nothing and the API returns `404`, so it does not reveal whether that ID exists.
9. **Why JWT, and what is in it?** Login returns a signed token with the user email as subject and an expiration. Protected requests validate it and load the current user; the JWT is not used as a substitute for database ownership checks.
10. **Where is the token stored?** The frontend uses `sessionStorage` for the current tab and clears it on logout, expiry, or a protected request's `401`. It is still readable by JavaScript, so XSS prevention matters.
11. **How are passwords protected?** BCrypt hashes them on registration and verifies them on login. The API returns only public user fields, never the hash.
12. **How do you handle duplicate registration?** The service checks case-insensitively before save; the unique database constraint remains the final guard against races. A duplicate becomes a conflict response.
13. **Why use a provider interface?** `AiCodeReviewProvider` lets business logic depend on an abstraction. Local mock and OpenAI-compatible implementations can be swapped without changing controller or analysis workflow code.
14. **What does the mock provider do?** It keeps the app runnable without an API key and clearly marks its result as a placeholder. It does not perform a real review or fabricate expected output.
15. **How do you make AI output usable?** The real provider requests strict structured JSON, then parses and validates required fields, categories, and severities before persistence. Validation reduces malformed responses but does not guarantee factual correctness.
16. **What happens when AI times out or returns invalid JSON?** The service marks the initial record `FAILED`, stores a safe reason, and returns an explanatory error. The UI shows an error and the saved record can be revisited.
17. **Why save `PENDING` first?** It gives the request a durable identity and allows failures to be recorded rather than disappearing. The current call is still synchronous, not a background job.
18. **Do you run submitted code or generated tests?** No. The app reviews source text only. Running untrusted code would require a separate isolated execution architecture and threat review.
19. **How are generated test cases represented?** As ordered embedded values attached to an analysis, with name, category, input, expected output, explanation, and confidence/warning text. They are suggestions, not executed results.
20. **How reliable are security findings?** They are advisory AI output with severity and uncertainty notes. Limited context can cause false positives and missed issues; they do not replace static analysis or a professional audit.
21. **How does history scale?** `/api/analyses/history` applies user scope, search, language filter, sort, and pagination in the database, with a page-size cap of 100. The older `GET /api/analyses` remains unpaginated and is a known limitation.
22. **Where do analytics numbers come from?** Owner-scoped database counts by language and finding severity, a generated-test count, and the five most recent stored analyses. There are no invented productivity scores.
23. **What tests did you add?** Backend tests cover validation, auth, ownership, state transitions, controllers, and provider failures; frontend tests cover session/login, submit states, and history actions. Tests do not yet constitute full browser end-to-end coverage.
24. **How does Docker Compose connect services?** The frontend Nginx container proxies `/api` to `backend:8080`; Spring Boot connects to `db:5432`. Compose supplies service DNS, environment values, startup ordering, and a named PostgreSQL volume.
25. **What would you improve first with more time?** Add versioned database migrations and an asynchronous review job with polling or events. Then add full browser/accessibility tests, stronger deployment/security controls, and account recovery—without claiming these exist today.

## Tradeoffs and limitations to volunteer

- **Synchronous review:** Simpler to understand and test, but the HTTP request waits for the provider. A queue/job model would improve resilience and user experience at higher latency or volume.
- **Development schema updates:** Hibernate `ddl-auto=update` helps local iteration, but controlled migrations are needed before production.
- **Per-tab token storage:** Simple for this portfolio app; no refresh-token or account-recovery flow. Any XSS could access the token.
- **External AI uncertainty:** Strict JSON validation catches shape errors, not hallucinated analysis. The mock is only a placeholder.
- **Untrusted code boundary:** Source is stored and possibly sent to a configured provider, but never executed. Users should not submit secrets or proprietary code without approval.
- **Scale:** Paginated history and database aggregates exist, but `GET /api/analyses` is unpaginated and there is no background worker, caching layer, or production observability stack.

## If I had more time

Prioritize (1) Flyway/Liquibase migrations and repeatable deployment configuration; (2) asynchronous, idempotent analysis jobs with clear `PENDING`/retry behavior; (3) full browser and accessibility testing; (4) rate limits, HTTPS, managed secrets, backups, and monitoring; and (5) account recovery and token lifecycle improvements. I would evaluate each against actual usage rather than add infrastructure just for its own sake.
