# ClinicBook

**Spring Boot 3.5 + React appointment system** with MySQL/PostgreSQL, Docker, CI/CD,
optimistic locking, and role-based access control.

---

## 📦 Phase 2 — Clinic Domain (Doctors, Specialties, Schedules)

**Branch:** `feature/p2-doctors-schedule`

| Feature | Status | API Docs |
|---|---|---|
| 🟦 Database migrations (`specialty`, `doctor`, `working_hours`, `time_off`, `time_slot`) — MySQL & PostgreSQL | ✅ Completed | `V2__clinic_domain.sql` |
| 🟧 Doctor CRUD (admin) + doctor profile (doctor) | ✅ Completed | `/api/v1/admin/doctors`, `/api/v1/doctors/me` |
| 🟧 Public doctor search with filters & pagination | ✅ Completed | `/api/v1/doctors?specialty=&q=&page=&size=&sort=` |
| 🟧 Working hours & time-off (doctor) | ✅ Completed | `/api/v1/doctors/me/working-hours`, `/api/v1/doctors/me/time-off` |
| 🟧 Slot generation (next 30 days, minus time off) | ✅ Completed | `/api/v1/doctors/{id}/slots?date=` |
| 🟧 `@Scheduled` nightly job + immediate regen on schedule change | ✅ Completed | `SLOT_NIGHTLY_CRON`, `/api/v1/doctors/me/slots/regenerate` |
| 🟧 Seed data (6 fake specialties + doctors, `Doctor123!`) | ✅ Completed | `local` profile only, `SEED_ENABLED=false` to disable |

---

## 📁 Repository Structure

```
ClinicBook/
├── backend/             # Spring Boot 3.5 (Java 21), Maven, Flyway
│   ├── src/main/java/  # Feature‑first packages: auth, user, doctor, schedule, admin
│   ├── src/main/resources/db/migration/{mysql,postgresql}/  # V1__users.sql, V2__clinic_domain.sql
│   └── src/main/resources/application-{local,}.yml
├── frontend/            # React 18 + Vite + TypeScript + Tailwind
│   ├── src/features/doctor/  # DoctorsPage, useDoctors hook
│   └── src/mocks/  # MSW handlers for Phase 2 endpoints
├── docs/                # OpenAPI spec, roadmap, architecture
└── README.md            # This dashboard
```

---

## 🛠 Backend — Phase 2 Changes (since `fa1b697`)

### Migrations
- `V2__clinic_domain.sql` — creates `specialties`, `doctors`, `working_hours`, `time_off`, `time_slot` tables (both MySQL & PostgreSQL dialects)
- Each migration exists in both `mysql/` and `postgresql/` folders with identical filenames

### API Endpoints (public + protected + admin)

**Public (no auth)**
- `GET /api/v1/specialties` — list specialties, ordered by name
- `GET /api/v1/doctors` — search active doctors (`specialty`, `q`, `page`, `size`, `sort`)
- `GET /api/v1/doctors/{id}` — get one active doctor
- `GET /api/v1/doctors/{id}/slots` — available slots for a date (`YYYY-MM-DD`, clinic timezone)

**Doctor‑owned**
- `GET /api/v1/doctors/me` — own profile
- `PUT /api/v1/doctors/me` — update specialty, bio, fee
- `GET /api/v1/doctors/me/working-hours` — list own working hours
- `PUT /api/v1/doctors/me/working-hours` — replace working hours and regenerate slots
- `GET /api/v1/doctors/me/time-off` — list own time off
- `POST /api/v1/doctors/me/time-off` — add time off and regenerate slots
- `DELETE /api/v1/doctors/me/time-off/{id}` — delete time off and regenerate slots
- `POST /api/v1/doctors/me/slots/regenerate` — regenerate the rolling slot window now

**Admin**
- `GET /api/v1/admin/doctors` — list doctors, including inactive ones
- `POST /api/v1/admin/doctors` — create a `DOCTOR` user and profile
- `GET /api/v1/admin/doctors/{id}` — get a doctor, including inactive ones
- `PUT /api/v1/admin/doctors/{id}` — update a doctor and activate/deactivate
- `DELETE /api/v1/admin/doctors/{id}` — soft-delete: deactivate doctor and disable account
- `POST /api/v1/admin/specialties` — create a specialty
- `PUT /api/v1/admin/specialties/{id}` — rename or update a specialty
- `DELETE /api/v1/admin/specialties/{id}` — delete an unused specialty (`409` if in use)

### Key Behaviours
- Working hours are wall‑clock times in `CLINIC_TIME_ZONE` (default `Asia/Kolkata`); slots are stored/returned as UTC instants
- Slots are materialised for the next `SLOT_HORIZON_DAYS` (default `30`) days, minus time off
- Generation is idempotent: existing rows matched by UTC start, free slots resized when slot length changes, only free slots that no longer match the schedule are deleted
- `BOOKED` and `BLOCKED` slots are never removed
- Nightly job (`SLOT_NIGHTLY_CRON`, default `0 0 3 * * *` in clinic timezone) regenerates the rolling window
- Schedule changes and time‑off edits regenerate immediately
- Public search matches doctor name or specialty name, case‑insensitively
- `page` clamped to `>= 0`, `size` to `1..100` (default `20`); `sort` accepts `fullName`, `specialty`, `consultationFee`, `createdAt` with optional `,asc` or `,desc`
- Admin delete is a soft delete: doctor becomes inactive (hidden from public search) and user account is disabled

