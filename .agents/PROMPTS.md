# ClinicBook — Reusable Prompts

Copy, fill the `{{placeholders}}`, and paste into OpenCode or Kiro. Every prompt assumes the agent can read the repo. Tool hints: 🟦 Kiro · 🟧 OpenCode · ⚪ either.

**Habits that make these work**

- Start each session with Prompt 1. Agents forget; the files don't.
- One task per prompt, one branch per task.
- Ask for a plan first on anything bigger than ~3 files.
- Always end with "how to verify". Run the checks yourself.

---

## A. Session and planning

### 1. Context loader ⚪ (start of every session)

```text
Read these files fully before doing anything:
- .agents/AGENTS.md
- .agents/ARCHITECTURE.md
- .agents/roadmap.md

Then:
1. Tell me the current phase and the next three unchecked roadmap items.
2. Run `git status` and `git branch --show-current` and tell me where we are.
3. List anything in those files that conflicts with the current code.
Do not change any files yet.
```

### 2. Feature spec 🟦

```text
Create a spec for the feature "{{feature name}}" in docs/specs/{{feature-slug}}/ using requirements, design, and tasks.

Context: ClinicBook, phase {{n}} in .agents/roadmap.md. Follow .agents/ARCHITECTURE.md and .agents/AGENTS.md.

Requirements: write user stories with acceptance criteria in EARS style (WHEN ... THE SYSTEM SHALL ...). Cover the happy path, validation errors, authorization (PATIENT / DOCTOR / ADMIN), and concurrency where relevant.
Design: list affected packages, entities, DTOs, endpoints (method, path, role, status codes), migrations for both MySQL and PostgreSQL, and error codes.
Tasks: ordered, small, each independently testable, each naming the tests to add.

Flag open questions at the top. Do not write production code yet.
```

### 3. Plan before coding ⚪

```text
Task: {{task description}}

Before writing code, give me a plan:
1. Files to create or modify (with one line each on why)
2. Public API/DTO/schema changes
3. Risks and edge cases
4. Tests you will add
5. Questions that would change the design

Wait for my approval before implementing.
```

---

## B. Backend

### 4. New feature package scaffold 🟧

```text
Scaffold the backend package `com.clinicbook.{{feature}}` following AGENTS.md section 4.

Create: entity, repository, service, controller, request/response DTO records, mapper, and a feature-specific exception if needed.
Requirements:
- Constructor injection, DTO records, no entities in controllers
- Validation annotations on request DTOs
- OpenAPI annotations (@Operation, @ApiResponse) on controller methods
- Endpoints: {{list endpoints with roles}}
- Empty-but-compiling tests for service and controller

Stop after scaffolding. Show the file tree and run `mvn -B -q compile`.
```

### 5. Entity + dual-dialect migration 🟧

```text
Add the entity `{{Entity}}` with fields: {{field list with types, nullability, constraints}}.

Deliver:
1. JPA entity (dialect-neutral, enums as STRING, UTC timestamps)
2. Flyway migration `V{{n}}__{{name}}.sql` in BOTH db/migration/mysql and db/migration/postgresql with identical filenames
3. Indexes for foreign keys and for {{filter/sort columns}}
4. Repository with only the queries we need: {{queries}}
5. A Testcontainers integration test that applies all migrations and exercises the repository

Rules: no native SQL unless unavoidable, no ENUM types, no vendor-specific functions. Explain any difference between the two migration files. Run the tests against both databases.
```

### 6. Service with business rules + tests 🟧

```text
Implement `{{Service}}.{{method}}` in the `{{feature}}` package.

Business rules:
{{numbered rules}}

Requirements:
- @Transactional boundaries chosen deliberately (explain in one sentence)
- Ownership/authorization enforced in the service
- Typed exceptions mapped by the global handler (list the error codes used)
- Unit tests (JUnit 5 + Mockito): one per rule, plus failure paths and boundary values
- Do not weaken or delete existing tests

Show test names first as a checklist, then implement. Run `mvn -B test -Dtest={{TestClass}}`.
```

### 7. Booking with optimistic locking + concurrency test 🟧

```text
Implement `AppointmentService.book(patientId, slotId, reason)`.

Behaviour:
- In one transaction: load the TimeSlot, require status AVAILABLE and startAt in the future, set BOOKED, insert Appointment (PENDING).
- TimeSlot has @Version. On OptimisticLockingFailureException or a unique-constraint violation, throw SlotTakenException which the handler maps to HTTP 409 with error code SLOT_TAKEN.
- A patient cannot exceed {{n}} active appointments.

Then write `BookingConcurrencyIT`: {{20}} threads released together by a CountDownLatch all try to book the same slot. Assert exactly one success, the rest get SlotTakenException, and exactly one Appointment row exists. Run it on both MySQL and PostgreSQL.

If you find a race the design does not cover, stop and explain it instead of patching around it.
```

