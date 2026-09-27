# Database Design

PostgreSQL, managed exclusively through Flyway migrations
(`backend/src/main/resources/db/migration`). `spring.jpa.hibernate.ddl-auto`
is set to `validate` — Hibernate never creates or alters the schema; it
only checks the entity mapping still matches what Flyway produced. This
is what makes the local → production path safe: the exact same
migration files run everywhere.

## ER diagram

```
 USERS                     PROJECTS                  PROJECT_MEMBERS
 ───────────────           ───────────────            ───────────────
 id (PK)            ◄───┐  id (PK)            ◄────┐  id (PK)
 name                    │  name                     │  project_id (FK)──┐
 email (UNIQUE)          │  description               │  user_id (FK) ───┼──┐
 password_hash           │  start_date                │  role             │  │
 role                     │  end_date                 │  joined_at        │  │
 active                    └──manager_id (FK)          UNIQUE(project_id, │  │
 created_at, updated_at   status                        user_id)          │  │
                          version                                          │  │
                          created_at, updated_at                           │  │
                                │                                          │  │
                                │ 1:N                                      │  │
                                ▼                                          │  │
                            TASKS                                          │  │
                          ───────────────                                  │  │
                          id (PK)                                          │  │
                          title, description                               │  │
                          project_id (FK) ────────────────────────────────-┘  │
                          owner_id (FK, nullable) ────────────────────────────┘
                          status, priority
                          start_date, due_date
                          progress
                          version
                          created_at, updated_at
                                │
                                │ 1:N (self-referencing via a join table)
                                ▼
                        TASK_DEPENDENCIES
                       ───────────────────
                       id (PK)
                       task_id (FK -> tasks.id)
                       depends_on_task_id (FK -> tasks.id)
                       created_at
                       UNIQUE(task_id, depends_on_task_id)
                       CHECK(task_id <> depends_on_task_id)
```

## Constraints implemented

- **Primary keys**: `BIGSERIAL` on every table.
- **Foreign keys**: `projects.manager_id -> users.id`,
  `project_members.project_id -> projects.id` (cascade delete),
  `project_members.user_id -> users.id`,
  `tasks.project_id -> projects.id` (cascade delete),
  `tasks.owner_id -> users.id`,
  `task_dependencies.task_id / depends_on_task_id -> tasks.id` (cascade delete).
- **Unique constraints**:
  - `users.email`
  - `(project_members.project_id, project_members.user_id)` — the
    database-level guarantee that a user cannot be added to a project twice,
    even under a race between two concurrent "add member" requests.
  - `(task_dependencies.task_id, task_dependencies.depends_on_task_id)` —
    duplicate dependencies are impossible even if the service-layer check
    is ever bypassed.
- **Check constraints** (a deliberate second line of defense behind the
  service-layer validation, per the project's "don't rely exclusively on
  application-level validation" requirement):
  - `projects`: `start_date <= end_date`
  - `tasks`: `start_date <= due_date` (when both are present),
    `progress BETWEEN 0 AND 100`, `status <> 'COMPLETED' OR progress = 100`
  - `task_dependencies`: `task_id <> depends_on_task_id`
- **NOT NULL** on every field that the domain requires to always be
  present (see `V1__init_schema.sql`).
- **Indexes**: on every foreign key column that is queried directly
  (`projects.manager_id`, `projects.status`, `tasks.project_id`,
  `tasks.owner_id`, `tasks.status`, `tasks.due_date` for overdue
  lookups, `project_members.user_id`, and both columns on
  `task_dependencies`), to keep the dashboard and board queries fast as
  data grows.

## Optimistic locking

`projects` and `tasks` carry a `@Version` column. If User A loads a
task, User B updates it, and User A then submits a stale update, JPA
raises `OptimisticLockException` rather than silently overwriting User
B's change (see `DEVELOPMENT_DECISIONS.md` for the reasoning — this was
judged the right amount of concurrency protection for this MVP, without
introducing distributed locking or a queueing layer that the project
doesn't need).

## Circular dependencies are not a schema-level concept

The database can (and does) prevent a task depending on *itself* via a
`CHECK` constraint, and can prevent a duplicate edge via a `UNIQUE`
constraint — but detecting a *multi-hop* cycle (A→B→C→A) requires
walking the graph, which SQL check constraints cannot express cleanly.
That validation is therefore a service-layer responsibility
(`TaskService.createsCycle`), covered by both unit and integration
tests.
