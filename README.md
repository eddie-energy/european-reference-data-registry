# ceeds-backend

S3 federation service. Java 21 / Spring Boot backend.

## Stack
- Java 21, Spring Boot 3.5 (web, data-jpa, actuator)
- PostgreSQL + Flyway migrations
- Lombok, MapStruct, springdoc OpenAPI
- errorprone + NullAway, Jacoco, Jib

## Layout
- `backend/` — backend Gradle module
- `frontend/` — Vue app (added later)

## Build & run
```sh
# local Postgres
(cd backend/env && cp .env .env.local)   # fill in DB creds
docker compose -f backend/env/docker-compose.yaml up -d postgres

# build (Docker required; tests start their own PostgreSQL container)
./gradlew :backend:build

# run (set backend.db.* or use application-local.yaml)
./gradlew :backend:bootRun
```

- Health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html

## Organization responsibilities

Operational Entity users assign NDSF organizations to reference data objects and nations at `/management/responsibilities`. Assignment nation may differ from the organization's `ceeds_nations` in Keycloak. NDSF members need a matching assignment to maintain national entries or add national fields.

Local Keycloak bootstrap creates the `ceeds-directory` service account. For Helm, add `directory-client-secret` to the existing `keycloak-secret` before upgrading. The chart passes this secret to Keycloak and backend and provisions `view-organizations` on the service account. Existing realms are updated by the bootstrap job. Keycloak 26.7.4 is required for this narrow read permission.

## Tests

```sh
./gradlew :backend:test             # unit tests; no Docker or database required
./gradlew :backend:integrationTest  # starts disposable PostgreSQL 17 via Testcontainers
./gradlew build                     # includes both suites
```

Integration tests require a working Docker daemon and access to container images. No manually started PostgreSQL or Keycloak instance is needed. Missing Docker fails the integration suite rather than skipping tests.
