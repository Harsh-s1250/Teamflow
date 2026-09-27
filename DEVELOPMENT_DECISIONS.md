# TeamFlow — Development Decisions

## 1. Purpose

This document explains the important architectural, technology,
database, security and development decisions made while building
TeamFlow. It exists to explain **why** the application was designed
this way, not to restate what the code already shows.

## 2. Project Goal

Organizations need a centralized system for managing projects, members,
tasks, deadlines, dependencies and progress, while preventing common
project-management failures: unclear ownership, missed deadlines,
invalid assignments, tasks starting before prerequisites are done, and
unauthorized access. TeamFlow's architecture (layered backend,
relational schema with real constraints, backend-enforced authorization)
is aimed squarely at making those failure modes structurally hard to
hit, not just discouraged by UI copy.

## 3. Why Java

Java was used for the backend because it offers strong static typing
(catching a whole class of bugs at compile time rather than at
runtime), a mature object-oriented ecosystem well suited to modeling a
domain like this one (User/Project/Task/Dependency), and long-standing
enterprise adoption that translates into predictable long-term
maintainability — a relevant property for a business system meant to
outlive its first developer.

## 4. Why Spring Boot

Spring Boot provides dependency injection, first-class REST support,
Spring Data JPA for the persistence layer, Spring Security for
authentication/authorization, Bean Validation integration, and a
production-ready ecosystem (Actuator, externalized configuration). It
let the project stay focused on business logic instead of infrastructure
plumbing.

## 5. Why PostgreSQL

The data model is strongly relational:

```
User → Project → ProjectMember → Task → TaskDependency
```

The project needs real foreign keys, referential integrity, unique
constraints (e.g. one membership per user per project), transactions,
and reasonably complex aggregate queries (dashboard counts, progress
calculation). PostgreSQL is a strong, production-grade fit for all of
that. MySQL would also be technically capable — this is a
project-specific fit decision, not a claim that PostgreSQL is
universally better.

## 6. Why React

The frontend needs a dashboard, project/task list and detail views, a
Kanban-style board, forms, and filters — all dynamic, API-driven UI. A
component-based library with a large ecosystem and fast dev loop (Vite)
fits that well, and keeps the frontend maintainable as screens are added.

## 7. Why REST

REST gives a clean, well-understood contract between the React frontend
and the backend, keeps the backend reusable by other clients (Postman,
a future mobile app), and maps naturally onto the resource-oriented
domain (projects, tasks, members, dependencies).

## 8. Why Layered Architecture

```
Controller → Service → Repository → Database
```

Controllers handle HTTP concerns only; services own business rules and
authorization; repositories own persistence. This separation is what
makes the business rules in `docs/business-rules.md` testable in
isolation (see `TaskServiceTest`) without spinning up the web layer, and
keeps a rule's enforcement in exactly one place regardless of which
endpoint triggers it.

## 9. Why DTOs

JPA entities are never returned directly from a controller. DTOs give an
explicit, versionable API contract, prevent accidental exposure of
fields like `passwordHash`, avoid serializing lazy-loaded associations
by accident, and let the database schema evolve somewhat independently
of the API shape.

## 10. Why PostgreSQL Constraints (not just Java validation)

Application-level checks (e.g. "does this membership already exist?")
can race: two concurrent requests can both pass the check before either
has committed. A database `UNIQUE(project_id, user_id)` constraint is
the actual backstop. The same reasoning applies to
`chk_tasks_completed_progress` and `chk_dep_not_self` — cheap,
unconditional guarantees that hold even if a future code path forgets
the service-layer check.

## 11. Why Flyway

Schema changes are versioned, ordered, and reproducible
(`V1__init_schema.sql`, `V2__seed_demo_data.sql`, and so on). This is
what lets `ddl-auto: validate` be safe — the schema Hibernate validates
against was always produced by an explicit, reviewable SQL file, not by
Hibernate's own best guess, which removes an entire category of
"works on my machine" schema drift between developers and environments.

## 12. Why JWT

JWT gives stateless authentication appropriate for a REST API: no
server-side session store, easy to scale horizontally. The token
carries the user's id and role as claims; `JwtService` signs it with a
secret sourced only from configuration and validates signature +
expiry on every request via `JwtAuthenticationFilter`.

## 13. Why Backend Authorization (not hidden UI buttons)

Hiding a "Delete project" button in React does not stop someone from
calling `DELETE /api/projects/7` directly. Every authorization decision
in this codebase — role checks, project-membership checks, task
ownership checks — happens in the service layer, sourced from the
validated JWT via `CurrentUser`, never from a client-supplied field.

## 14. Why Environment Variables

