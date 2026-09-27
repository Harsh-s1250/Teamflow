# Security

## Authentication

- JWT, issued on `POST /api/auth/login` after Spring Security validates
  the credentials against a BCrypt hash (`ApplicationConfig.passwordEncoder`).
- The signing secret (`JWT_SECRET`) is read only from configuration/environment.
  `JwtService` fails fast at startup if it is missing or shorter than 32
  bytes — the app will not start with a weak or absent secret.
- Tokens are stateless (`SessionCreationPolicy.STATELESS`); no server-side
  session store is needed.
- Login returns the same generic "Invalid email or password" message
  whether the email doesn't exist or the password is wrong, to avoid
  leaking which emails are registered.
- Inactive users cannot authenticate: `AppUserPrincipal.isEnabled()`
  returns the user's `active` flag, and Spring Security's account-status
  checks reject disabled accounts during authentication.

## Authorization

- **Never trust the client for identity.** `CurrentUser` reads the
  authenticated principal from the Spring Security context (populated by
  `JwtAuthenticationFilter` from a *validated* token), not from any field
  in the request body. A request body containing `"userId": 15` is
  never used to decide who is acting.
- **Role checks**: manager-only operations (creating projects/tasks,
  managing membership, reassigning owners, defining dependencies) call
  `requireManagerOfProject(...)`/an equivalent guard before doing
  anything, in the service layer — not hidden by disabling a button in
  the UI.
- **Resource-level checks (IDOR prevention)**: reading a project or task
  requires the caller to be a member of *that* project or its manager
  (`ProjectService.requireMembershipOrManager`). Changing `projectId=123`
  to `projectId=124` in a URL does not grant access to project 124's data
  — the server re-checks membership for the id actually requested, every
  time.
- **Ownership checks**: a team member can update the status/progress of
  a task only if they are its owner or the project's manager
  (`TaskService.requireWriteAccess`).

## Input validation

- Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Min`, `@Max`, `@Size`,
  `@NotNull`) on every request DTO.
- Cross-field/business rules that annotations can't express (date
  ranges, dependency cycles, status transitions) are validated in the
  service layer — see `docs/business-rules.md`.
- Bean Validation failures return `400 VALIDATION_ERROR` with a
  human-readable, field-level message, never a stack trace.

## Secrets and configuration

- No secret (`JWT_SECRET`, `DB_PASSWORD`) is hard-coded anywhere in the
  source tree. All are environment variables (see `backend/.env.example`).
- `.env` is git-ignored; only `.env.example` (placeholders) is committed.
- CORS origins are configurable (`CORS_ALLOWED_ORIGINS`/`FRONTEND_URL`)
  and never wildcarded (`SecurityConfig`/`CorsConfig`).

## Error handling / information disclosure

- `GlobalExceptionHandler` is the only place that formats error
  responses. It never includes stack traces, SQL, or internal class
  names in a response body (`server.error.include-*: never` in
  `application.yml` is a second layer of the same guarantee at the
  framework level).
- Unexpected exceptions are logged server-side with full detail but
  returned to the client as a generic `500 INTERNAL_ERROR` message.

## Logging

- Passwords, JWTs, and Authorization headers are never logged.
- `LOG_LEVEL` is configurable per environment; production should default
  to `INFO` or higher, not `DEBUG`.

## Dependency hygiene

- Dependencies are declared with explicit versions in `pom.xml` and
  `package.json`; there is no reliance on unpinned "latest" ranges for
  security-relevant libraries (Spring Security, jjwt, BCrypt).
- `npm audit` should be run periodically against `frontend/package-lock.json`
  in CI (not wired up here, since a CI pipeline was out of scope for this
  assessment, but it's a natural next step — see "Future Improvements").
