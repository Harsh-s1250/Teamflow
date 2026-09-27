# TeamFlow — Project & Task Management System

A production-oriented, full-stack project and task management system
built to demonstrate business-process understanding, clean architecture,
backend-enforced business rules, authentication/authorization, and
environment-independent configuration — not a flashy UI.

## 1. Overview

TeamFlow centralizes projects, project membership, tasks, ownership,
deadlines, priorities, dependencies, progress, and overdue detection for
a small team, with every rule enforced on the backend regardless of what
the frontend allows.

## 2. Problem Statement

Teams commonly struggle with unclear task ownership, missed deadlines,
invalid assignments, tasks starting before their prerequisites are
finished, poor visibility into progress, and unauthorized access to
project data. TeamFlow's backend makes each of these structurally hard
to hit rather than just discouraged by UI hints.

## 3. Objectives

- Demonstrate a clean layered architecture (Controller → Service →
  Repository → Database) with thin controllers and rule-owning services.
- Enforce every business rule server-side, with the database as a
  second line of defense for critical integrity rules.
- Implement real JWT authentication and role/ownership-based
  authorization — never hidden-button "security."
- Externalize all environment-specific configuration so the same
  source/build moves from local → test → production unchanged.
- Provide meaningful automated tests, including failure scenarios.
- Ship a medium-complexity, professional React frontend that
  demonstrates the backend, without over-investing in visual polish.

## 4. Features

- JWT authentication, role-based + resource-based authorization
- User management: managers create accounts and activate/deactivate them
- Projects: create/update/delete, status lifecycle, computed progress
- Project membership management (unique per project, active users only)
- Tasks: ownership, priority, status, dates, progress
- Task dependencies with cycle prevention
- Dynamic overdue detection (never stored, always computed)
- Dashboard with project/task counts and overdue/blocked/high-priority lists
- Kanban-style task board
- Consistent API error model; loading/empty/error/unauthorized UI states
- Flyway-managed PostgreSQL schema with real constraints

## 5. User Roles

- **PROJECT_MANAGER** — create/update projects, manage membership,
  create/assign tasks, set priorities/deadlines, define dependencies,
  monitor progress, **and manage the user directory** (create accounts,
  activate/deactivate them). The manager acts as the administrative user
  for this MVP — there is no separate ADMIN role.
- **TEAM_MEMBER** — view permitted projects/tasks, update progress and
  permitted status transitions on tasks they own, view dependencies.
  Cannot create, list, view, or activate/deactivate other users.

All of the above is enforced server-side — see `docs/security.md`.

## 5a. User Management

TeamFlow has no public/self-service registration. A new person enters
the system only through a project manager:

```
Manager creates user (name, email, role, temporary password)
        ↓
User can log in with that temporary password
        ↓
Manager adds the user to a project (ProjectMember)
        ↓
User becomes eligible to own tasks in that project
        ↓
Manager assigns tasks to the user
```

- **Who can create users**: only `PROJECT_MANAGER` accounts
  (`POST /api/users`); a `TEAM_MEMBER` request is rejected with `403`.
- **How users are created**: a manager submits `name`, `email`, `role`,
  and a `temporaryPassword`. The password is hashed with the
  application's existing `PasswordEncoder` (BCrypt) before it is ever
  persisted — the raw value is never stored, logged, or returned by any
  API response.
- **Roles**: `PROJECT_MANAGER` or `TEAM_MEMBER`, same two roles used
  everywhere else in the system — no new role was introduced for this
  feature.
- **Activation/deactivation**: `PATCH /api/users/{id}/status` (manager
  only) flips a user's `active` flag. There is **no delete endpoint**.
  Deactivating a user is reversible and does not touch their existing
  task ownership or project membership history — it only prevents them
  from being *newly* assigned to a project or task going forward (and,
  per the existing authentication design, from logging in at all while
  inactive).
- **Becoming a project member**: adding a user to a project
  (`POST /api/projects/{id}/members`) and assigning them a task both
  re-check, server-side, that the user exists, is active, and (for task
  ownership) already belongs to the project — see
  `docs/business-rules.md` rules 3 and 12.
- **Password security**: full detail in `docs/security.md` — BCrypt via
  the same `PasswordEncoder` bean used at login, never a second hashing
  scheme.

