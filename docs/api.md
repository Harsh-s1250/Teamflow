# API Documentation

Base URL: `{VITE_API_BASE_URL}` (e.g. `http://localhost:8080/api` locally).
All endpoints except `POST /api/auth/login` and `GET /actuator/health`
require `Authorization: Bearer <JWT>`.

## Auth

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/login` | none | `{ email, password }` → `{ token, expiresInMs, userId, name, email, role }` |

## Users

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/users` | Manager | Create a user account. Body: `{ name, email, role, temporaryPassword }` |
| GET | `/api/users` | Manager | List users. Optional filters: `?role=`, `?active=`, `?search=` |
| GET | `/api/users/{id}` | Manager | User detail |
| PATCH | `/api/users/{id}/status` | Manager | `{ active }` — activate/deactivate (no hard delete) |

## Projects

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/projects` | Manager | Create a project (creator becomes its manager) |
| GET | `/api/projects` | Any | Paginated list — managers see all projects, team members see only projects they belong to |
| GET | `/api/projects/{id}` | Member or manager of that project | Project detail incl. computed progress |
| PUT | `/api/projects/{id}` | That project's manager | Update name/description/dates/status |
| DELETE | `/api/projects/{id}` | That project's manager | Delete project and its tasks |

### Project members

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/projects/{projectId}/members` | Manager | `{ userId }` — add an active user as a member |
| GET | `/api/projects/{projectId}/members` | Member or manager | List members |
| DELETE | `/api/projects/{projectId}/members/{userId}` | Manager | Remove a member (not the project's manager) |

## Tasks

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/projects/{projectId}/tasks` | Manager | Create a task |
| GET | `/api/projects/{projectId}/tasks` | Member or manager | Paginated list of a project's tasks |
| GET | `/api/tasks/{id}` | Member or manager of the task's project | Task detail |
| PUT | `/api/tasks/{id}` | Manager | Update title/description/dates/owner/priority |
| DELETE | `/api/tasks/{id}` | Manager | Delete task (and its dependency edges) |
| PATCH | `/api/tasks/{id}/status` | Owner or manager | `{ status }` — subject to the transition/dependency rules |
| PATCH | `/api/tasks/{id}/owner` | Manager | `{ ownerId }` — reassign; owner must be an active project member |
| PATCH | `/api/tasks/{id}/progress` | Owner or manager | `{ progress }` (0–100) |
| GET | `/api/tasks/overdue` | Any | Overdue tasks visible to the caller (computed dynamically) |

### Dependencies

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/tasks/{taskId}/dependencies` | Manager | `{ dependsOnTaskId }` |
| GET | `/api/tasks/{taskId}/dependencies` | Member or manager | List a task's dependencies |
| DELETE | `/api/tasks/{taskId}/dependencies/{dependencyId}` | Manager | Remove a dependency |

## Dashboard

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/dashboard` | Any | Project/task counts + overdue/blocked/high-priority lists, scoped to what the caller can see |

## Health

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/actuator/health` | none | `{ "status": "UP" }` — no internal details exposed |

## Error shape

Every error response has this shape:

```json
{
  "timestamp": "2026-09-25T10:15:30Z",
  "status": 400,
  "error": "BUSINESS_RULE_VIOLATION",
  "message": "Task cannot be started because it has incomplete dependencies: Database Design",
  "path": "/api/tasks/15/status"
}
```

| HTTP status | `error` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation failure (missing/malformed field) |
| 400 or 409 | `BUSINESS_RULE_VIOLATION` | A domain rule was violated (409 for "already exists" conflicts) |
| 401 | `UNAUTHORIZED` | Missing/invalid/expired token, or bad login credentials |
| 403 | `FORBIDDEN` | Authenticated, but not permitted to do this |
| 404 | `NOT_FOUND` | Resource doesn't exist (or the caller shouldn't be able to tell) |
| 500 | `INTERNAL_ERROR` | Unexpected server error — no stack trace/SQL is ever returned |

A Postman/Bruno collection is not included as a binary file in this
repository, but every endpoint above maps directly to a request you can
build in either tool: set `Authorization: Bearer {{token}}` after
calling `/api/auth/login`, and the JSON bodies match the DTOs in
`backend/src/main/java/com/teamflow/dto`.
