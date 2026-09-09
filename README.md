# DevLens AI

DevLens AI is an incrementally built code-review and test-generation platform. Day 2 contains a Spring Boot health API and a React UI that displays its availability.

## Prerequisites

- Java 21 or newer
- Maven 3.6.3 or newer
- Node.js 20.19+ or 22.12+
- PostgreSQL

## Run the backend

Create a PostgreSQL database named `devlens`, then set the local environment values:

```bash
cd backend
export DB_URL="jdbc:postgresql://localhost:5432/devlens"
export DB_USERNAME="postgres"
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