The **Users** screen (sidebar link, manager-only) lists all users with
search/role/status filters, a "+ Add user" form, and an
activate/deactivate control with a confirmation dialog.

## 6. Business Workflow

```
Manager creates project → adds members → creates tasks → assigns owners
→ sets deadlines → defines dependencies → team works → updates progress
→ dependencies complete → dependent task becomes available → task
completes → project progress updates
```

## 7. Business Rules

Full table with enforcement locations: **[`docs/business-rules.md`](docs/business-rules.md)**.

## 8. Technology Stack

**Backend**: Java 21, Spring Boot 3, Spring Web/Data JPA/Security,
Hibernate, Jakarta Bean Validation, Maven, Flyway, JUnit 5, Mockito.
**Database**: PostgreSQL. **Frontend**: React 18, Vite, React Router,
Axios, hand-written CSS design system. **Auth**: JWT.

No Kafka, Kubernetes, Redis, microservices, or WebSockets — see
`DEVELOPMENT_DECISIONS.md` §20–21 for why.

## 9. Architecture

**[`docs/architecture.md`](docs/architecture.md)**

## 10. Project Structure

```
teamflow/
├── backend/            Spring Boot API (Maven)
│   ├── src/main/java/com/teamflow/{controller,service,repository,entity,dto,exception,security,config}
│   ├── src/main/resources/{application.yml, db/migration/*.sql}
│   ├── src/test/java/com/teamflow/{service,integration}
│   └── .env.example
├── frontend/            React + Vite app
│   ├── src/{pages,components,context,api,styles}
│   └── .env.example
├── docs/                 architecture, business-rules, database-design,
│                          api, security, deployment, ai-usage
├── README.md              (this file)
├── DEVELOPMENT_DECISIONS.md
└── .gitignore
```

## 11. Database Design

**[`docs/database-design.md`](docs/database-design.md)** — includes the
ER diagram, every constraint, and the reasoning behind each one.

## 12. ER Diagram

See `docs/database-design.md` §"ER diagram" for the full ASCII diagram
of `User → Project → ProjectMember → Task → TaskDependency`.

## 13. API Documentation

**[`docs/api.md`](docs/api.md)** — every endpoint, required role, and
the shared error response shape.

## 14. Authentication

JWT issued on login, validated on every subsequent request. Details:
`docs/security.md` §Authentication.

## 15. Authorization

Role-based and resource-based, enforced entirely server-side. Details:
`docs/security.md` §Authorization.

## 16. Security

**[`docs/security.md`](docs/security.md)**

## 17. Configuration

All environment-specific values are environment variables — never
hard-coded. See `backend/.env.example` and `frontend/.env.example`.

## 18. Environment Variables

**Backend** (`backend/.env.example`): `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `SERVER_PORT`,
`FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `LOG_LEVEL`.
**Frontend** (`frontend/.env.example`): `VITE_API_BASE_URL`.

## 19. Local Setup

1. Install PostgreSQL 14+ locally and create a `teamflow` database/user
   matching `backend/.env.example` (or point the same env vars at an
   existing PostgreSQL instance — nothing here requires a container
   runtime).
2. `cd backend && cp .env.example .env` (export the variables) `&& mvn spring-boot:run`
   — Flyway migrates the schema (and optional demo data) automatically.
3. `cd frontend && cp .env.example .env && npm install && npm run dev`
   — opens on `http://localhost:5173`.
