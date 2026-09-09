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

## AI provider foundation

The backend currently registers a clearly labeled mock code-review provider, so local development does not require an API key and no external AI request is made. Leave this value blank:

```bash
export AI_API_KEY=""
```

The `CodeReviewService` depends on the `AiCodeReviewProvider` interface rather than a specific vendor. A future external provider can supply that interface and replace the fallback mock without changing controllers or application-level review logic.
