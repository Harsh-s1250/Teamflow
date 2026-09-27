# Business Rules

Every rule below is enforced **server-side**, in the service layer
(and, where noted, also at the database level as a second line of
defense). The frontend may also validate these for a better user
experience, but the frontend is never the source of truth.

| # | Rule | Enforced in |
|---|------|--------------|
| 1 | Project `startDate <= endDate` | `ProjectService.validateDateRange`, DB check `chk_projects_dates` |
| 2 | Task `startDate <= dueDate` | `TaskService.validateDateRange`, DB check `chk_tasks_dates` |
| 3 | Task owner must belong to the project | `TaskService.validateOwnerBelongsToProject` |
| 4 | Task progress must be 0–100 | `@Min`/`@Max` on `TaskProgressUpdateRequest`, service-layer check, DB check `chk_tasks_progress` |
| 5 | `COMPLETED` task must have 100% progress | `TaskService.updateStatus`, DB check `chk_tasks_completed_progress` |
| 6 | A task with incomplete dependencies cannot move to `IN_PROGRESS` | `TaskService.updateStatus` → `incompleteDependencies()` |
| 7 | A task cannot depend on itself | `TaskService.addDependency`, DB check `chk_dep_not_self` |
| 8 | Circular dependencies are rejected | `TaskService.createsCycle` (BFS over the dependency graph) |
| 9 | Duplicate dependencies are rejected | `TaskService.addDependency`, DB unique constraint `uk_dep_task_dependson` |
| 10 | Cancelled project cannot receive new tasks | `TaskService.create` |
| 11 | Completed project cannot normally receive new tasks | `TaskService.create` |
| 12 | Inactive users cannot receive assignments | `UserService.getActiveUserOrThrow`, used by both task-owner and project-member flows |
| 13 | Unauthorized users cannot modify resources | `ProjectService.requireManagerOfProject`, `TaskService.requireWriteAccess` |
| 14 | Manager-only operations require manager authorization | Same as above, plus role check at creation |
| 15 | Invalid status transitions are rejected | `TaskService.ALLOWED_TRANSITIONS` whitelist |
| 16 | Overdue tasks are identified dynamically | `Task.isOverdue()`, never a stored column |
| 17 | A user cannot be added twice to the same project | `ProjectService.addMember`, DB unique constraint `uk_project_members_project_user` |

## Status transition model

```
TODO ──────► IN_PROGRESS ──────► COMPLETED
  ▲               │  ▲
  │               ▼  │
  └───────────  BLOCKED
```

Allowed transitions (see `TaskService.ALLOWED_TRANSITIONS`):

- `TODO` → `IN_PROGRESS`, `BLOCKED`
- `IN_PROGRESS` → `BLOCKED`, `COMPLETED`, `TODO`
- `BLOCKED` → `TODO`, `IN_PROGRESS`
- `COMPLETED` → *(terminal — no further transitions)*

Moving into `IN_PROGRESS` additionally requires all of the task's
dependencies to be `COMPLETED` (rule 6). Moving into `COMPLETED`
additionally requires `progress == 100` (rule 5).

## Overdue detection

A task is overdue when:

```
today > dueDate  AND  status != COMPLETED
```

This is **never persisted**. It's computed at read time
(`Task.isOverdue(LocalDate today)`), both for the `/api/tasks/overdue`
endpoint and for the dashboard counts. Storing it as a column would
require a background job to keep it in sync with the passage of time and
could silently drift out of date; computing it on read cannot drift.

## Project progress (MVP formula)

```
progress = completedTasks / totalTasks * 100
```

**Known limitation**: this treats every task as equally "worth" the
same amount of progress. A 5-minute task and a 5-day task both count as
one task. See `DEVELOPMENT_DECISIONS.md` §22 for the documented
trade-off and the suggested future improvement (weighted/effort-based
progress).

## Circular dependency prevention

Before saving a new dependency edge `task -> dependsOn`, the service
runs a breadth-first search starting at `dependsOn`: if that search can
reach `task` by following existing `dependsOn` edges, adding the new
edge would close a cycle, and the request is rejected with
`BUSINESS_RULE_VIOLATION`. See `TaskService.createsCycle`.
