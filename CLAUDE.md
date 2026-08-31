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
  `ReferenceDataEntriesApi`, `ReferenceDataObjectDto`, etc. Runs automatically before `:api:compileJava`.
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
`ReferenceDataEntry` belongs to the **ReferenceDataObject, not a version** — it survives into later
versions and its stored values are projected onto whatever version is being read. Each
`ReferenceDataEntryValue` is one typed slot per field. A reference data entry carries an optional
`nation`; a field applies when it is shared (`nation == null`) or matches the reference data entry's
nation.

## Database & migrations

- PostgreSQL, Flyway (`classpath:db/backend/migration`), `hibernate.ddl-auto: none` — schema is owned
  entirely by migrations, not entities.
- Local DB runs on **port 5440** (`docker compose -f backend/env/docker-compose.yaml up -d postgres`,
  needs `backend/env/.env`). Default creds `backend/backend/backend` come from `application.yaml`.
- **Never edit an already-applied migration** — Flyway validates checksums on startup and a changed
  file aborts context load with "checksum mismatch". Add a new `V1_x__*.sql` instead, even for a
  feature that hasn't merged yet, if its earlier migration has run against any local DB.

## Authentication & authorization

Keycloak runs in the same compose file on **port 8081**, realm `ceeds`, public client `ceeds-frontend`,
seeded dev user `ceeds`/`ceeds`. The realm is provisioned from `backend/env/keycloak/ceeds-realm.json`
via `start-dev --import-realm`, so it is re-imported on every container start — change the realm by
editing that file, not in the admin console. Self-registration is on and the registration form is
reduced to username + password.

Anything realm import cannot express lives in `backend/env/keycloak/org-bootstrap.sh`, run by the
one-shot `keycloak-bootstrap` compose service after Keycloak reports healthy. It is idempotent and
re-runs on every `docker compose up`.

### Roles come from Keycloak Organizations

Roles are assigned to **organizations**, not users (issue #144). An organization carries two
attributes, `ceeds_role` (`NDSF` / `OPERATIONAL_ENTITY`, multivalued) and `ceeds_nations`
(`AUT`/`FRA`/`ESP`/`GER`, only meaningful with `NDSF`, so an Operational Entity carries none). Seed
org: `fhooe` = Operational Entity, with the `ceeds` user as its member — the dev login therefore has
full rights. Change it by editing the `ORGANIZATIONS` array (`alias|name|domain|roles|nations`) in
`org-bootstrap.sh` and re-running that service. A user's effective roles are the **union** over every organization they
belong to.

The organization membership mapper is configured with *add organization id* and *add organization
attributes*, and the `organization` client scope is a **default** scope of `ceeds-frontend`, so the
access token carries:

```json
"organization": { "fhooe": { "id": "…", "ceeds_role": ["NDSF"], "ceeds_nations": ["AUT"] } }
```

`security/OrganizationClaim` parses that (tolerating the list-shaped claim you get without those
mapper options, and string-or-array attribute values); `security/OrganizationRolesConverter` turns it
into authorities — `ROLE_PARTICIPANT` for every valid token, plus `ROLE_NDSF` /
`ROLE_OPERATIONAL_ENTITY` and one `NDSF_NATION_<code>` per nation. `security/CurrentUser` is the only
place that reads `SecurityContextHolder`; services ask it, nothing else.

| Role | Who | May |
|---|---|---|
| `VIEWER` | anonymous | read published reference data objects and their reference data entries |
| `PARTICIPANT` | any valid token | (API tokens — not built yet) |
| `NDSF` | org attribute, per nation | create/update/delete reference data entries and add fields **of its nations**; see drafts |
| `OPERATIONAL_ENTITY` | org attribute | manage objects, versions, fields; see drafts. **Only** role that creates versions |

- Enforcement lives in `SecurityConfig`'s `authorizeHttpRequests` matchers, **not** `@PreAuthorize`:
  `@EnableMethodSecurity` JDK-proxies the controllers, and in a `@WebMvcTest` slice (no
  `AopAutoConfiguration`) the proxies stop being registered as handlers, so every write 404s/405s.
- Rule ordering matters — the SPA deep-link patterns (`/{a}/{b}`) also match `/api/...`, so every
  `/api/**` rule must be declared before `PUBLIC_PATHS`, and the reference data entry paths before the broader
  reference-data-object rule.
- The **nation** check cannot be expressed as a matcher (it depends on the request body or the stored
  reference data entry), so `ReferenceDataEntryService` calls
  `CurrentUser.mayMaintainReferenceDataEntriesFor` and throws `ForbiddenException`.
- Reads filter drafts server-side, and both services ask `CurrentUser.maySeeDrafts()` (Operational
  Entity **or** NDSF — an NDSF has to reach a draft to add its national fields to it):
  `ReferenceDataObjectService` drops non-`PUBLISHED` versions (and objects left with none) for
  everyone else, and `ReferenceDataEntryService.findVersion` 404s a draft version for them too. Keep the two in
  step — if only one filters, the other's endpoint 404s on a version the caller was just shown.
- The frontend uses `check-sso`, mounts whether or not you are signed in, and reads its role from
  `GET /api/me` (`frontend/src/stores/userInfo.ts`) — there is no self-selected role any more.

## Frontend-in-backend packaging

The backend serves the built Vue app: the `buildFrontend` Gradle task runs `:frontend:build`, copies
`frontend/dist` → `backend/src/main/resources/public`, then moves `index.html` on into
`backend/src/main/resources/templates`, where **Thymeleaf renders it** to inject the Keycloak host,
realm and client at request time (`controllers/UiController.java`). `processResources` depends on it.
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
./gradlew :backend:test --tests 'energy.eddie.s3.services.ReferenceDataEntryServiceTest'                                      # single class
./gradlew :backend:test --tests 'energy.eddie.s3.services.ReferenceDataEntryServiceTest.createReferenceDataEntry_storesTypedValues'  # single method
./gradlew :api:generateServerApi    # regenerate Java API types from the spec

docker compose -f backend/env/docker-compose.yaml up -d   # postgres (:5440) + keycloak (:8081)
```

- Health: http://localhost:8080/actuator/health · Swagger UI: http://localhost:8080/swagger-ui.html
- Keycloak admin console: http://localhost:8081 (`admin`/`admin`, from `backend/env/.env`)

## Conventions & gotchas

- **No explanatory comments.** Don't write Javadoc, block comments, or inline comments that restate
  what the code does — in Java, TypeScript, SQL, shell, or anywhere else. Name things so the code
  reads on its own. This applies to new code and to code you touch. OpenAPI `description:` fields and
  the docs in this file are documentation, not code comments, and stay.
- **NullAway** runs as an ERROR-level errorprone check over package `energy.eddie.s3` (generated code
  excluded). Annotate nullable fields/params/returns with `@Nullable`; a missed one fails the build,
  not just a warning.
- Test split is by name: any class ending `IntegrationTest` is excluded from `test` and only runs
  under `integrationTest` (which needs Postgres up).
- Dependency versions are locked (`dependencyLocking` / `*.lockfile`).
- **Commit messages** must start with an issue reference or `NOISSUE`, matching
  `^((#|GH-|gh-)[0-9]+|NOISSUE).+` (CI-enforced on non-`main` branches).
