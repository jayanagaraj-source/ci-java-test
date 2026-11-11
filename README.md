# Task API

A task management REST API built with Spring Boot 3.5, Java 21 and Gradle (Kotlin DSL).

## Features

- **Stateless JWT auth** (HS256): register, login, `GET /me`. Passwords are hashed with BCrypt.
  The first user to register becomes `ADMIN` and every later user gets `USER`. Login returns the
  same error, and does the same BCrypt work, whether the email is unknown or the password is wrong.
- **Access isolation**: users only see their own tasks. Admins see all tasks and can list users.
  Asking for someone else's task returns `404`, not `403`.
- **Tasks**: title, description, status (`TODO`/`IN_PROGRESS`/`DONE`), priority (`LOW`/`MEDIUM`/`HIGH`),
  tags and dueDate. Input is checked with Bean Validation, and unknown JSON fields are rejected.
- **Listing**: filter by `status`, `priority`, `tag` and `q` (searches title and description).
  Sort with `sort=-priority,dueDate`. Paginate with `page` (starts at 1) and `limit` (max 100).
- **Optimistic locking**: JPA `@Version` is exposed as an `ETag`. A stale `If-Match` returns `412`.
  `PATCH` uses merge-patch semantics: only the fields you send change, and `null` clears a nullable field.
- **Live updates**: `GET /api/v1/tasks/events` streams `task.created`, `task.updated` and
  `task.deleted` over SSE. Each subscriber only gets events for tasks they can see.
- **Errors**: every error is an RFC 9457 Problem Details body (`application/problem+json`) with a
  `requestId`. Internal error messages are never returned.
- **Operations**: request IDs (`X-Request-Id`), structured JSON logs (ECS), CORS allow-list,
  rate limiting on the auth endpoints, a request body size limit, security headers, liveness and
  readiness probes, and graceful shutdown.
- **Persistence**: Spring Data JPA on H2, with the schema managed by Flyway migrations.

Code is organized by feature: `auth`, `users`, `tasks`, plus `common` and `config`. Each feature
has its controller, service, repository and DTOs. Auth has no table of its own and stores users
through the users feature.

## Running

You need JDK 21. Gradle itself comes from the wrapper.

```bash
cp .env.example .env              # then set JWT_SECRET (at least 32 characters)
export $(grep -v '^#' .env | xargs)
./gradlew bootRun
```

Run the tests and full build with `./gradlew build`.

With Docker:

```bash
docker build -t task-api .
docker run --rm -p 8080:8080 --env-file .env task-api
```

All configuration comes from environment variables, listed in [.env.example](.env.example).
Startup fails if `JWT_SECRET` is missing or shorter than 32 characters.

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public (rate limited) | Create an account and return a token |
| POST | `/api/v1/auth/login` | public (rate limited) | Exchange credentials for a token |
| GET | `/api/v1/auth/me` | user | The current user |
| GET | `/api/v1/users?page&limit` | admin | List users |
| GET | `/api/v1/tasks?status&priority&tag&q&sort&page&limit` | user | List visible tasks |
| POST | `/api/v1/tasks` | user | Create a task (`201`, `Location`, `ETag`) |
| GET | `/api/v1/tasks/{id}` | user | Get a task (`ETag`; `If-None-Match` returns `304`) |
| PATCH | `/api/v1/tasks/{id}` | user | Partial update (`If-Match` optional; stale returns `412`) |
| DELETE | `/api/v1/tasks/{id}` | user | Delete a task (`If-Match` optional) |
| GET | `/api/v1/tasks/events` | user | SSE stream of task events |
| GET | `/actuator/health/liveness` | public | Liveness probe |
| GET | `/actuator/health/readiness` | public | Readiness probe |

Send the token as `Authorization: Bearer <token>`. The SSE endpoint needs that header too, so use
a fetch-based SSE client, because the browser's `EventSource` can't set headers.

## Dependency versions and lock state

Plugin versions are pinned in `build.gradle.kts`. Library versions come from the Spring Boot BOM,
and the Gradle distribution is pinned with a SHA-256 checksum in
`gradle/wrapper/gradle-wrapper.properties`. Dependency locking is enabled, but **`gradle.lockfile`
is not committed** (it's in `.gitignore`). To generate it locally:

```bash
./gradlew dependencies --write-locks
```

## Security scan

[`.github/workflows/tigergate.yml`](.github/workflows/tigergate.yml) runs the TigerGate CLI on
pushes and pull requests to `main`, and on manual dispatch. It only scans the checked-out
repository and never builds, compiles or tests the project. Because the lockfile isn't
committed, the workflow sets up a JDK and runs `gradlew dependencies --write-locks` inside the
job. That only resolves the dependency graph, so SCA sees exact transitive versions. The
generated lockfile is never committed or pushed back.

It needs the `TIGERGATE_API_KEY` repository secret. These repository variables are optional:
`TIGERGATE_API_URL`, `TIGERGATE_CLI_IMAGE` and `TIGERGATE_CLI_TAG`.
