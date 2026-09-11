# DevLens AI

DevLens AI is an AI-assisted code-review and test-case-generation MVP. It accepts source code in a React interface, reviews it through a configurable AI provider, persists the structured result in PostgreSQL, and presents readable feedback without executing submitted code.

## MVP features

- Submit Java, Python, JavaScript, or C++ source code from the browser.
- Validate empty submissions and show loading, success, provider, and network errors.
- Use a clearly labeled mock provider locally or an OpenAI-compatible provider configured with environment variables.
- Return and persist a structured summary, potential bugs, time and space complexity, edge cases, suggestions, and improved code.
- Generate and persist categorized test-case suggestions with input, expected output, explanation, and confidence or uncertainty warnings.
- Generate and persist advisory security findings with severity, evidence, remediation, and confidence or uncertainty notes.
- Reload stored analyses through `GET /api/analyses/{id}` and list analyses newest first through `GET /api/analyses`.
- Display structured review sections and generated test cases without exposing raw JSON.
- Display advisory security findings with restrained severity labels, remediation guidance, and uncertainty notes.
- Copy improved code to the clipboard and reset the editor for a new analysis.
- Keep failed AI analyses stored with a safe failure reason.
- Register users with BCrypt-hashed passwords and issue signed JWTs after successful login.
- Register and log in from the frontend, keep authentication for the current browser tab, and log out explicitly.

Analysis endpoints require JWT authentication and only return the signed-in user's records. The MVP does not yet include a frontend history screen, a broader security review, or execution of submitted code.

## Prerequisites

- Java 21 or newer
- Maven 3.6.3 or newer
- Node.js 20.19+ or 22.12+
- PostgreSQL

## Set up PostgreSQL

Start PostgreSQL. With Homebrew on macOS:

```bash
brew services start postgresql@18
```

Create the application database if it does not already exist:

```bash
createdb devlens
```

Alternatively, from `psql`:

```sql
CREATE DATABASE devlens;
```

No tables need to be created manually. During local startup, Hibernate updates the schema and creates the analysis and structured-result collection tables from the JPA model.

## Run the backend

Set the local environment values, replacing the username and password with your PostgreSQL credentials. Homebrew PostgreSQL commonly uses your macOS username and an empty password for local connections.

```bash
cd backend
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="devlens"
export DB_USERNAME="your-postgres-user"
export DB_PASSWORD="your-local-password"
export SERVER_PORT="8080"
export FRONTEND_ORIGIN="http://localhost:5173"
export JWT_SECRET="replace-with-a-random-secret-at-least-32-bytes-long"
export JWT_EXPIRATION_MINUTES="60"
mvn spring-boot:run
```

Verify the API directly:

```bash
curl http://localhost:8080/api/health
```

Run backend tests with `mvn test` from `backend/`.

## Authentication API

Register a user:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ada Lovelace","email":"ada@example.com","password":"replace-with-a-strong-password"}'
```

Registration returns public user fields only. Passwords are hashed with BCrypt before persistence; plaintext passwords and password hashes are never returned.

Log in:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ada@example.com","password":"replace-with-a-strong-password"}'
```

Successful login returns a signed token, the `Bearer` token type, expiration time, and public user details. Keep the returned token private and send it in the `Authorization` header for every analysis request.

Create an analysis as the logged-in user:

```bash
curl -X POST http://localhost:8080/api/analyses \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"language":"JAVA","sourceCode":"public class Main {}"}'
```

Read one of your analyses or list all of them, newest first:

```bash
curl http://localhost:8080/api/analyses/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

curl http://localhost:8080/api/analyses \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

Analysis records are owned by the user who creates them. A missing, expired, or invalid token returns `401`; requesting another user's analysis returns `404` so the API does not reveal whether that record exists.

Paginated history is available at `GET /api/analyses/history`. Page numbers start at zero, the default page size is 20, and the maximum page size is 100:

```bash
curl "http://localhost:8080/api/analyses/history?page=0&size=20&sort=newest" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

curl "http://localhost:8080/api/analyses/history?search=Main&language=JAVA&sort=oldest" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

The optional `search` parameter searches source code and stored result summaries. `language` accepts `JAVA`, `PYTHON`, `JAVASCRIPT`, or `CPP`. `sort` accepts `newest` or `oldest`. Filtering, sorting, pagination, and ownership constraints are applied by the database query.

Delete one of your analyses:

```bash
curl -X DELETE http://localhost:8080/api/analyses/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

A successful deletion returns `204 No Content`. Missing records and records owned by another user both return `404`.

## Run the frontend

In a second terminal:

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Open `http://localhost:5173`. Run `npm run build` to type-check and create a production build.

If either application uses a different port, keep `VITE_API_BASE_URL` in `frontend/.env` and `FRONTEND_ORIGIN` in the backend environment aligned with their actual URLs.

## Run frontend and backend together

Keep PostgreSQL running, then use two terminals.

Terminal 1:

```bash
cd backend
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="devlens"
export DB_USERNAME="your-postgres-user"
export DB_PASSWORD="your-local-password"
export FRONTEND_ORIGIN="http://localhost:5173"
export JWT_SECRET="replace-with-a-random-secret-at-least-32-bytes-long"
export JWT_EXPIRATION_MINUTES="60"
mvn spring-boot:run
```

Terminal 2:

```bash
cd frontend
cp .env.example .env  # only needed once
npm install           # only needed after dependency changes
npm run dev
```

Open `http://localhost:5173`, create an account or log in, and submit code from the protected dashboard. The frontend stores the JWT and public user details in `sessionStorage`, sends the token as `Authorization: Bearer <token>` for analysis requests, and clears the session on logout, token expiry, or a backend `401` response. Passwords are never stored.

## AI provider configuration

Local development defaults safely to the clearly labeled mock provider when no API key is configured:

```bash
export AI_PROVIDER="mock"
```

To use an OpenAI-compatible chat-completions API, configure all provider values through environment variables:

```bash
export AI_PROVIDER="openai-compatible"
export AI_API_KEY="your-secret-api-key"
export AI_BASE_URL="https://api.openai.com/v1/"
export AI_MODEL="gpt-4.1-mini"
export AI_TIMEOUT_SECONDS="30"
```

Never commit a real API key. `AI_PROVIDER=auto` selects the real provider when `AI_API_KEY` is present and otherwise uses the mock. The provider asks for strict structured JSON and validates the complete response schema before returning a result.

Security findings are advisory rather than proof of a vulnerability. The provider reviews only the submitted source text without executing it, performing malware analysis, inspecting dependencies, or observing runtime configuration. Findings can therefore contain false positives or miss issues when relevant context is absent. Validate important findings through human review and appropriate security tooling before acting on them.

The `CodeReviewService` depends on `AiCodeReviewProvider`, not a specific vendor. Provider HTTP details, authentication, error translation, and JSON parsing therefore remain outside controllers and application-level review logic.
