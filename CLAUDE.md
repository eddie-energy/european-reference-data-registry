# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo shape

Gradle multi-project (`rootProject.name = "ceeds-backend"`) with three modules:

- **`api/`** — no hand-written source. Generates Spring MVC interfaces + models from the OpenAPI
  contract (`api-specs/backend-api.yml`) into `energy.eddie.s3.generated.{api,model}`. Exposes them
  as `api` scope so `backend` gets them transitively.
- **`backend/`** — Java 21 / Spring Boot 3.5 service. Implements the generated interfaces.
- **`frontend/`** — Vue 3 app. Has its own `frontend/CLAUDE.md` — read it for frontend commands,
  conventions, and architecture; don't duplicate that here.

## Spec-first workflow (the central architectural fact)

`api-specs/backend-api.yml` is the **single source of truth** for the HTTP API. Both sides are
generated from it, never hand-edited to diverge:

- `api` module: `:api:generateServerApi` (OpenAPI Generator, `spring`, `interfaceOnly`) →
  `EntriesApi`, `ReferenceDataObjectDto`, etc. Runs automatically before `:api:compileJava`.
- frontend: `pnpm generate-openapi-schemas` → `frontend/src/schema.d.ts`.
- Swagger UI serves the authored spec itself (copied to static resources via `copyApiSpec`), not a
  definition re-derived from controllers.

**When changing the API: edit the YAML first, then regenerate both sides.** A backend build alone
regenerates the Java side; run `pnpm generate-openapi-schemas` (or `pnpm build`) for the TS side.

Note: OpenAPI `date-time` is mapped to `java.time.Instant` (see `api/build.gradle.kts` typeMappings).

## Backend layering

`controllers → services → repositories → JPA models`

- Controllers (`controllers/`) implement the generated `*Api` interfaces and do nothing but delegate;
  `GlobalExceptionHandler` turns domain exceptions (`exceptions/`, e.g. `NotFoundException`,
  `ConflictException`) into HTTP responses.
- Services (`services/`) hold all domain logic and transaction boundaries (`@Transactional`).
- Two enum/DTO worlds meet in the services: **generated model types** (`generated.model.*`) vs
  **domain/JPA types** (`models.referencedata.*`). They are distinct classes with the same names
  (e.g. `Nation` exists in both) — map between them explicitly (`Nation.valueOf(x.name())`) or via the
  MapStruct mappers in `mappers/`. Watch for the name clash; fully-qualify when both are in scope.

### Domain model (`models/referencedata/`)

`ReferenceDataObject` → many `ReferenceDataObjectVersion` (`publishState` DRAFT/PUBLISHED) → many
`Field` (`DataType` TEXT/NUMBER/DATE/ENUM, optional `nation`, ENUM fields own `EnumOption`s). An
`Entry` belongs to the **ReferenceDataObject, not a version** — it survives into later versions and its
stored values are projected onto whatever version is being read. Each `EntryValue` is one typed slot
per field. An entry carries an optional `nation`; a field applies to an entry when the field is shared
(`nation == null`) or matches the entry's nation.

## Database & migrations

- PostgreSQL, Flyway (`classpath:db/backend/migration`), `hibernate.ddl-auto: none` — schema is owned
  entirely by migrations, not entities.
- Local DB runs on **port 5440** (`docker compose -f backend/env/docker-compose.yaml up -d postgres`,
  needs `backend/env/.env`). Default creds `backend/backend/backend` come from `application.yaml`.
- **Never edit an already-applied migration** — Flyway validates checksums on startup and a changed
  file aborts context load with "checksum mismatch". Add a new `V1_x__*.sql` instead, even for a
  feature that hasn't merged yet, if its earlier migration has run against any local DB.

## Frontend-in-backend packaging

The backend serves the built Vue app: the `buildFrontend` Gradle task runs `:frontend:build` and
copies `frontend/dist` → `backend/src/main/resources/public`, and `processResources` depends on it.
So `:backend:build` / `:backend:bootRun` rebuild and bundle the frontend automatically. **Pressing
Play on `S3Application` in the IDE only refreshes the bundled frontend if IntelliJ is set to build via
Gradle** (Settings → Build Tools → Gradle → "Build and run using: Gradle"); otherwise run
`./gradlew :backend:buildFrontend` first, and hard-refresh the browser (hashed filenames + cached
`index.html`).

## Commands

```sh
./gradlew :backend:bootRun          # run (serves API + frontend on :8080)
./gradlew :backend:build            # compile + test + bundle frontend
./gradlew :backend:test             # unit tests only (excludes *IntegrationTest)
./gradlew :backend:integrationTest  # *IntegrationTest classes — needs a running DB
./gradlew :backend:test --tests 'energy.eddie.s3.services.EntryServiceTest'                       # single class
./gradlew :backend:test --tests 'energy.eddie.s3.services.EntryServiceTest.createEntry_storesTypedValues'  # single method
./gradlew :api:generateServerApi    # regenerate Java API types from the spec
```

- Health: http://localhost:8080/actuator/health · Swagger UI: http://localhost:8080/swagger-ui.html

## Conventions & gotchas

- **NullAway** runs as an ERROR-level errorprone check over package `energy.eddie.s3` (generated code
  excluded). Annotate nullable fields/params/returns with `@Nullable`; a missed one fails the build,
  not just a warning.
- Test split is by name: any class ending `IntegrationTest` is excluded from `test` and only runs
  under `integrationTest` (which needs Postgres up).
- Dependency versions are locked (`dependencyLocking` / `*.lockfile`).
- **Commit messages** must start with an issue reference or `NOISSUE`, matching
  `^((#|GH-|gh-)[0-9]+|NOISSUE).+` (CI-enforced on non-`main` branches).
