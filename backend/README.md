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
doctor signs in with password `Doctor123!`. Set `SEED_ENABLED=false` to turn it off;
the runner never loads in tests or production.

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
| `BOOKING_CANCEL_CUTOFF` | `2h` | How close to the start a patient may still cancel |
| `BOOKING_MAX_ACTIVE_PER_PATIENT` | `3` | Maximum `PENDING` + `CONFIRMED` appointments per patient |
| `MAIL_HOST` | `localhost` | SMTP host (Mailpit/MailHog locally) |
| `MAIL_PORT` | `1025` | SMTP port |
| `MAIL_USERNAME` | empty | SMTP username, if the server requires auth |
| `MAIL_PASSWORD` | empty | SMTP password |
| `MAIL_SMTP_AUTH` | `false` | Enable SMTP authentication |
| `MAIL_STARTTLS` | `false` | Enable STARTTLS |
| `MAIL_FROM` | `no-reply@clinicbook.local` | Sender address on appointment emails |
| `NOTIFICATIONS_ENABLED` | `true` | Master switch for appointment email and the reminder job |
| `CLINIC_NAME` | `ClinicBook` | Clinic name shown in email subjects and bodies |
| `REMINDER_CRON` | `0 0 * * * *` | Reminder sweep schedule (clinic timezone) |
| `REMINDER_LEAD` | `24h` | How far ahead of the start a reminder is sent |
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
