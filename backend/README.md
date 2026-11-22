# ClinicBook Backend

Spring Boot 3.5 (Java 21) API for ClinicBook. Schema is owned by Flyway; JPA runs with
`ddl-auto=validate`.

## Prerequisites

- JDK 21
- Maven 3.9+
- MySQL 8 running locally with a database named `clinicbook`
  (production uses Neon PostgreSQL; see [Deploy to Render + Neon](#deploy-to-render--neon))

```sql
CREATE DATABASE clinicbook CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## Run locally

```bash
cp .env.sample .env      # then set JWT_SECRET (openssl rand -base64 48)
mvn spring-boot:run
```

Spring imports `.env` automatically when it exists (OS environment variables still win),
so no export step is needed. The `local` profile is the default and reads
`CLINICBOOK_DB_URL`, `CLINICBOOK_DB_USER` and `CLINICBOOK_DB_PASSWORD`. Local overrides are
namespaced so unrelated `DB_*` environment variables on the machine cannot break ClinicBook.
`JWT_SECRET` and `CLINICBOOK_DB_PASSWORD` have no defaults; the app fails fast when they are
missing. Production uses `DB_URL`, `DB_USER` and `DB_PASSWORD` with the `prod` profile.

> Copying `.env.sample` gives you placeholder values for secrets — replace them with real
> values before starting the app.

To run the app locally against Neon instead of MySQL, fill `DB_URL`, `DB_USER` and
`DB_PASSWORD`, then set `SPRING_PROFILES_ACTIVE=prod` in your shell (profile activation
does not come from an imported `.env` file).

Health check: <http://localhost:8080/actuator/health>
Swagger UI: <http://localhost:8080/swagger-ui.html>

## Verify

```bash
mvn -B verify
```

The suite includes unit tests, controller slices, and real MySQL/PostgreSQL tests for
search, booking concurrency, reminder idempotency and review rules using Testcontainers.
Database tests skip automatically when Docker is not available and run in CI (Docker is
present on GitHub-hosted runners).

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
doctor signs in with the password in `DEMO_DOCTOR_PASSWORD`; when that variable is blank,
demo doctors are skipped. Set `SEED_ENABLED=false` to turn it off entirely; the runner
never loads in tests or production.

## Appointments

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/appointments` | patient | Book an available slot; returns `201` and a `PENDING` appointment |
| GET | `/api/v1/appointments/me` | patient, doctor | List own appointments, ordered by start time (`page`, `size`) |
| PATCH | `/api/v1/appointments/{id}/cancel` | patient, doctor, admin | Cancel and release the slot |
| PATCH | `/api/v1/appointments/{id}/confirm` | doctor, admin | `PENDING → CONFIRMED` |
| PATCH | `/api/v1/appointments/{id}/complete` | doctor, admin | `CONFIRMED → COMPLETED` |
| PATCH | `/api/v1/appointments/{id}/no-show` | doctor, admin | `CONFIRMED → NO_SHOW` |

- Lifecycle: `PENDING → CONFIRMED → COMPLETED`; `PENDING`/`CONFIRMED → CANCELLED`;
  `CONFIRMED → NO_SHOW`. `COMPLETED`, `CANCELLED` and `NO_SHOW` are terminal.
- Booking is transactional. The slot is reserved with an optimistic lock on
  `time_slot.version` and a unique index on the active appointment's `slot_id`; if two
  patients race for one slot, exactly one wins and the other gets `409 SLOT_TAKEN`.
  The 20-thread/1-slot integration test runs against both MySQL and PostgreSQL.
- Cancelling returns the slot to `AVAILABLE`, detaches `slot_id` from the appointment and
  keeps the appointment as history.
- Rules: slots in the past are rejected (`400 SLOT_IN_PAST`); patients cannot cancel
  within `BOOKING_CANCEL_CUTOFF` of the start (`409 CANCELLATION_WINDOW_CLOSED`); a patient
  may hold at most `BOOKING_MAX_ACTIVE_PER_PATIENT` active (`PENDING` + `CONFIRMED`)
  appointments (`409 BOOKING_LIMIT_REACHED`).
- Ownership mismatches return `404 APPOINTMENT_NOT_FOUND` rather than revealing another
  user's appointment. Invalid transitions return `409 INVALID_APPOINTMENT_STATE`.
- Doctors only see and manage their own appointments; admins can manage any appointment
  and are exempt from the cancellation cutoff.

## Notifications

Email is best-effort and never blocks an appointment action. Booking, confirmation and
cancellation events are published inside the appointment transaction and delivered
*after* it commits; a broken SMTP server is logged and swallowed, so the booking still
succeeds.

| Event | Email | When |
|---|---|---|
| `POST /api/v1/appointments` | Booking received | Patient books a slot |
| `PATCH /api/v1/appointments/{id}/confirm` | Appointment confirmed | Doctor or admin confirms |
| `PATCH /api/v1/appointments/{id}/cancel` | Appointment cancelled | Patient, doctor or admin cancels |
| Reminder job | Appointment reminder | Confirmed appointment starts within `REMINDER_LEAD` |

- HTML templates live in `src/main/resources/templates/email/` and are rendered with
  Thymeleaf. Times are formatted in the clinic timezone.
- Every delivered email is recorded in `notification_log`. A unique
  `(appointment_id, type)` index makes each email at-most-once per appointment, including
  repeated or overlapping reminder runs.
- The reminder job runs hourly (`REMINDER_CRON`, default `0 0 * * * *` in the clinic
  timezone). It scans `CONFIRMED` appointments starting within `REMINDER_LEAD` (default
  `24h`), locks each appointment, re-checks its status and window, and skips anything
  already logged. One failure is logged and the run continues.
- Local development uses **Mailpit** or **MailHog** on SMTP `localhost:1025` with a web
  inbox at <http://localhost:8025>. Example:

  ```bash
  docker run --rm --name clinicbook-mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
  ```

  Set `NOTIFICATIONS_ENABLED=false` to disable all appointment email (tests do this).

## Reviews

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/reviews` | patient | Review a `COMPLETED` appointment once (`appointmentId`, `rating` 1–5, optional `comment`) |
| GET | `/api/v1/doctors/{doctorId}/reviews` | public | List a doctor's reviews, newest first (`page`, `size`) |

- Only the patient who owns the appointment may review it; other patients get
  `404 APPOINTMENT_NOT_FOUND` so ownership is not revealed.
- A review is accepted only when the appointment is `COMPLETED`
  (`409 APPOINTMENT_NOT_COMPLETED`) and only once (`409 REVIEW_ALREADY_EXISTS`). The
  unique `review.appointment_id` index is the final guard.
- Creating a review updates `doctor.rating_average` and `doctor.rating_count` atomically
  under a doctor-row lock. Doctor responses now expose `ratingAverage` and `ratingCount`.
- Public review lists mask patient names (`Patient O.`).

## Admin

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/v1/admin/stats` | admin | Bookings per day/specialty, status counts, cancellation rate, top doctors |
| GET | `/api/v1/admin/users` | admin | List accounts (`role`, `q`, `page`, `size`) |
| PATCH | `/api/v1/admin/users/{id}/status` | admin | Enable or disable an account |
| PATCH | `/api/v1/admin/users/{id}/role` | admin | Change a non-doctor role |

- Stats default to the last 30 days in the clinic timezone and are capped at 366 days.
  `from`/`to` are ISO dates; `topDoctors` defaults to 5 and is capped at 20. Aggregation
  happens in Java so MySQL and PostgreSQL return identical numbers.
- `cancellationRate` is cancelled bookings divided by all bookings in the range, with
  four decimal places (`0.2500` = 25%).
- Safeguards: administrators cannot change their own role or status
  (`409 SELF_MODIFICATION_NOT_ALLOWED`), the last enabled administrator cannot be
  disabled or demoted (`409 LAST_ADMIN`), and doctor roles are managed through the doctor
  endpoints (`409 DOCTOR_ROLE_MANAGED_SEPARATELY`).
- Disabling an account revokes its refresh tokens; a role change also revokes tokens so
  the next login picks up the new role. Disabling a doctor deactivates their public
  profile.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` = MySQL, `prod` = Neon PostgreSQL |
| `PORT` | Render-provided | PaaS HTTP port; `SERVER_PORT` wins when both are set |
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | none (**required** in `prod`) | Neon JDBC URL, e.g. `jdbc:postgresql://host/db?sslmode=require&channelBinding=require` |
| `DB_USER` | none (**required** in `prod`) | Neon database user |
| `DB_PASSWORD` | none (**required** in `prod`) | Neon database password |
| `DB_POOL_SIZE` | `5` in `prod`, `10` otherwise | Hikari maximum pool size |
| `DB_MIN_IDLE` | `1` | Hikari minimum idle connections |
| `DB_CONNECTION_TIMEOUT_MS` | `30000` | Hikari connection timeout |
| `DB_IDLE_TIMEOUT_MS` | `600000` | Hikari idle timeout |
| `DB_MAX_LIFETIME_MS` | `1800000` | Hikari maximum connection lifetime |
| `DB_KEEPALIVE_TIME_MS` | `300000` | Hikari keepalive (helps with Neon scale-to-zero) |
| `CLINICBOOK_DB_URL` | local MySQL URL | `local` profile JDBC URL |
| `CLINICBOOK_DB_USER` | `clinicbook` | `local` profile database user |
| `CLINICBOOK_DB_PASSWORD` | none (**required** in `local`) | `local` profile database password |
| `JWT_SECRET` | none (**required** everywhere) | HMAC signing key, **at least 32 bytes** |
| `JWT_ISSUER` | `clinicbook` | Access-token issuer claim |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Access-token lifetime |
| `JWT_REFRESH_TOKEN_TTL` | `7d` | Refresh-token lifetime |
| `CORS_ORIGIN` | `http://localhost:5173` local, **required** in `prod` | Allowed frontend origin(s), comma separated |
| `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` | `5` | Login attempts per window per IP |
| `LOGIN_RATE_LIMIT_WINDOW` | `1m` | Login rate-limit window |
| `CLINIC_TIME_ZONE` | `Asia/Kolkata` | Zone used to interpret working hours and clinic dates |
| `SLOT_HORIZON_DAYS` | `30` | Days of bookable slots to materialise |
| `SLOT_NIGHTLY_CRON` | `0 0 3 * * *` | Nightly slot-extension schedule (clinic timezone) |
| `BOOKING_CANCEL_CUTOFF` | `2h` | How close to the start a patient may still cancel |
| `BOOKING_MAX_ACTIVE_PER_PATIENT` | `3` | Maximum `PENDING` + `CONFIRMED` appointments per patient |
| `MAIL_HOST` | `localhost` | SMTP host (Mailpit/MailHog locally) |
| `MAIL_PORT` | `1025` | SMTP port |
| `MAIL_USERNAME` | empty | SMTP username, if the server requires auth |
| `MAIL_PASSWORD` | empty | SMTP password |
| `MAIL_SMTP_AUTH` | `false` | Enable SMTP authentication |
| `MAIL_STARTTLS` | `false` | Enable STARTTLS (port 587) |
| `MAIL_SSL_ENABLE` | `false` | Enable implicit TLS (port 465) |
| `MAIL_FROM` | `no-reply@clinicbook.local` | Sender address on appointment emails |
| `NOTIFICATIONS_ENABLED` | `true` | Master switch for appointment email and the reminder job |
| `CLINIC_NAME` | `ClinicBook` | Clinic name shown in email subjects and bodies |
| `REMINDER_CRON` | `0 0 * * * *` | Reminder sweep schedule (clinic timezone) |
| `REMINDER_LEAD` | `24h` | How far ahead of the start a reminder is sent |
| `SEED_ENABLED` | `true` | Create fake local-profile demo data on startup |
| `DEMO_DOCTOR_PASSWORD` | none | Password for seeded demo doctors; seeding is skipped when blank |
| `JAVA_OPTS` | see `Dockerfile` | JVM flags for the container image |

`backend/.env.sample` is the copy-paste template for every key above (local and
production sections). Secrets are never committed: `application-prod.yml` has no
fallback values for `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` or `CORS_ORIGIN`.

Only `health`, `info` and `metrics` Actuator endpoints are exposed.

## Deploy to Render + Neon

`render.yaml` at the repository root builds `backend/Dockerfile` and runs the image with
`SPRING_PROFILES_ACTIVE=prod`.

1. **Neon** — create a project/database, then copy the **JDBC** connection string from
   *Connect*. Use the direct host (no `-pooler`) so Flyway and Hibernate prepared
   statements behave; append `?sslmode=require&channelBinding=require`. If you must use
   the pooled host, add `&prepareThreshold=0`. Flyway creates the schema on first boot
   from `db/migration/postgresql`.
2. **Render** — *New → Blueprint* and select this repo, or create a Web Service with
   Runtime **Docker**, Dockerfile `backend/Dockerfile` and context `backend`. Set
   `DB_URL`, `DB_USER`, `DB_PASSWORD` and `CORS_ORIGIN`; `JWT_SECRET` is generated by the
   blueprint. The health check is `/actuator/health`. The app binds to Render's `PORT`.
3. **Frontend** — build it with `VITE_API_URL=https://<service>.onrender.com/api/v1`.
4. **Auto-deploy** — Render's deploy hook URL goes in the GitHub repository secret
   `RENDER_BACKEND_HOOK`. `.github/workflows/deploy.yml` calls it only after CI passes.

The free Render plan spins down when idle; the first request after that is slow. Use a
paid plan for a real clinic deployment.


## Migrations

Flyway resolves `classpath:db/migration/{vendor}`, so migrations live in
`src/main/resources/db/migration/mysql/` and `.../postgresql/`. Every migration must
exist in both folders with the same filename.

### Apply the Postgres schema to Neon manually

Render runs Flyway automatically on every boot, so this is optional. It is useful for
verifying the schema before the first deploy. The `flyway-maven-plugin` is configured to
use only the `postgresql` migrations.

```bash
cd backend

# Fill these with the Neon connection details (shell env, not committed)
export DB_URL='jdbc:postgresql://<host>/<db>?sslmode=require&channelBinding=require'
export DB_USER='<neon-user>'
export DB_PASSWORD='<neon-password>'

# Check, apply, verify
mvn -B flyway:info   -Dflyway.url="$DB_URL" -Dflyway.user="$DB_USER" -Dflyway.password="$DB_PASSWORD"
mvn -B flyway:migrate -Dflyway.url="$DB_URL" -Dflyway.user="$DB_USER" -Dflyway.password="$DB_PASSWORD"
mvn -B flyway:validate -Dflyway.url="$DB_URL" -Dflyway.user="$DB_USER" -Dflyway.password="$DB_PASSWORD"
```

PowerShell equivalent:

```powershell
$env:DB_URL = "jdbc:postgresql://<host>/<db>?sslmode=require&channelBinding=require"
$env:DB_USER = "<neon-user>"
$env:DB_PASSWORD = "<neon-password>"
mvn -B flyway:migrate "-Dflyway.url=$env:DB_URL" "-Dflyway.user=$env:DB_USER" "-Dflyway.password=$env:DB_PASSWORD"
```

`flyway:repair` fixes the schema history table only; it never re-runs a failed migration
silently. The schema is owned by Flyway — do not create tables in Neon by hand.


## Package layout

Feature-first (`auth`, `user`, `doctor`, `schedule`, `appointment`, `review`,
`notification`, `admin`). Cross-cutting code goes in `config` and `common`.
