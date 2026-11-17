# ClinicBook Frontend

React + Vite + TypeScript + Tailwind client for the ClinicBook API.

## Stack

- React 18, Vite 5, TypeScript (strict), Tailwind 3
- TanStack Query (server state), React Router (routing)
- Axios API client in `src/api`
- MSW — optional mock data for running without a backend
- Vitest + Testing Library (tests)

## Quick start

```sh
npm install
npm run dev          # http://localhost:5173
```

The app talks to the Spring Boot backend on `http://localhost:8080` (proxied at
`/api`). To run the UI without a backend, copy `.env.example` to `.env` and set
`VITE_API_MOCKING=enabled`; MSW then serves the full booking flow in memory.

Mock accounts (password is any value, e.g. `pass`):

| Email                 | Role    |
| --------------------- | ------- |
| `patient@example.com` | PATIENT |
| `doctor@example.com`  | DOCTOR  |
| `admin@example.com`   | ADMIN   |

## Features

- Doctor search with specialty filter, sorting and pagination
- Doctor profile with a date strip + time-slot picker
- Booking confirmation step; a `409 SLOT_TAKEN` refreshes the slots and shows a
  friendly retry message
- “My appointments” for patients (cancel, 2-hour cutoff) and the doctor’s list
  (confirm / complete / no-show)
- Doctor schedule editor: weekly working hours, time off and slot regeneration
- Role-aware navigation and protected routes

## Scripts

| Script                 | Purpose                                                        |
| ---------------------- | -------------------------------------------------------------- |
| `npm run dev`          | Vite dev server                                                |
| `npm run build`        | `tsc --noEmit && vite build`                                   |
| `npm run lint`         | `eslint .`                                                     |
| `npm run typecheck`    | `tsc --noEmit`                                                 |
| `npm test -- --run`    | Vitest once                                                    |
| `npm run generate-api` | Regenerate `src/api/generated/schema.ts` from the OpenAPI spec |
| `npm run msw:init`     | (Re)create `public/mockServiceWorker.js`                       |

## Connecting to the real backend

The backend (Spring Boot, default port `8080`) exposes its OpenAPI at
`http://localhost:8080/v3/api-docs` and Swagger UI at
`http://localhost:8080/swagger-ui.html`. It has no servlet context-path; API endpoints
sit under `/api/v1` (ARCHITECTURE.md §7).

Local dev with the real backend:

1. Start the backend from `../backend`: `mvn spring-boot:run`
2. Leave `VITE_API_MOCKING` unset (or `disabled`) and `VITE_API_URL` unset — the Vite
   dev server proxies `/api` -> `http://localhost:8080`, avoiding CORS.

Production: set `VITE_API_URL` to the absolute backend URL; the backend must allow
the frontend origin via CORS (ARCHITECTURE.md §8).

## API client types

`npm run generate-api` writes `src/api/generated/schema.ts`. Source priority:

1. `OPENAPI_SPEC` — explicit path to a spec file.
2. A committed spec at `../docs/api/openapi.json` (offline / CI).
3. The backend's live endpoint, `OPENAPI_URL` (default
   `http://localhost:8080/v3/api-docs`).

The generated schema is committed so a fresh checkout type-checks without the
backend. `src/api/types.ts` holds the app-facing aliases and includes compile-time
checks that they stay assignable to the generated contract. `src/api/endpoints.ts` is
the typed client the UI calls. Re-run `npm run generate-api` whenever the backend
contract changes.

## Notes

- Access token is kept in memory only (never `localStorage`) — ARCHITECTURE.md §8.
- The backend serializes JSON with `non_null` inclusion (null fields are omitted) and
  rejects unknown request properties (`fail-on-unknown-properties: true`). Keep request
  payloads exact.
- Timestamps cross the API as ISO-8601 UTC strings; the backend generates slots in the
  clinic time zone (`Asia/Kolkata` by default) and stores them as UTC. The UI labels
  slot times as UTC.
- Lifecycle rules: `PENDING -> CONFIRMED|CANCELLED`,
  `CONFIRMED -> COMPLETED|CANCELLED|NO_SHOW`; terminal states never move. Patients are
  bound by a 2-hour cancellation cutoff and a maximum of 3 active bookings.