### 8. Security configuration (JWT) 🟧

```text
Implement JWT auth per ARCHITECTURE.md section 8.

- Access token 15 min, refresh token 7 days, stored hashed, rotated on every use, revoked on logout
- JwtService (sign/parse/validate), JwtAuthenticationFilter, SecurityFilterChain (stateless, CORS from env, public endpoints from ARCHITECTURE.md section 7)
- 401 and 403 responses use the standard error JSON
- Secret from env var JWT_SECRET; fail fast on startup if missing or shorter than 32 bytes
- Tests: expired token, tampered token, wrong role, refresh reuse (second use of the same refresh token must fail and revoke the family)

List anything you consider a security weakness in what you built.
```

### 9. Bug fix, test first ⚪

```text
Bug: {{what happens}}. Expected: {{what should happen}}. Steps: {{repro steps or request}}.

Process:
1. Find the root cause. Explain it in 3 sentences before changing code.
2. Write a failing test that reproduces it. Run it and show it failing.
3. Make the minimal fix. Run it and show it passing.
4. Run the full suite for the affected module.
5. List any similar code that might share the bug.

Do not refactor unrelated code in this change.
```

### 10. Safe refactor ⚪

```text
Refactor {{target}} to {{goal, e.g. remove duplication / split responsibilities}}.

Constraints:
- Behaviour must not change; public API and DB schema stay the same
- Confirm tests exist for the current behaviour first. If coverage is thin, add characterization tests in a separate commit before refactoring
- Small commits, each leaving the build green
- Run the full test suite after each step

Summarize what moved, what was renamed, and anything you deliberately left alone.
```

### 11. Generate missing tests ⚪

```text
Analyze `{{class or package}}` and list untested behaviours (branches, error paths, boundaries, authorization cases). Present the list ranked by risk.

After I approve, write the tests. Rules: meaningful assertions (no tests that only call methods), descriptive names (`shouldRejectBookingWhenSlotAlreadyBooked`), one behaviour per test, no mocking of the class under test, no sleeping. Report coverage before and after.
```

---

## C. Frontend

### 12. Feature page / component 🟦

```text
Build the `{{feature}}` UI in frontend/src/features/{{feature}}.

Screens/behaviour: {{description}}
API: use the generated client in src/api for {{endpoints}}; do not hand-write duplicate types.
Requirements:
- TypeScript strict, function components, TanStack Query for server state
- Loading, empty, and error states; handle 401 (redirect to login) and 409 (show a clear message and refetch)
- Accessible: labels, keyboard navigation, focus management for dialogs
- Responsive with Tailwind (mobile first)
- Vitest + Testing Library tests for the main flow and one error state

Show me a short component tree first, then implement. Finish by running lint, typecheck, tests, and build.
```

### 13. Sync the API client 🟦

```text
The backend OpenAPI spec changed. Regenerate the TypeScript client in frontend/src/api from http://localhost:8080/v3/api-docs (use {{openapi-typescript | orval | tool already in the repo}}).

Then fix every compile error caused by the changes, update affected components and tests, and list each breaking change (renamed/removed fields) you had to adapt to. Do not edit generated files by hand.
```

---

## D. DevOps and delivery

### 14. GitHub Actions CI pipeline 🟧

```text
Create or update .github/workflows/ci.yml per ARCHITECTURE.md section 11.

Jobs:
- backend: Java 21 Temurin, Maven cache, `mvn -B verify`, matrix db: [mysql, postgres] (service containers or Testcontainers), upload JaCoCo report
- frontend: Node 20, npm cache, lint, typecheck, test, build
- migrations-parity: fail if db/migration/mysql and db/migration/postgresql file names differ
- docker: build both images without pushing

Requirements: triggers on PR and push to develop and main, concurrency group cancelling superseded runs, least-privilege `permissions`, actions pinned to a major version, no secrets in logs. Validate YAML syntax. Explain what each job protects against, and which job names I should mark as required status checks.
```

### 15. Dockerfiles + compose 🟧