Database credentials, the JWT secret, the frontend origin, and the
server port are all environment variables (see `backend/.env.example`).
The target principle: **environment changes configuration, not
application source code.** This is what lets the exact same jar/build
artifact move from a developer's laptop to a test environment to
production without a single source-code change.

## 15. Why Configurable CORS

`CORS_ALLOWED_ORIGINS` is read from configuration rather than
hard-coded, and is never a wildcard (`SecurityConfig`/`CorsConfig`).
Development and production have different frontend origins, and a
wildcard in production would allow any website to make authenticated
requests using a user's token if it were ever exposed to client-side
JavaScript elsewhere.

## 16. Why Business Logic Is in the Service Layer

Rules like dependency validation, ownership checks, and status
transitions must apply identically no matter which client calls the
API. Putting them in services (rather than controllers, or worse, the
frontend) is what guarantees that.

## 17. Why Centralized Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) is the single place
that turns an exception into an HTTP response. This keeps controllers
free of repetitive try/catch blocks and guarantees every error response
has the same shape and never leaks a stack trace, SQL, or an internal
class name.

## 18. Why Security Is Backend-Enforced

Summarized by one principle used throughout: **never trust the client.**
Identity, role, ownership, and resource access are all re-derived
server-side on every request. See `docs/security.md` for the full list
of checks.

## 19. Why the UI Is Medium Complexity

The assessment's priority order (per the master prompt) is logic,
security, architecture, database correctness, testing, production
readiness, UX, then visual polish — in that order. The UI is deliberately
a clean, professional internal tool (sidebar nav, tables, a Kanban board,
badges, loading/empty/error states) rather than a heavily designed
product, so development time went into the seven higher-priority items.

## 20. Why We Avoided Microservices

Five entities and one cohesive business workflow do not need independent
scaling, independent deployment, or a message bus. A modular monolith
(distinct packages, no cross-cutting spaghetti) gets the maintainability
benefits of separation without the operational cost of a distributed
system this project's scale does not justify.

## 21. Why We Avoided Kafka/Redis/Kubernetes

Each is a real solution to a real problem — but not a problem this MVP
has. There is no high-throughput event stream (Kafka), no need for a
distributed cache or pub/sub layer beyond what a single Postgres
instance handles fine at this scale (Redis), and no need for
multi-service orchestration (Kubernetes) when there is one deployable
backend and one static frontend. Adding them would be complexity in
search of a justification.

## 22. Why Overdue Is Calculated Dynamically

`currentDate > dueDate AND status != COMPLETED`, computed at read time
rather than stored as a column. A stored `OVERDUE` status would need a
scheduled job to keep it accurate as days pass, and could silently go
stale between job runs. A dynamic check is always correct at the moment
it's read.

## 23. Why Task Progress Is Initially Task-Based

```
completedTasks / totalTasks * 100
```

Simple and easy to reason about. The documented limitation: a five-minute
task and a five-day task both count as "one task," so this does not
represent actual effort. A future version could weight by story
points/estimated hours (see §31).

## 24. Why Task Dependencies Exist

Real projects have prerequisites — you cannot meaningfully start
"Frontend" before "Backend API" exists. Without an enforced dependency
model, "IN_PROGRESS" is just a label a team member can set at any time,
regardless of whether the work is actually unblocked. TeamFlow makes
that impossible for the exact chain the manager defines.

## 25. Why Circular Dependencies Are Prevented

If Task A depends on Task B and Task B depends on Task A, neither can
ever legally reach `IN_PROGRESS` — the workflow deadlocks. Rejecting the
edge that would create the cycle, at creation time, is far cheaper than
detecting and explaining a deadlocked project later.

## 26. Why Testing Includes Failure Scenarios

A production system is judged by how it behaves when used incorrectly,
not only when used correctly. `TaskServiceTest`/`ProjectServiceTest`
and the MockMvc integration tests deliberately cover invalid dates,
duplicate membership, incomplete-dependency starts, circular
dependencies, missing/invalid tokens, and role-mismatched requests —
not only the happy path.

## 27. Why Production Configuration Is Part of Development

Deployment was designed in from the start (externalized config, no
`localhost` in source, Flyway-managed schema) rather than treated as an
afterthought once the "real" work was done. See `docs/deployment.md`
for the concrete local→production checklist this produces.

## 28. Security Decisions (summary)

BCrypt password hashing · stateless JWT with a fail-fast secret check ·
service-layer authorization sourced from the security context, never the
request body · DTOs isolating the API contract from entities · Bean
Validation + service-layer business validation · configurable,
non-wildcard CORS · secrets only via environment variables, never
committed · sanitized error responses (no stack traces/SQL) · no
password/JWT/secret logging · database constraints as a second line of
defense. Full detail in `docs/security.md`.

