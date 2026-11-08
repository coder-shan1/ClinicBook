# ClinicBook — Roadmap

Phase-by-phase plan. Update the checkboxes as work completes (agents: tick items in the same PR that finishes them).

**Current phase:** Phase 0
**Target:** ~5–6 weeks part-time. Each phase ends with a merged PR into `develop` and a green CI run.

Legend: 🟦 Kiro · 🟧 OpenCode · 👤 you (review/decision)

---

## Phase 0 — Foundation and tooling (Days 1–2)

Branch: `chore/p0-bootstrap`

- [x] 👤 Create GitHub repo `clinicbook`, enable branch protection on `main` and `develop`
- [X] 🟧 Create monorepo skeleton from `ARCHITECTURE.md` §4 (empty `backend/`, `frontend/`, `docs/`, `.github/`)
- [X] 🟧 Spring Boot project (Java 21, Maven) with Web, JPA, Security, Validation, Actuator, Flyway, MySQL + PostgreSQL drivers, springdoc
- [X] 🟦 Vite + React + TypeScript + Tailwind project
- [X] 🟧 `.gitignore`, `.editorconfig`, `.env.example`, PR template, root `AGENTS.md` pointer
- [x]🟧 Local MySQL database `clinicbook` + `local` profile connecting; app boots and `/actuator/health` is UP
- [X] 🟦 Add `.kiro/steering` pointer to `.agents/` docs
- [X] 👤 First ADRs (monorepo, dual-dialect DB)

**Exit:** `mvn verify` and `npm run build` both pass on a fresh clone.

---

## Phase 1 — Auth and users (Days 3–6)

Branch: `feature/p1-auth`

- [ ] 🟧 Flyway `V1__users.sql` (mysql + postgresql)
- [ ] 🟧 `User`, `Role`, `RefreshToken` entities and repositories
- [ ] 🟧 Register, login, refresh, logout endpoints
- [ ] 🟧 JWT service + filter, BCrypt, method security, CORS config
- [ ] 🟧 Global exception handler with the standard error shape
- [ ] 🟧 Tests: service unit tests; MockMvc for 401/403/validation; login rate limit
- [ ] 🟦 Frontend: login and register pages, auth context, protected routes, API client with token refresh
- [ ] 🟦 Vitest tests for login form (success, wrong password, validation)

**Exit:** A user can register, log in, and reach a role-protected page. Swagger UI documents all auth endpoints.

---

## Phase 2 — Clinic domain: doctors, specialties, schedules (Week 2)

Branch: `feature/p2-doctors-schedule`

- [ ] 🟧 Migrations: `specialty`, `doctor`, `working_hours`, `time_off`, `time_slot` (both dialects)
- [ ] 🟧 Doctor CRUD (admin), doctor profile (doctor), public doctor search with filters + pagination
- [ ] 🟧 Working hours and time-off endpoints (doctor)
- [ ] 🟧 Slot generation service: next 30 days from working hours minus time off; idempotent
- [ ] 🟧 `@Scheduled` nightly job to extend slots; regenerate on schedule change
- [ ] 🟧 Seed data (fake doctors/specialties) via a `local` profile runner
- [ ] 🟧 Tests: slot generation edge cases (time off, DST/UTC, overlapping hours), search queries on MySQL and Postgres
- [ ] 🟦 Frontend: doctor list with search/filter, doctor detail page, doctor schedule editor

**Exit:** Doctors can define availability; slots appear for the next 30 days; public search works.

Tag after merge to `main` later: none yet.

---

## Phase 3 — Booking engine (Week 3) ⭐ the centrepiece

Branch: `feature/p3-booking`

- [ ] 🟧 Migration: `appointment` (both dialects)
- [ ] 🟧 `AppointmentService.book()` — transactional, optimistic lock on `TimeSlot`, 409 `SLOT_TAKEN` on conflict
- [ ] 🟧 Cancel / confirm / complete / no-show with state-machine validation and role rules
- [ ] 🟧 Cancel returns slot to `AVAILABLE`, detaches `slot_id`, keeps history
- [ ] 🟧 Rules: no booking in the past, cutoff for cancellations, max active bookings per patient
- [ ] 🟧 **Concurrency integration test** (20 threads, 1 slot → exactly 1 success) on MySQL and Postgres
- [ ] 🟧 Unit tests for every status transition (allowed and forbidden)
- [ ] 🟦 Spec in `docs/specs/booking/` (requirements, design, tasks)
- [ ] 🟦 Frontend: slot picker (calendar + times), booking confirmation, "My appointments" with cancel, doctor's appointment list with confirm/complete
- [ ] 🟦 Frontend handling of 409 (slot just taken → refresh slots, friendly message)

**Exit:** Concurrency test is green on both DBs. End-to-end flow works in the browser. Tag `v0.1.0` on release PR.

---

## Phase 4 — Notifications, reviews, admin (Week 4)

Branch: `feature/p4-extras`

- [ ] 🟧 Email on booking/confirm/cancel (Spring Mail, HTML templates); use Mailpit/MailHog locally
- [ ] 🟧 Reminder job: email 24h before appointment; idempotent via `notification_log`
- [ ] 🟧 Reviews: only for `COMPLETED` appointments, one per appointment, average rating on doctor
- [ ] 🟧 Admin stats endpoint: bookings per day, per specialty, cancellation rate, top doctors
- [ ] 🟧 Admin user management (enable/disable, role change with safeguards)
- [ ] 🟦 Frontend: admin dashboard with charts, review form, notification preferences stub
- [ ] 🟧 Tests for reminder idempotency and review rules