```text
Create backend/Dockerfile, frontend/Dockerfile, frontend/nginx.conf, docker-compose.yml, and .dockerignore files.

Backend: multi-stage (maven + temurin 21 build, temurin 21 JRE runtime), dependency layer caching, non-root user, HEALTHCHECK on /actuator/health, JAVA_OPTS support.
Frontend: node build stage, nginx:alpine runtime, SPA fallback, gzip, VITE_API_URL as build arg.
Compose: mysql:8 with volume and healthcheck, backend waits for healthy db, frontend, Mailpit; everything configured through .env with .env.example updated.

Verify with `docker compose up --build`, curl the health endpoint, and report image sizes. Note anything that would behave differently on Render.
```

### 16. Render + Neon production config 🟧

```text
Prepare production deployment on Render (backend + frontend) with Neon PostgreSQL.

1. application-prod.yml: Postgres datasource from env (DB_URL, DB_USER, DB_PASSWORD), Flyway location classpath:db/migration/postgresql, Hikari max pool 5 with sensible timeouts for Neon's idle suspend, ddl-auto validate, no SQL logging, Actuator limited to health/info
2. render.yaml (or step-by-step manual config) for both services including health check path and env var names (no values)
3. deploy.yml triggering Render deploy hooks on push to main after CI passes
4. A checklist of every secret/variable I must set manually in GitHub, Render, and Neon

Call out Neon connection string format (sslmode=require) and cold-start risks. Never put real credentials in any file.
```

### 17. README generator ⚪

```text
Write README.md for ClinicBook for a recruiter audience. Read the code and .agents/ARCHITECTURE.md first and describe only what actually exists.

Sections: one-line pitch, live demo link placeholder, screenshot/GIF placeholders, key features, "Engineering highlights" (booking concurrency, dual-DB migrations, CI matrix, JWT rotation), architecture diagram (Mermaid), ER diagram (Mermaid), tech stack table, run locally (Docker and manual), environment variables table, API overview with Swagger link, testing instructions, CI/CD overview, deployment notes (Render + Neon, cold starts), project structure, roadmap, license.

Style: concise, scannable, no marketing fluff, no claims I cannot verify. Mark anything you could not verify with TODO.
```

---

## E. Quality, review, handoff

### 18. Cross-tool code review ⚪ (use the other tool than the author)

```text
Review the changes on branch `{{branch}}` against `develop` (git diff develop...{{branch}}).

Check, in this order:
1. Correctness bugs and unhandled edge cases
2. Security (authz gaps, injection, secrets, token handling, logging of sensitive data)
3. Concurrency and transaction boundaries
4. Dual-dialect database compatibility (MySQL vs PostgreSQL) and migration parity
5. Test quality (are tests asserting real behaviour? anything weakened?)
6. Violations of .agents/AGENTS.md
7. Readability and naming

Output: a table of findings with severity (blocker/major/minor/nit), file:line, problem, suggested fix. End with a go/no-go verdict. Do not modify files.
```

### 19. Security audit ⚪

```text
Audit the whole backend for security issues. For every controller method, produce a table: endpoint, intended roles, roles actually enforced (URL rules + @PreAuthorize + service ownership checks), and gaps.

Also check: password and token handling, CORS, error messages leaking internals, logging of sensitive data, mass-assignment via DTOs, pagination limits, rate limiting, dependency vulnerabilities, hard-coded secrets. Rank issues by exploitability and propose fixes. Write failing tests for confirmed issues before fixing.
```

### 20. Commit message and PR description ⚪

```text
Look at `git diff develop...HEAD` and `git log develop..HEAD`.

1. Propose a Conventional Commits message for a squash merge (type(scope): summary, then a short body explaining why).
2. Fill .github/pull_request_template.md: summary, what changed, how I tested it (only commands that were actually run), screenshots needed, risks, and follow-ups.
3. Mention any migration, env var, or breaking API change in a clearly marked line.
```

### 21. Handoff and roadmap update ⚪ (end of every session)

```text
Wrap up this session:
1. Tick completed items in .agents/roadmap.md and add a row to the progress log
2. Write a handoff note: what was done, what is half-done, exact next step, commands to verify, known issues
3. Check that .env.example, README, and OpenAPI annotations reflect today's changes
4. Run the full checks for the areas touched and report results honestly, including failures

Do not start new work.
```

### 22. Interview prep from your own code ⚪

```text
Act as a technical interviewer for a Java internship. Based on the actual code in `{{package or feature}}`, ask me 10 questions of increasing difficulty (why this design, what happens if X fails, how would you scale it, trade-offs vs alternatives). After each of my answers, grade it, point out gaps, and give a stronger answer I could use. Start with question 1 and wait for my reply.
```

---

## Prompt-writing checklist

A strong prompt has: **goal** (what and why), **context** (files, phase, rules to follow), **constraints** (what not to do), **deliverables** (exact outputs), **verification** (commands to run), and **stop condition** (when to ask or halt).
