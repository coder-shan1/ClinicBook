# AGENTS.md — Rules for AI agents working on ClinicBook

Read this file first, then `.agents/ARCHITECTURE.md` and `.agents/roadmap.md`. Applies to OpenCode, Kiro, and any other agent.

## 1. Project in one paragraph

ClinicBook is a clinic appointment booking platform: Spring Boot 3 (Java 21) + React/Vite/TypeScript, MySQL locally, PostgreSQL (Neon) in production, deployed on Render, built and tested by GitHub Actions. The goal is a portfolio-grade project that shows secure, tested, containerized, deployed full-stack Java work.

## 2. Before you start any task

1. Read the current phase in `.agents/roadmap.md`. Work only on the current phase unless told otherwise.
2. Check `git status` and the current branch. Never work on `main` or `develop`.
3. Restate the task, list the files you expect to touch, and note anything ambiguous. Ask when the answer would change the design; otherwise state an assumption and continue.
4. Prefer the smallest change that satisfies the task.

## 3. Commands

```bash
# Backend (from backend/)
mvn -B verify                          # build + all tests
mvn spring-boot:run -Dspring-boot.run.profiles=local
mvn -Dtest=BookingConcurrencyIT test   # single test

# Frontend (from frontend/)
npm ci
npm run dev
npm run lint && npm run typecheck && npm test -- --run
npm run build

# Whole stack (from repo root)
docker compose up --build
docker compose down -v                 # reset local DB volume
```

Run the relevant checks before declaring a task done. If you cannot run something, say so explicitly. Never claim a test passed without running it.

## 4. Backend rules

- Java 21, records for DTOs, constructor injection only (no field `@Autowired`), Lombok allowed for entities only.
- Layering: `controller → service → repository`. Controllers contain no business logic. Services own transactions (`@Transactional`). Repositories contain no logic beyond queries.
- Feature-first packages (`appointment`, `doctor`, ...). Do not create global `controllers/` or `services/` folders.
- Never return entities from controllers. Map to DTOs.
- Validate input with Jakarta Validation on request DTOs. Business rule violations throw typed exceptions handled by one `@RestControllerAdvice` returning the standard error shape.
- Use `Optional`/exceptions properly, not `null` returns. No `System.out`; use SLF4J.
- Time: store UTC (`Instant`/`OffsetDateTime`), convert at the edge. Never use `new Date()`.
- Config via `application.yml` and env vars. No secrets, URLs, or credentials in code.
- Add Javadoc only where intent is non-obvious. Comments explain why, not what.

## 5. Database rules (critical)

- Flyway owns the schema. `ddl-auto=validate`. Never edit an applied migration; add a new one.
- **Every migration must be created in both** `db/migration/mysql/` and `db/migration/postgresql/` with the **same filename**. CI fails otherwise.
- Keep JPA dialect-neutral: no native SQL unless unavoidable (then justify in a comment and test on both DBs), no DB `ENUM` types, no vendor-specific functions or JSON types.
- Always add indexes for foreign keys and for columns used in filters/sorting.
- Booking correctness depends on the unique `(doctor_id, start_at)` constraint and `@Version` on `TimeSlot`. Do not weaken either.

## 6. Frontend rules

- React function components, TypeScript strict mode, no `any` without a comment.
- Server state via TanStack Query; local UI state via hooks. No global state library unless justified.
- API calls only through `src/api/`. Types come from the generated OpenAPI client; do not hand-write duplicates.
- Feature folders under `src/features/`. Shared UI under `src/components/`.
- Tailwind for styling. Accessible markup (labels, roles, keyboard focus). Handle loading, empty, and error states on every data view.
- Never store the access token in `localStorage` if a safer option is in the current design; follow the auth approach in `ARCHITECTURE.md` §8.

## 7. Security rules (non-negotiable)

- Never commit secrets, `.env`, tokens, or real patient-like data. Update `.env.example` when adding a variable.
- All endpoints are authenticated unless explicitly listed as public in `ARCHITECTURE.md` §7.
- Enforce authorization in the service layer (ownership checks), not only in the URL rules.
- Passwords: BCrypt. Tokens: signed, short-lived. Do not log tokens, passwords, or full request bodies.
- Seed/demo data is fake. Use obviously fake emails (`@example.com`).
- Treat user input as hostile: validate, bound sizes, parameterize queries.

## 8. Division of labour between tools

| Area | Primary tool | Notes |
|---|---|---|
| Requirements → design → tasks for a new feature | Kiro (spec mode) | Save the spec in `docs/specs/<feature>/` |
| Frontend features and UI | Kiro | Uses generated API client |
| Backend internals, refactors, bug hunts | OpenCode | |
| Docker, compose, GitHub Actions, scripts | OpenCode | |
| README, ADRs, docs | Either; review by human | |
| Code review of the other tool's PR | The other tool | Cross-review catches blind spots |

Coordination rules:

- Never run both tools on the same branch at the same time. One task, one branch, one tool.
- Branch naming for agent work: `ai/<tool>/<short-name>` (for example `ai/opencode/dockerize`).
- For parallel work use `git worktree add ../clinicbook-kiro ai/kiro/booking-ui`.
- If the other tool changed shared contracts (OpenAPI, DTOs, migrations), regenerate/rebase before continuing.
- Leave a short handoff note in the PR description: what changed, what's left, how to verify.

## 9. Git workflow

- Follow the branching model in `ARCHITECTURE.md` §10. Branch from `develop`. Merge by PR only.
- Conventional Commits: `feat(scope): ...`, `fix`, `test`, `docs`, `chore`, `ci`, `refactor`.
- Small, focused commits. No unrelated formatting changes. No commented-out code.
- Do not force-push shared branches. Do not rewrite history on `main` or `develop`.
- PR description uses `.github/pull_request_template.md`: summary, changes, how tested, screenshots (UI), checklist.

## 10. Testing expectations

| Change | Required tests |
|---|---|
| Service logic | Unit tests (JUnit 5 + Mockito), including failure paths |
| Repository/queries | Integration test with Testcontainers (run on MySQL and Postgres) |
| Controller/security | `@WebMvcTest` or MockMvc tests incl. 401/403/validation cases |
| Booking flow | Concurrency test: N threads, one slot, exactly one success |
| React feature | Vitest + Testing Library for the main flow and error state |

A bug fix starts with a failing test that reproduces it.

## 11. Definition of done

A task is done only when:

- [ ] Code compiles; relevant test suites pass locally (state which you ran)
- [ ] New behaviour has tests; no tests were deleted or weakened to make things pass
- [ ] Lint/typecheck clean
- [ ] Migrations exist for both MySQL and Postgres (if the schema changed)
- [ ] `.env.example`, OpenAPI annotations, and README updated if relevant
- [ ] `.agents/roadmap.md` checklist updated for completed items
- [ ] Commits follow Conventional Commits; PR description is filled in

## 12. Ask first, don't assume

Stop and ask the human before:

- Adding a new major dependency or framework
- Changing the database schema in a way that rewrites existing tables or data
- Changing the auth/token design or role model
- Altering CI/CD, deployment settings, or branch protection
- Deleting files or branches you did not create
- Anything that touches real credentials or production data

## 13. Output style

- Be concise. Lead with what you changed and why, then how to verify.
- Show diffs or file paths rather than pasting whole files back.
- Flag risks, TODOs, and assumptions explicitly at the end.
- If you are unsure, say so. Do not invent APIs, library methods, or config keys; check the docs or the code.
