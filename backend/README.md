# TeamFlow Backend

Spring Boot 3 / Java 21 REST API for the TeamFlow Project & Task
Management System. See the root [README.md](../README.md) for the full
project overview; this file covers backend-specific setup only.

## Prerequisites

- JDK 21
- Maven 3.9+ (or use the included `./mvnw` wrapper if present in your
  environment)
- PostgreSQL 14+ running locally (no container runtime required)

## Configuration

All environment-specific values are read from environment variables (see
`.env.example`). Copy it and fill in real values:

```bash
cp .env.example .env
# then export the variables, or use a tool like direnv / your IDE's run config
```

`JWT_SECRET` must be at least 32 bytes; the app fails fast at startup if
it is missing or too short, rather than silently running with a weak key.

## Running locally

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080` by default (`SERVER_PORT`).
Flyway runs the migrations in `src/main/resources/db/migration`
automatically on startup, including an optional demo-data seed
(`V2__seed_demo_data.sql`) that creates the "E-Commerce Platform" demo
project described in the root README's demonstration scenario, with
users:

| Email                  | Password       | Role             |
|-------------------------|----------------|------------------|
| pm@teamflow.dev         | Password123!   | PROJECT_MANAGER  |
| backend@teamflow.dev    | Password123!   | TEAM_MEMBER      |
| frontend@teamflow.dev   | Password123!   | TEAM_MEMBER      |
| qa@teamflow.dev         | Password123!   | TEAM_MEMBER      |

Do not rely on `V2__seed_demo_data.sql` in a real production deployment;
disable/remove it or gate it behind a profile before going live.

## Tests

```bash
mvn test
```

Unit tests (Mockito) cover business rules in `TaskService` and
`ProjectService`. Integration tests (`@SpringBootTest` + MockMvc) run
against an in-memory H2 database (see `src/test/resources/application-test.yml`)
and exercise authentication, authorization, and end-to-end business-rule
rejection over real HTTP requests.

> This backend was generated in a sandboxed environment without access to
> Maven Central, so `mvn compile` / `mvn test` could not actually be
> executed there. The code was written and reviewed carefully (structure,
> imports, and brace-balance were checked programmatically), but you
> should run `mvn test` yourself on first checkout. See
> `../DEVELOPMENT_DECISIONS.md` for the full verification note.

## Health check

`GET /actuator/health` — returns UP/DOWN without leaking internal details
(`management.endpoint.health.show-details: never`).

## API documentation

See [`../docs/api.md`](../docs/api.md).