## 29. Code Comment Strategy

Comments explain **why**, not what. A comment like `// Save task` next to
`taskRepository.save(task)` adds nothing; a comment explaining why
ownership is validated in the service layer rather than only the
controller explains an actual decision. Comments in this codebase are
concentrated around business rules, security decisions, database
integrity decisions, and non-obvious validation — not sprinkled on every
line.

## 30. AI-Assisted Development

See `docs/ai-usage.md` for the full breakdown of what was AI-generated,
what was reviewed, and — importantly — what could **not** be verified by
execution in the generating environment (Maven Central was not reachable,
so the backend was never actually compiled there; the frontend *was*
installed and built successfully). That distinction is stated explicitly
rather than glossed over.

## 31. Known Trade-offs / Limitations

- **Task-count progress** is simple but not effort-aware (§23).
- **JWT** is well suited to this REST API but requires careful secret
  management and has no built-in server-side revocation before
  expiry — acceptable for this MVP's token lifetime, worth revisiting
  if a "log out everywhere" feature is ever required.
- **Modular monolith** is simpler to build, test, and deploy than
  microservices, at the cost of independent scaling of its parts —
  not a real constraint at this project's scale.
- **Optimistic locking** (a `@Version` column) protects `projects` and
  `tasks` from silently-overwritten concurrent edits without the
  complexity of pessimistic/distributed locking.
- **The backend was never actually compiled in the generating
  environment** (see §30) — treat it as reviewed-but-unverified code
  until `mvn test` has actually been run.

## 32. Future Improvements

Weighted/effort-based project progress · notifications/email ·
calendar integration · file attachments · advanced audit history ·
advanced analytics/reporting · more granular per-project permissions ·
a CI pipeline that actually runs `mvn test` and `npm run build` on every
change (not possible to wire up inside this generation environment, but
a natural next step for a real repository).

## 33. User Management Decision

The original Project & Task Management problem statement does not, on
its own, demand a full user-management subsystem — but it does demand
that projects have members and tasks have owners, and those people have
to come from *somewhere*. Manager-controlled user creation was added to
close that gap:

- **It completes the user lifecycle.** Without it, the only way a new
  Team Member could exist is by someone manually inserting a row into
  the database — which is not a realistic operational story for a
  system meant to be deployed and used by a real team.
- **It provides a source for project members.** `ProjectMember` and task
  ownership already required an active `User` to point to; this feature
  is what actually produces those `User` rows through the API instead of
  requiring direct database access.
- **It prevents reliance on manually seeded users.** The demo data in
  `V2__seed_demo_data.sql` is convenient for local development, but a
  real deployment needs a supported way to onboard real people — this
  feature is that way.
- **It remains intentionally lightweight.** No separate IAM service, no
  email-invitation flow, no email verification, no SSO/LDAP/OAuth, no
  password-reset platform, no multi-tenant identity system, no
  standalone permission engine. A manager creates a user with a name,
  email, role, and a temporary password; that's the entire surface
  area. Anything beyond that is out of scope for this MVP by design
  (see §20/§21 for the same reasoning applied to the rest of the stack).
- **Public self-registration was intentionally avoided.** This is an
  internal business tool with two roles and manager-controlled
  membership throughout (project membership already worked this way);
  letting anyone register themselves would be inconsistent with that
  model and would require solving problems (email verification, abuse
  prevention, self-assigned roles) this application does not otherwise
  need to solve.

### Why deactivation instead of deletion

Users are never hard-deleted. By the time a user account matters enough
to remove, it likely already has historical value tied to it elsewhere
in the schema — `tasks.owner_id`, `project_members.user_id`, timestamps
that describe when work happened. Deleting the row would either cascade
into deleting that history (losing an audit trail of who did what) or
require nulling out foreign keys across multiple tables in a way that
silently corrupts otherwise-correct historical data. Flipping
`active = false` instead:

- Keeps every historical task/membership/timestamp record fully intact.
- Is trivially reversible if the deactivation was a mistake or the
  person returns.
- Is the same mechanism the rest of the application already relies on:
  `UserService.getActiveUserOrThrow` was already being used to block
  inactive users from new project/task assignments before this feature
  existed (for users seeded as inactive); this feature just exposes a
  supported way to *set* that flag through the API instead of only
  reading it.

One additional guard was added beyond the literal spec: a manager cannot
deactivate their own account. Without it, a lone manager could lock
themselves out of user management entirely with no way back in short of
direct database access — a straightforward failure mode worth
preventing for negligible added complexity.
