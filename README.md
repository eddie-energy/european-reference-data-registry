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

# build
./gradlew :backend:build

# run (set backend.db.* or use application-local.yaml)
./gradlew :backend:bootRun
```

- Health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html

## API

New REST endpoints for managing reference data objects, their versions, and fields. All under the `ReferenceData` tag.

### Reference Data Objects

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/reference-data-objects` | Get all reference data objects, including their versions and fields. |
| `POST` | `/reference-data-objects` | Create a reference data object with an initial draft version. Returns `201 Created`. |
| `GET` | `/reference-data-objects/{id}` | Get a single reference data object, including its versions and fields. |
| `DELETE` | `/reference-data-objects/{id}` | Delete a reference data object (only allowed if no fields exist). Returns `204 No Content`. |

### Versions

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/reference-data-objects/{id}/versions` | Create a new version that links all fields of the latest version. Returns `201 Created`. |
| `POST` | `/reference-data-objects/{id}/versions/{versionId}/publish` | Publish a version of a reference data object. |

### Fields

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/reference-data-objects/{id}/versions/{versionId}/fields` | Create a field and link it to a version. Returns `201 Created`. |
| `PUT` | `/reference-data-objects/{id}/versions/{versionId}/fields` | Replace the full set of fields linked to a version. Entries with an `id` keep an existing field linked; entries without an `id` create a new field; omitted fields are unlinked (and deleted if no other version uses them). Returns `200 OK`. |
| `DELETE` | `/reference-data-objects/{id}/versions/{versionId}/fields/{fieldId}` | Unlink a field from a version (deletes the field if no other version uses it). Returns `204 No Content`. |
