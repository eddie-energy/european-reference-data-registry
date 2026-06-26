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
