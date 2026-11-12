# ClinicBook Backend

Spring Boot 3.5 (Java 21) API for ClinicBook. Schema is owned by Flyway; JPA runs with
`ddl-auto=validate`.

## Prerequisites

- JDK 21
- Maven 3.9+
- MySQL 8 running locally with a database named `clinicbook`

```sql
CREATE DATABASE clinicbook CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## Run locally

```bash
mvn spring-boot:run
```

The `local` profile is the default. It reads `CLINICBOOK_DB_URL`, `CLINICBOOK_DB_USER`,
and `CLINICBOOK_DB_PASSWORD`, falling back to `jdbc:mysql://localhost:3306/clinicbook`,
`clinicbook`, `clinicbook`. Local overrides are namespaced so unrelated `DB_*`
environment variables on the machine cannot break ClinicBook. Production uses `DB_*`
from Phase 7 onward.

Health check: <http://localhost:8080/actuator/health>
Swagger UI: <http://localhost:8080/swagger-ui.html>

## Verify

```bash
mvn -B verify
```

## Auth

Stateless JWT authentication with rotating refresh tokens.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Create a `PATIENT` account; returns tokens |
| POST | `/api/v1/auth/login` | public | Exchange email/password for tokens (rate limited per IP) |
| POST | `/api/v1/auth/refresh` | public | Rotate a refresh token; old token is revoked |
| POST | `/api/v1/auth/logout` | public | Revoke a refresh token (idempotent, `204`) |
| GET | `/api/v1/auth/me` | bearer | Return the authenticated user |

- Access tokens are HMAC-signed (HS512 at the default key size), valid for 15 minutes, and carry
  the user id, email, role and display name.
- Refresh tokens are opaque 256-bit values, valid for 7 days. Only their SHA-256 hash is stored.
  Every refresh rotates the token; a revoked or expired token returns `401`.
- Registration always creates a `PATIENT`. Roles (`PATIENT`, `DOCTOR`, `ADMIN`) are assigned by
  an administrator, never by the caller.
- Passwords are BCrypt hashed and must be 8–72 characters and at most 72 UTF-8 bytes.
- Login is limited to 5 attempts per minute per client IP; the limit returns `429` with
  `Retry-After`. Adjust with `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` and `LOGIN_RATE_LIMIT_WINDOW`.
- Every error uses the standard shape:
  `{ timestamp, status, error, message, path, fieldErrors }`.

## Configuration

| Variable | Default (local) | Purpose |
|---|---|---|
| `CLINICBOOK_DB_URL` | `jdbc:mysql://localhost:3306/clinicbook?...` | JDBC URL |
| `CLINICBOOK_DB_USER` | `clinicbook` | Database user |
| `CLINICBOOK_DB_PASSWORD` | `clinicbook` | Database password |
| `DB_POOL_SIZE` | `10` | Hikari maximum pool size |
| `SERVER_PORT` | `8080` | HTTP port |
| `JWT_SECRET` | dev-only key in `application-local.yml` | HMAC signing key, **at least 32 bytes**. Required outside `local` |
| `JWT_ISSUER` | `clinicbook` | Access-token issuer claim |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Access-token lifetime |
| `JWT_REFRESH_TOKEN_TTL` | `7d` | Refresh-token lifetime |
| `CORS_ORIGIN` | `http://localhost:5173` | Allowed frontend origin(s), comma separated |
| `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` | `5` | Login attempts per window per IP |
| `LOGIN_RATE_LIMIT_WINDOW` | `1m` | Login rate-limit window |

The local JWT key exists only so the `local` profile starts without setup. Never use it in
production; set `JWT_SECRET` to a random value of at least 32 bytes.

Only `health` and `info` Actuator endpoints are exposed. Production profile and
PostgreSQL settings arrive in Phase 7.

## Migrations

Flyway resolves `classpath:db/migration/{vendor}`, so migrations live in
`src/main/resources/db/migration/mysql/` and `.../postgresql/`. Every migration must
exist in both folders with the same filename.

## Package layout

Feature-first (`auth`, `user`, `doctor`, `schedule`, `appointment`, `review`,
`notification`, `admin`). Cross-cutting code goes in `config` and `common`.
