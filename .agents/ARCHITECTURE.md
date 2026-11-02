# ClinicBook — Architecture

> Clinic appointment booking platform. Patients book slots with doctors; doctors manage availability; admins oversee the clinic.
> Portfolio project built with AI-assisted development (OpenCode + Kiro). Rename freely.

## 1. Goals

- Demonstrate production-style Java engineering: security, transactions, concurrency, testing, CI/CD.
- Zero double-bookings, even under concurrent requests.
- Run identically on a laptop (Docker or local MySQL) and in production (Render + Neon).

## 2. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Java 21 | LTS |
| Backend | Spring Boot 3.x (Maven) | Web, Data JPA, Security, Validation, Mail, Actuator |
| Auth | JWT (access + refresh) | Stateless, role-based |
| API docs | springdoc-openapi (Swagger UI) | Frontend client types generated from it |
| Migrations | Flyway | Vendor-specific folders (see §6) |
| DB (local) | MySQL 8 | Local install or Docker |
| DB (prod) | PostgreSQL on Neon | Neon is Postgres-only, not MySQL |
| Frontend | React 18 + Vite + TypeScript + Tailwind | TanStack Query, React Router |
| Tests | JUnit 5, Mockito, Testcontainers, Vitest, Testing Library | |
| Containers | Docker, docker-compose | Multi-stage builds |
| CI/CD | GitHub Actions | Deploys to Render |
| Hosting | Render (backend + static frontend), Neon (DB) | Free tier cold starts expected |

## 3. System overview

```
 Browser (React SPA)
        │  HTTPS, JSON, Bearer JWT
        ▼
 Spring Boot API ──► Flyway ──► DB
   │   controller → service → repository
   │   security filter chain (JWT)
   └─► Mail (SMTP) / @Scheduled jobs
        │
        ▼
 MySQL (local)  |  Neon PostgreSQL (prod)
```

## 4. Repository structure (monorepo)

```
clinicbook/
├── .agents/
│   ├── AGENTS.md              # rules for AI agents (read first)
│   ├── ARCHITECTURE.md        # this file
│   ├── roadmap.md             # phases + checklists
│   └── PROMPTS.md             # reusable prompts
├── .github/
│   ├── workflows/
│   │   ├── ci.yml             # PR + push checks
│   │   └── deploy.yml         # main → Render
│   ├── pull_request_template.md
│   └── dependabot.yml
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/clinicbook/
│       │   ├── ClinicBookApplication.java
│       │   ├── config/            # Security, CORS, OpenAPI, Scheduling beans
│       │   ├── common/            # exceptions, ApiError, base entity, utils
│       │   ├── auth/              # login, register, refresh, JWT service/filter
│       │   ├── user/              # User, Role, profile
│       │   ├── doctor/            # Doctor, Specialty, search
│       │   ├── schedule/          # WorkingHours, TimeOff, TimeSlot generation
│       │   ├── appointment/       # booking engine, status lifecycle
│       │   ├── review/            # ratings after completed visits
│       │   ├── notification/      # email templates, reminder jobs
│       │   └── admin/             # stats, user management
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-local.yml
│       │   ├── application-prod.yml
│       │   └── db/migration/
│       │       ├── mysql/         # V1__init.sql ...
│       │       └── postgresql/    # V1__init.sql ... (same versions)
│       └── test/java/com/clinicbook/   # mirrors main packages
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
│       ├── api/               # generated client + axios instance
│       ├── components/        # shared UI
│       ├── features/          # auth, doctors, booking, appointments, admin
│       ├── hooks/
│       ├── pages/
│       ├── routes/
│       ├── types/
│       └── main.tsx
├── docs/
│   ├── adr/                   # architecture decision records
│   ├── er-diagram.png
│   └── screenshots/
├── docker-compose.yml         # app + db for local/dev
├── docker-compose.mysql.yml   # optional: MySQL only
├── .env.example
├── .gitignore
├── AGENTS.md                  # 3-line pointer to .agents/AGENTS.md (see §9)
└── README.md
```

Package rule: **feature-first**. Each feature package holds its own controller, service, repository, entity, and DTOs. No global `controllers/` or `services/` folders.

## 5. Domain model

Roles: `PATIENT`, `DOCTOR`, `ADMIN`.

| Entity | Key fields |
|---|---|
| `User` | id, email (unique), passwordHash, role, fullName, phone, enabled, createdAt |
| `Doctor` | id, user_id, specialty_id, bio, consultationFee, active |
| `Specialty` | id, name (unique) |
| `WorkingHours` | id, doctor_id, dayOfWeek, startTime, endTime, slotMinutes |
| `TimeOff` | id, doctor_id, startAt, endAt, reason |
| `TimeSlot` | id, doctor_id, startAt, endAt, status (`AVAILABLE`/`BOOKED`/`BLOCKED`), **version** |
| `Appointment` | id, patient_id, doctor_id, slot_id (nullable), startAt, endAt, status, reason, notes, createdAt |
| `Review` | id, appointment_id (unique), rating 1–5, comment |
| `RefreshToken` | id, user_id, tokenHash, expiresAt, revoked |

