# Architecture

## Layers

```
Controller  ->  handles HTTP concerns, maps DTO <-> service call
    |
Service     ->  business rules, authorization checks, transactions
    |
Repository  ->  Spring Data JPA, persistence only
    |
PostgreSQL
```

Controllers never contain business logic — they validate the request
shape (Bean Validation), delegate to a service, and translate the
service's return value into an HTTP response. Every business rule in
`docs/business-rules.md` is enforced in a service class, not a
controller, so the same rule applies no matter which client calls the
API (the React app, Postman, a future mobile client, etc.).

## Package layout

```
com.teamflow
├── controller   REST endpoints (thin)
├── service      Business logic, authorization, transactions
├── repository   Spring Data JPA interfaces
├── entity       JPA-mapped domain objects
├── dto          Request/response records, grouped by feature
├── exception    Domain exceptions + GlobalExceptionHandler
├── security     JWT issuing/validation, Spring Security wiring
└── config       CORS, application-level beans
```

## Identity and authorization flow

1. `POST /api/auth/login` authenticates via Spring Security's
   `AuthenticationManager` (BCrypt password check) and returns a signed JWT.
2. Every subsequent request carries `Authorization: Bearer <token>`.
3. `JwtAuthenticationFilter` validates the token and, if valid, populates
   the Spring Security context with an `AppUserPrincipal`.
4. Services never read identity from the request body. `CurrentUser`
   (`service/CurrentUser.java`) is the single place that reads "who is
   calling" from the security context, which is what prevents a client
   from spoofing another user's id or role.
5. Authorization decisions (role checks, project-membership checks,
   task-ownership checks) live in `ProjectService` and `TaskService` next
   to the data they protect, not scattered across controllers.

## Why this shape

- **Thin controllers / fat services**: keeps HTTP concerns (status codes,
  path variables) separate from business rules, so the rules are testable
  without spinning up the web layer (see `TaskServiceTest`,
  `ProjectServiceTest`).
- **DTOs everywhere**: no JPA entity is ever serialized directly to JSON.
  This avoids accidentally leaking `passwordHash`, avoids Hibernate lazy-
  loading surprises during serialization, and lets the API contract
  evolve independently of the database schema.
- **A modular monolith, not microservices**: the domain (5 entities, one
  cohesive workflow) does not need independent scaling or independent
  deployment of its parts. Splitting it up would add network calls,
  distributed transactions, and operational overhead without solving a
  real problem this project has.
