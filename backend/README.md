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

## Configuration

| Variable | Default (local) | Purpose |
|---|---|---|
| `CLINICBOOK_DB_URL` | `jdbc:mysql://localhost:3306/clinicbook?...` | JDBC URL |
| `CLINICBOOK_DB_USER` | `clinicbook` | Database user |
| `CLINICBOOK_DB_PASSWORD` | `clinicbook` | Database password |
| `DB_POOL_SIZE` | `10` | Hikari maximum pool size |
| `SERVER_PORT` | `8080` | HTTP port |

Only `health` and `info` Actuator endpoints are exposed. Production profile and
PostgreSQL settings arrive in Phase 7.

## Migrations

Flyway resolves `classpath:db/migration/{vendor}`, so migrations live in
`src/main/resources/db/migration/mysql/` and `.../postgresql/`. Every migration must
exist in both folders with the same filename.

## Package layout

Feature-first (`auth`, `user`, `doctor`, `schedule`, `appointment`, `review`,
`notification`, `admin`). Cross-cutting code goes in `config` and `common`.