Appointment status lifecycle:

```
PENDING → CONFIRMED → COMPLETED
   │          │
   └──► CANCELLED   (patient/doctor/admin)      CONFIRMED → NO_SHOW
```

### Booking engine (the part to be proud of)

- Slots are **pre-generated rows** (nightly job + on schedule change) for the next 30 days from `WorkingHours` minus `TimeOff`.
- Unique constraint `(doctor_id, start_at)` on `time_slot`.
- Booking = one `@Transactional` method: load slot → verify `AVAILABLE` → set `BOOKED` → insert appointment. `@Version` on `TimeSlot` makes the loser of a race fail with `OptimisticLockingFailureException`, mapped to **409 Conflict**.
- Cancelling sets the appointment to `CANCELLED`, copies `startAt/endAt` onto it (already stored), nulls `slot_id`, and returns the slot to `AVAILABLE`. History is kept; the slot is reusable.
- Why a slot table and not a partial unique index: MySQL has no partial indexes, Postgres does. Slot rows behave the same on both.
- A concurrency integration test (N threads, one slot, exactly one success) is **mandatory**.

## 6. Database strategy (important)

Neon only offers PostgreSQL. Local dev uses MySQL. That is a deliberate dual-dialect setup:

- Spring profiles: `local` → MySQL, `prod` → Postgres (Neon).
- Flyway locations: `classpath:db/migration/{vendor}` (Flyway resolves `mysql` / `postgresql`).
- **Every migration exists in both folders with the same version number.** Adding one without the other fails CI.
- JPA entities stay dialect-neutral: no native queries unless unavoidable, no `ENUM` column types (use `VARCHAR` + `@Enumerated(STRING)`), no vendor functions.
- `spring.jpa.hibernate.ddl-auto=validate` everywhere. Flyway owns the schema.
- CI runs integration tests against **both** databases (matrix).
- Fallback if this becomes a time sink: switch local dev to Postgres in Docker and drop the MySQL folder. Record that as an ADR.

Neon notes: connection string needs `sslmode=require`; the free tier suspends idle compute (first query is slow); use a small Hikari pool (`maximum-pool-size: 5`).

## 7. API conventions

- Base path `/api/v1`. JSON only. Plural nouns. Pagination via `page`, `size`, `sort`.
- Errors use one shape:

```json
{ "timestamp": "...", "status": 409, "error": "SLOT_TAKEN", "message": "...", "path": "/api/v1/appointments", "fieldErrors": [] }
```

- Never expose entities; use DTOs (Java records) + a mapper (MapStruct or manual).
- Core endpoints:

| Method | Path | Role |
|---|---|---|
| POST | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` | public |
| GET | `/doctors?specialty=&q=&page=` | public |
| GET | `/doctors/{id}/slots?date=` | public |
| POST | `/appointments` | PATIENT |
| GET | `/appointments/me` | PATIENT, DOCTOR |
| PATCH | `/appointments/{id}/cancel`, `/confirm`, `/complete` | by rule |
| PUT | `/doctors/me/working-hours`, POST `/doctors/me/time-off` | DOCTOR |
| POST | `/reviews` | PATIENT |
| GET | `/admin/stats`, `/admin/users` | ADMIN |

## 8. Security

- BCrypt password hashing. Access token 15 min, refresh token 7 days (stored hashed, rotated).
- Method security (`@PreAuthorize`) plus URL rules. Ownership checks in the service layer (a patient only sees their own appointments).
- CORS restricted to the frontend origin from env.
- Secrets only via environment variables. `.env` is gitignored; `.env.example` is committed.
- Rate limit login endpoint (Bucket4j or a simple filter).
- Dependabot enabled; `dependency-review` action on PRs.

## 9. AI tooling setup

- **OpenCode** and **Kiro** both read agent instructions. Keep the single source of truth in `.agents/AGENTS.md`.
- Add a root `AGENTS.md` containing only: "Read `.agents/AGENTS.md`, `.agents/ARCHITECTURE.md`, and `.agents/roadmap.md` before any task." Root `AGENTS.md` is the file OpenCode looks for by convention. For Kiro, point a steering file in `.kiro/steering/` at the same docs. Verify both in each tool's current docs, as conventions change.
- Division of labour (see `AGENTS.md` §8): Kiro for spec-driven features and the frontend; OpenCode for backend internals, refactors, DevOps, and repetitive terminal tasks.
- Never run both tools on the same branch at once. Use separate branches or `git worktree`.

## 10. Git branching

Lightweight GitFlow, tuned for one developer plus AI agents.

```
main        ← production. Protected. Auto-deploys. Tagged vX.Y.Z.
 └─ develop ← integration. Protected. CI must pass.
     ├─ feature/<phase>-<short-name>   e.g. feature/p3-slot-booking
     ├─ fix/<short-name>
     ├─ chore/<short-name>             (deps, CI, tooling)
     ├─ docs/<short-name>
     └─ ai/<tool>/<short-name>         e.g. ai/kiro/booking-ui, ai/opencode/dockerize
