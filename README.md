# DevLens AI

DevLens AI is an incrementally built code-review and test-generation platform. The application currently accepts code submissions from React, stores analyses through the Spring Boot API, and displays the saved analysis metadata.

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

No tables need to be created manually. During local startup, Hibernate updates the schema and creates the `analyses` table from the `Analysis` entity.

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
mvn spring-boot:run
```

Verify the API directly:

```bash
curl http://localhost:8080/api/health
```

Run backend tests with `mvn test` from `backend/`.

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
mvn spring-boot:run
```

Terminal 2:

```bash
cd frontend
cp .env.example .env  # only needed once
npm install           # only needed after dependency changes
npm run dev
```

Open `http://localhost:5173`, select a language, enter source code, and choose **Analyze Code**. The frontend sends the request to `http://localhost:8080/api/analyses` by default.

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

The `CodeReviewService` depends on `AiCodeReviewProvider`, not a specific vendor. Provider HTTP details, authentication, error translation, and JSON parsing therefore remain outside controllers and application-level review logic.