4. Log in with a demo account from `backend/README.md` (e.g.
   `pm@teamflow.dev` / `password`), or create your own via
   `POST /api/users` (requires an existing manager's token).

## 20. Production Setup

See **[`docs/deployment.md`](docs/deployment.md)** for the full backend
and frontend deployment steps and the local→production configuration
table.

## 21. Deployment

Backend: build the jar (`mvn -DskipTests clean package`), deploy it to
your target host/PaaS with the production environment variables set,
verify `/actuator/health`. Frontend: `npm run build` with
`VITE_API_BASE_URL` pointed at the deployed API, deploy the static
`dist/` output with SPA fallback routing. Full checklist in
`docs/deployment.md`.

## 22. Testing

```bash
cd backend && mvn test     # unit + MockMvc integration tests (H2)
cd frontend && npm run build   # type/JSX correctness via the production build
```

Test coverage: business-rule unit tests (`TaskServiceTest`,
`ProjectServiceTest`, `UserServiceTest`), and end-to-end integration
tests covering authentication, authorization, and business-rule
rejection over real HTTP requests (`AuthenticationIntegrationTest`,
`BusinessRulesIntegrationTest`, `UserManagementIntegrationTest`).

> **Verification note**: the frontend build above was actually run in the
> environment that generated this repository and completed with no
> errors (109 modules, ~2s build). The backend's Maven build could
> **not** be executed there (no Maven Central network access in that
> sandbox) — run `mvn test` yourself before relying on it. See
> `docs/ai-usage.md` for the full disclosure.

## 23. Failure Scenarios

Considered explicitly and either tested or documented:
database unavailable, missing environment variable (fails fast at
startup for `JWT_SECRET`), invalid/expired JWT, unauthorized request,
resource not found, invalid task transition, invalid date range, invalid
progress, circular dependency, duplicate project membership, backend
unavailable from the frontend, wrong `VITE_API_BASE_URL`, CORS
misconfiguration, concurrent task update (optimistic locking). See
`docs/deployment.md` and `DEVELOPMENT_DECISIONS.md`.

## 24. Known Limitations

- Project progress is task-count-based, not effort-weighted (documented
  MVP limitation — see `DEVELOPMENT_DECISIONS.md` §23).
- No notifications/email, calendar integration, file attachments, or
  advanced audit history (explicitly post-MVP, per scope control).
- The backend build was not executed in the generating environment (see
  §22 above and `docs/ai-usage.md`) — verify with `mvn test` before
  production use.

## 25. Future Improvements

Weighted/effort-based progress, notifications, calendar integration,
file attachments, advanced audit history, advanced analytics/reporting,
more granular per-project permissions, a CI pipeline running the test
suites automatically. Full list: `DEVELOPMENT_DECISIONS.md` §32.

## 26. AI Usage

**[`docs/ai-usage.md`](docs/ai-usage.md)** — what was AI-generated,
what was reviewed, and what could not be verified by execution.

## 27. Design Decisions

**[`DEVELOPMENT_DECISIONS.md`](DEVELOPMENT_DECISIONS.md)** — the full
rationale for every major technology, architecture, and business-rule
decision in this repository.

## 28. Screenshots / Demo

No screenshots are included in this repository (generated in a
non-interactive environment without a browser to capture them from).
The demonstration scenario below can be run locally to see every screen
and flow in action.

### Demonstration scenario

Matches the seeded demo data (`backend/.env` + default `mvn spring-boot:run`
loads `V2__seed_demo_data.sql`):

1. Log in as `pm@teamflow.dev` (Project Manager).
2. View the seeded **E-Commerce Platform** project and its dashboard.
3. Open the task board — see the `Database Design → Backend API →
   Frontend → Integration Testing → Deployment` dependency chain.
4. Try moving **Frontend** to `IN_PROGRESS` before **Backend API** is
   completed → rejected with a clear `BUSINESS_RULE_VIOLATION` message.
5. Complete **Backend API** (progress to 100%, status to `COMPLETED`),
   then retry starting **Frontend** → now allowed.
6. Log out, log in as `backend@teamflow.dev` (Team Member).
7. Confirm you cannot see "New project"/"New task" controls, and that
   calling a manager-only endpoint directly returns `403 FORBIDDEN`.
8. Visit **Overdue** to see any task whose due date has passed.

## 29. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Backend fails to start with an `IllegalStateException` about `JWT_SECRET` | `JWT_SECRET` env var missing or shorter than 32 bytes |
| Frontend shows "Unable to load…" everywhere | `VITE_API_BASE_URL` wrong, backend not running, or CORS origin mismatch |
| `403 FORBIDDEN` on an action you expect to work | Check the account's role and project membership — authorization is real, not cosmetic |
| Flyway fails on startup | A migration file was edited after it already ran somewhere — add a new migration instead of editing an applied one |
| Login always returns 401 | Confirm the seeded/created user is `active = true` and the password matches |