```

Rules:

1. Branch from `develop`, merge back with a **PR + squash merge**. No direct pushes to `develop` or `main`.
2. `develop` → `main` via a release PR (merge commit), then tag `vX.Y.Z`.
3. Hotfix: branch from `main` as `fix/…`, PR into `main`, then merge `main` back into `develop`.
4. One AI task = one branch = one PR. Keep PRs under ~400 changed lines where possible.
5. Delete branches after merge.
6. Branch protection on `main` and `develop`: require PR, require status checks (`backend`, `frontend`, `docker`), require linear history on `develop`.

Commit messages follow Conventional Commits:

```
feat(appointment): prevent double booking with optimistic locking
fix(auth): rotate refresh token on use
test(appointment): add 20-thread concurrency test
ci: add postgres matrix job
docs: update roadmap phase 3
```

Versioning: `v0.1.0` after Phase 3, `v0.5.0` after frontend MVP, `v1.0.0` when deployed and documented.

## 11. CI/CD pipeline

### Triggers

| Event | Pipeline |
|---|---|
| PR into `develop` or `main` | `ci.yml` (all checks) |
| Push to `develop` | `ci.yml` |
| Push to `main` | `ci.yml` then `deploy.yml` |
| Tag `v*` | GitHub Release notes |

### Stages

1. **backend**: checkout → Java 21 (Temurin) with Maven cache → `mvn -B verify` → upload JaCoCo report. Matrix over `db: [mysql, postgres]` using service containers (or Testcontainers).
2. **frontend**: Node 20 with npm cache → `npm ci` → `npm run lint` → `npm run typecheck` → `npm test -- --run` → `npm run build`.
3. **migrations-check**: script fails if `db/migration/mysql` and `db/migration/postgresql` have different file names.
4. **docker**: build backend and frontend images (no push) to prove the Dockerfiles work.
5. **security**: `dependency-review-action` on PRs; optional CodeQL weekly.
6. **deploy** (main only): trigger Render deploy hooks after all checks pass. Alternative: enable Render auto-deploy gated on "after CI checks pass".

### `ci.yml` skeleton

```yaml
name: CI
on:
  pull_request:
    branches: [develop, main]
  push:
    branches: [develop, main]

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  backend:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        db: [mysql, postgres]
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven
      - run: mvn -B verify -Dtest.db=${{ matrix.db }}
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: jacoco-${{ matrix.db }}
          path: backend/target/site/jacoco

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - run: npm ci
      - run: npm run lint
      - run: npm run typecheck
      - run: npm test -- --run
      - run: npm run build

  migrations-parity:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: MySQL and Postgres migrations must have identical file names
        shell: bash
        run: |
          diff <(ls backend/src/main/resources/db/migration/mysql) \
               <(ls backend/src/main/resources/db/migration/postgresql)
```

Ask an agent to finalize and extend this skeleton (see `PROMPTS.md`, prompt 14).

`deploy.yml`: on push to `main`, `needs` the CI checks (or use `workflow_run`), then `curl -X POST "$RENDER_BACKEND_HOOK"` and `"$RENDER_FRONTEND_HOOK"`.

### Secrets and variables

| Where | Name |
|---|---|
| GitHub secrets | `RENDER_BACKEND_HOOK`, `RENDER_FRONTEND_HOOK` |
| Render env (backend) | `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGIN`, `MAIL_*` |
| Render env (frontend) | `VITE_API_URL` (build-time) |
| Local `.env` | same keys, see `.env.example` |

## 12. Containerization

- `backend/Dockerfile`: multi-stage. Stage 1 `maven:3-eclipse-temurin-21` builds with dependency-layer caching. Stage 2 `eclipse-temurin:21-jre` runs as a non-root user, exposes 8080, has a `HEALTHCHECK` on `/actuator/health`.
- `frontend/Dockerfile`: Stage 1 Node build. Stage 2 `nginx:alpine` serving `dist/` with SPA fallback (`try_files $uri /index.html`).
- `docker-compose.yml`: `db` (MySQL 8 with volume + healthcheck), `backend` (depends on healthy db), `frontend`. All config via `.env`.

## 13. Observability and quality gates

- Actuator `health` and `info` exposed; everything else off in prod.
- Structured logs with a request id (MDC filter).
- Quality gates: backend coverage ≥ 70% on service layer, no failing tests, frontend lint and typecheck clean.

## 14. Architecture decisions log

Record each non-obvious choice in `docs/adr/NNNN-title.md` (context, decision, consequences). Seed list:

1. 0001 Monorepo
2. 0002 Dual-dialect DB (MySQL local / Postgres prod)
3. 0003 Pre-generated slots + optimistic locking
4. 0004 JWT with rotating refresh tokens
5. 0005 React SPA instead of server-rendered views
