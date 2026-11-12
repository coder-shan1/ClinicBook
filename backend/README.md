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

The suite includes unit tests, controller slices, and real MySQL/PostgreSQL search tests
using Testcontainers. Database tests skip automatically when Docker is not available and
run in CI (Docker is present on GitHub-hosted runners).

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

## Clinic domain

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/v1/specialties` | public | List specialties, ordered by name |
| GET | `/api/v1/doctors` | public | Search active doctors (`specialty`, `q`, `page`, `size`, `sort`) |
| GET | `/api/v1/doctors/{id}` | public | Get one active doctor |
| GET | `/api/v1/doctors/{id}/slots` | public | Available slots for a date (`YYYY-MM-DD`, clinic timezone) |
| GET | `/api/v1/doctors/me` | doctor | Own profile |
| PUT | `/api/v1/doctors/me` | doctor | Update own specialty, bio and fee |
| GET | `/api/v1/doctors/me/working-hours` | doctor | List own working hours |
| PUT | `/api/v1/doctors/me/working-hours` | doctor | Replace working hours and regenerate slots |
| GET | `/api/v1/doctors/me/time-off` | doctor | List own time off |
| POST | `/api/v1/doctors/me/time-off` | doctor | Add time off and regenerate slots |
| DELETE | `/api/v1/doctors/me/time-off/{id}` | doctor | Delete time off and regenerate slots |
| POST | `/api/v1/doctors/me/slots/regenerate` | doctor | Regenerate the rolling slot window now |
| GET | `/api/v1/admin/doctors` | admin | List doctors, including inactive ones |
| POST | `/api/v1/admin/doctors` | admin | Create a `DOCTOR` user and profile |
| GET | `/api/v1/admin/doctors/{id}` | admin | Get a doctor, including inactive ones |
| PUT | `/api/v1/admin/doctors/{id}` | admin | Update a doctor and activate/deactivate |
| DELETE | `/api/v1/admin/doctors/{id}` | admin | Soft-delete: deactivate doctor and disable account |
| POST | `/api/v1/admin/specialties` | admin | Create a specialty |
| PUT | `/api/v1/admin/specialties/{id}` | admin | Rename or update a specialty |
| DELETE | `/api/v1/admin/specialties/{id}` | admin | Delete an unused specialty (`409` if in use) |

- Working hours are wall-clock times in the clinic timezone (`CLINIC_TIME_ZONE`, default
  `Asia/Kolkata`). Slots are always stored and returned as UTC instants.
- Slots are materialised for the next `SLOT_HORIZON_DAYS` (default `30`) days, minus
  time off. Generation is idempotent: existing rows are matched by UTC start, free slots
  are resized when the slot length changes, and only free slots that no longer match the
  schedule are deleted. `BOOKED` and `BLOCKED` slots are never removed.
- A nightly job (`SLOT_NIGHTLY_CRON`, default `0 0 3 * * *` in the clinic timezone)
  regenerates the window so it always rolls forward. Schedule changes and time-off
  edits regenerate immediately.
- Public search matches the doctor name or specialty name, case-insensitively.
  `page` is clamped to `>= 0` and `size` to `1..100` (default `20`); `sort` accepts
  `fullName`, `specialty`, `consultationFee` and `createdAt`, with an optional `,asc`
  or `,desc`.
- Admin delete is a soft delete: the doctor becomes inactive (hidden from public search)
  and the user account is disabled.

### Local seed data

With the default `local` profile, a runner creates six fake specialties and doctors on
first start. The data is idempotent and uses `@example.com` addresses only. Every demo
doctor signs in with password `Doctor123!`. Set `SEED_ENABLED=false` to turn it off;
the runner never loads in tests or production.

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
| `CLINIC_TIME_ZONE` | `Asia/Kolkata` | Zone used to interpret working hours and clinic dates |
| `SLOT_HORIZON_DAYS` | `30` | Days of bookable slots to materialise |
| `SLOT_NIGHTLY_CRON` | `0 0 3 * * *` | Nightly slot-extension schedule (clinic timezone) |
| `SEED_ENABLED` | `true` | Create fake local-profile demo data on startup |

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
