# Deployment

## Topology

```
React (static build)  --->  Spring Boot API  --->  PostgreSQL
   served as static           (stateless,
   files from any               JWT auth)
   static host / CDN
```

## Backend

1. Build: `mvn -DskipTests clean package` → `backend/target/teamflow-backend-1.0.0.jar`.
   Run it directly with `java -jar` on the target host/VM/PaaS — no
   container runtime is required.
2. Provide these environment variables in the target environment (see
   `backend/.env.example` for the full list): `DB_URL`, `DB_USERNAME`,
   `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `FRONTEND_URL`,
   `CORS_ALLOWED_ORIGINS`, `SERVER_PORT`, `LOG_LEVEL`.
3. Run the jar (or container). Flyway migrates the schema automatically
   on startup — there is no separate manual migration step.
4. Verify `GET {API_URL}/actuator/health` returns `{"status":"UP"}`.

## Frontend

1. Set `VITE_API_BASE_URL` to the deployed backend's `/api` URL (build-time
   environment variable — see `frontend/.env.example`).
2. Build: `npm ci && npm run build` → static files in `frontend/dist/`.
3. Deploy `dist/` to any static host (S3+CloudFront, Netlify, Vercel,
   Nginx, etc.). No server-side rendering is required.
4. Because this is a client-side-routed single-page app, the host must
   fall back to `index.html` for unknown paths (otherwise refreshing on
   e.g. `/projects/3` 404s at the host level, not inside the app).

## Local → Production, concretely

| Value | Local default | Production |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/teamflow` | your managed Postgres connection string |
| `JWT_SECRET` | (must still be ≥32 bytes; no default is baked in) | a long random secret from a secrets manager |
| `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | `https://your-production-frontend-domain` |
| `VITE_API_BASE_URL` (frontend build) | `http://localhost:8080/api` | `https://your-production-api-domain/api` |

No application source file changes between these environments — only
the values above change, which is the hard requirement this project was
built against (see `DEVELOPMENT_DECISIONS.md` §13/§26).

## Pre-flight checklist before calling a deployment "done"

- [ ] Frontend can reach the backend (`VITE_API_BASE_URL` correct, CORS allows it)
- [ ] Backend can reach PostgreSQL
- [ ] `/actuator/health` returns UP
- [ ] Login works end-to-end from the deployed frontend
- [ ] A manager-only action is rejected for a team-member account (403)
- [ ] Flyway migrations applied cleanly (check backend startup logs)
- [ ] No `localhost` value present anywhere in the deployed frontend bundle
- [ ] `JWT_SECRET` in production is not the local-dev placeholder

## What was and wasn't verified in this environment

The frontend was actually installed and built in the sandbox that produced
this repository (`npm install && npm run build` completed with no errors,
109 modules transformed). The backend could **not** be compiled here —
this sandbox's network allow-list does not include Maven Central, only a
small fixed set of domains — so `mvn compile`/`mvn test` were not run.
The code was written and manually reviewed (including a programmatic
brace-balance check across all 59 Java source files), but you should run
`mvn test` yourself on first checkout before deploying.