### Configuration (application.yml / application-local.yml)
| Variable | Default (local) | Purpose |
|---|---|---|
| `CLINIC_TIME_ZONE` | `Asia/Kolkata` | Zone for working hours & clinic dates |
| `SLOT_HORIZON_DAYS` | `30` | Bookable slots horizon |
| `SLOT_NIGHTLY_CRON` | `0 0 3 * * *` | Nightly slot‑extension cron (clinic TZ) |
| `SEED_ENABLED` | `true` | Create fake demo data on startup |
| `SLOT_INTERVAL_MINUTES` | `30` | Slot duration |
| `DOCTOR_CONSULTATION_FEE` | `0` | Default fee for seeded doctors |

### Seed data (local profile only)
On first start with `local` profile, six fake specialties and doctors are created idempotently.
Every demo doctor signs in with password `Doctor123!`. Set `SEED_ENABLED=false` to disable;
the runner never runs in tests or production.

### Tests
- Slot generation edge cases (time off, UTC/ DST, overlapping hours)
- Search queries on MySQL and PostgreSQL — dual‑database compatibility
- Idempotency of slot regeneration

---

## 🌐 Frontend — Phase 2 Changes

### Pages & Hooks
- `src/pages/DoctorsPage.tsx` — public doctor listing with filters, pagination, and slot lookup
- `src/hooks/useDoctors.ts` — query helpers: `listDoctors`, `getDoctor`, `getDoctorSlots`
- `src/features/doctor/DoctorDetailPage.tsx` — doctor profile, working hours, time off, slot booking

### MSW Mocks
- `src/mocks/handlers.ts` — intercepts `/api/v1/...` requests for doctors, specialties, slots
- `src/mocks/db.ts` — in‑memory seed data (6 specialties + doctors)

### TypeScript Types
- `src/api/types.ts` — `Specialty`, `DoctorDTO`, `TimeSlot`, `WorkingHours`, `TimeOff`, search params
- Generated client via `npm run generate-api` when the OpenAPI spec is available

### Notes
- By default `VITE_API_MOCKING=enabled`; set to `disabled` to hit the real backend
- Access token kept in memory only (never `localStorage`)
- Backend time zone is UTC; client exchanges ISO‑8601 UTC strings

---

## 📦 Quick Start

### Backend
```bash
cd backend
mvn spring-boot:run          # or: ./mvnw spring-boot:run
# -DskipTests for faster startup
# Profile: `local` (default) creates seed data
```

### Frontend
```bash
cd frontend
npm install
npm run dev                  # http://localhost:5173
# Set VITE_API_MOCKING=disabled to use real backend
```

### Docker (if available)
```bash
docker-compose up -d          # (not yet configured; see README for Phase 7+)
```

---

## 📄 Change Report (since Phase 1 — auth)

| Area | Change |
|---|---|
| **Backend** | Migrations `V2__clinic_domain.sql`; doctor/admin/specialty CRUD; working‑hours & time‑off slot logic; nightly regeneration job; seed data runner; 37 new unit tests |
| **Frontend** | Doctors page + hook; MSW mocks for all Phase 2 endpoints; TypeScript types; 92 new vitest cases |
| **Validation** | Strict email `@ValidEmail` annotation; duplicate JSON key rejection; UTF‑8 byte limits on passwords; phone/name patterns |
| **Security** | CSRF disabled, stateless JWT, rate limiting per IP, CORS configured for `localhost:5173` |
| **CI/CD** | GitHub Actions ready (Testcontainers‑enabled database tests) |

---

## 🛣 Roadmap

| Phase | Target | Key Deliverables |
|---|---|---|
| **Phase 1** | ✅ Auth (register, login, JWT, refresh, logout) | Email validation, rate limiting, in‑memory tokens |
| **Phase 2** | ✅ Clinic domain (doctors, specialties, slots) | Migrations, CRUD, search, working hours, time off, slot generation |
| **Phase 3** | 🟡 Appointments & booking | Book/cancel/confirm slots, patient appointments endpoint |
| **Phase 4** | 📅 Reviews & notifications | Doctor reviews, notification system |
| **Phase 5** | 🏗 CI/CD & Docker | Full pipeline, PostgreSQL production profile |
| **Phase 6** | 📊 Analytics & reporting | Usage stats, dashboard, export features |

---

## 📞 Need Help?

- Check the `CHANGELOG.md` (if present) for per‑commit details
- See `docs/api/openapi.json` for the live OpenAPI contract
- Ask in the `#clinicbook` channel or open a GitHub Issue