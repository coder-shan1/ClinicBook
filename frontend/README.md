# ClinicBook Frontend

React + Vite + TypeScript + Tailwind client for the ClinicBook API.

## Stack

- React 18, Vite 5, TypeScript (strict), Tailwind 3
- TanStack Query (server state), React Router (routing)
- Axios API client in `src/api`
- MSW — mock data for endpoints the backend has not built yet
- Vitest + Testing Library (tests)

## Quick start

```sh
npm install
npm run dev          # http://localhost:5173
```

By default the app uses MSW mock data (`VITE_API_MOCKING=enabled`), so no backend is
required. The Doctors page at `/doctors` is served by mock handlers.

## Scripts

| Script | Purpose |
|---|---|
| `npm run dev` | Vite dev server (mocks on by default) |
| `npm run build` | `tsc --noEmit && vite build` |
| `npm run lint` | `eslint .` |
| `npm run typecheck` | `tsc --noEmit` |
| `npm test -- --run` | Vitest once |
| `npm run generate-api` | Generate `src/api/generated/schema.ts` from the OpenAPI spec |
| `npm run msw:init` | (Re)create `public/mockServiceWorker.js` |

## Connecting to the real backend

The backend (Spring Boot, default port `8080`) exposes its OpenAPI at
`http://localhost:8080/v3/api-docs` and Swagger UI at
`http://localhost:8080/swagger-ui.html`. It has no servlet context-path; API endpoints
sit under `/api/v1` (ARCHITECTURE.md §7).

Local dev with the real backend:

1. Start the backend from `../backend`: `mvn spring-boot:run`
2. In `frontend/.env` set `VITE_API_MOCKING=disabled`. Leave `VITE_API_URL` unset —
   the Vite dev server proxies `/api` -> `http://localhost:8080`, avoiding CORS.

Production: set `VITE_API_URL` to the absolute backend URL; the backend must allow
the frontend origin via CORS (ARCHITECTURE.md §8).

## API client types

`npm run generate-api` writes `src/api/generated/schema.ts`. Source priority:

1. A committed spec at `../docs/api/openapi.json` (offline / CI), if present.
2. The backend's live endpoint, `OPENAPI_URL` (default
   `http://localhost:8080/v3/api-docs`).

Until the backend ships endpoints (Phase 1+), the spec is near-empty and the
hand-written types in `src/api/types.ts` are used. Replace those with the generated
client once endpoints exist (AGENTS.md §6: no hand-written duplicates of generated types).

## Notes

- Access token is kept in memory only (never `localStorage`) — ARCHITECTURE.md §8.
- The backend serializes JSON with `non_null` inclusion (null fields are omitted) and
  rejects unknown request properties (`fail-on-unknown-properties: true`). Keep request
  payloads exact.
- Backend time zone is UTC; the client exchanges ISO-8601 UTC strings.
- The `/api/v1` base path is an assumption from ARCHITECTURE.md §7. The Phase 0 backend
  has no context-path and no endpoints yet, so it is not yet verifiable against code.