**Exit:** Emails are sent in local dev (visible in Mailpit); admin dashboard shows real numbers.

---

## Phase 5 — Quality hardening (Week 5, part 1)

Branch: `chore/p5-quality`

- [ ] 🟧 JaCoCo configured; ≥ 70% coverage on service layer
- [ ] 🟧 Review all endpoints for authorization gaps (write a test per role per endpoint group)
- [ ] 🟧 Add request-id logging, structured logs, and Actuator hardening
- [ ] 🟧 Performance pass: N+1 queries, indexes, pagination everywhere
- [ ] 🟦 Frontend: a11y pass, loading/empty/error states, responsive layout, 404 page
- [ ] 🟦 Frontend tests for booking flow and error states
- [ ] 👤 Cross-review: each tool reviews the other's code (use prompt "Code review" in `PROMPTS.md`)
- [ ] 👤 Manual exploratory test with a written checklist in `docs/qa-checklist.md`

**Exit:** No known auth gaps, coverage target met, QA checklist passes.

---

## Phase 6 — Containerization and CI/CD (Week 5, part 2)

Branches: `chore/p6-docker`, `ci/p6-pipeline`

- [ ] 🟧 `backend/Dockerfile` (multi-stage, non-root, healthcheck)
- [ ] 🟧 `frontend/Dockerfile` + `nginx.conf` (SPA fallback)
- [ ] 🟧 `docker-compose.yml` (db + backend + frontend + Mailpit), `.env.example` complete
- [ ] 🟧 `ci.yml`: backend matrix (mysql/postgres), frontend checks, migrations-parity, docker build
- [ ] 🟧 `deploy.yml`: deploy to Render on `main` after CI passes
- [ ] 🟧 `dependabot.yml`, `dependency-review` on PRs
- [ ] 👤 Add required status checks to branch protection once jobs have run once
- [ ] 🟧 Verify: fresh clone → `docker compose up --build` → app works

**Exit:** CI is green on PRs; one command starts the whole stack locally.

---

## Phase 7 — Production deployment (Week 6, part 1)

Branch: `chore/p7-deploy`

- [ ] 👤 Create Neon project and database; copy pooled connection string (`sslmode=require`)
- [ ] 👤 Create Render web service (backend, Docker) and static site (frontend) or Docker service
- [ ] 👤 Set env vars on Render (`SPRING_PROFILES_ACTIVE=prod`, `DB_*`, `JWT_SECRET`, `CORS_ORIGIN`, `MAIL_*`, `VITE_API_URL`)
- [ ] 🟧 `application-prod.yml`: Postgres dialect, Flyway location `postgresql`, small Hikari pool, no SQL logging
- [ ] 🟧 Confirm Flyway runs all Postgres migrations on first boot
- [ ] 🟧 Seed a demo admin/doctor/patient (fake data) via a guarded one-time script, not committed credentials
- [ ] 👤 Smoke test the live site; add health check path on Render (`/actuator/health`)
- [ ] 👤 Note cold-start behaviour on free tier in README; optional keep-alive via external pinger

**Exit:** Public URL works end to end on Neon + Render. Tag `v0.9.0`.

---

## Phase 8 — Portfolio polish (Week 6, part 2)

Branch: `docs/p8-portfolio`

- [ ] 🟧/🟦 README: pitch, live demo link, screenshots/GIF, features, architecture diagram, ER diagram, tech stack, run locally (Docker and manual), env vars, API overview, testing, CI badge, deployment notes, roadmap, license
- [ ] 🟧 Finalize ADRs in `docs/adr/`
- [ ] 🟦 Record a 60–90 second demo video/GIF of the booking flow
- [ ] 👤 Pin repo on GitHub profile; add topics (`spring-boot`, `react`, `mysql`, `postgresql`, `docker`, `github-actions`)
- [ ] 👤 Write 3–4 resume bullets with measurable details (see below)
- [ ] 👤 Release `v1.0.0` with release notes

**Exit:** A recruiter can understand and try the project in under two minutes.

### Resume bullet starters

- Built a full-stack clinic booking platform (Spring Boot, React, MySQL/PostgreSQL) with JWT role-based security; deployed via Docker, GitHub Actions, Render and Neon.
- Eliminated double-booking using pre-generated slots and optimistic locking, verified by a multi-threaded integration test on two databases.
- Designed a CI pipeline running unit and integration tests across MySQL and PostgreSQL, with automated migration-parity checks and deploy-on-merge.
- Reached 70%+ service-layer coverage with JUnit 5, Mockito and Testcontainers.

---

## Parking lot (only after v1.0.0)

- WebSocket live slot updates
- Google Calendar / `.ics` export
- Payments (Stripe test mode)
- Multi-clinic tenancy
- Redis caching for slot queries
- Playwright end-to-end tests in CI

---

## Weekly rhythm

1. Monday: pick the next unchecked items; write/update the spec (Kiro) for any new feature.
2. Midweek: implement on `ai/<tool>/<task>` branches; open PRs early.
3. Friday: cross-review, merge to `develop`, tick the roadmap, note blockers.
4. When a phase is complete: PR `develop → main`, tag, confirm deploy (from Phase 7).

## Progress log

| Date | Phase | Notes |
|---|---|---|
| | | |
