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

## CEEDS Keycloak

The custom Keycloak 26.7.5 image includes the realm, organization attribute
provider and automatic startup configuration. No realm/script mounts, separate
bootstrap container, Helm Job or manual setup commands are needed.

```sh
docker build -t ceeds-keycloak:26.7.5 backend/env/keycloak
docker compose -f backend/env/docker-compose.yaml up -d --build keycloak
```

Compose enables the development login `ceeds` / `ceeds` and membership in FHOOE.
The image and Helm disable demo-user creation by default. Disabling it later
does not delete an existing user or change their password.

Configure organizations using aligned, semicolon-separated environment values:

```yaml
CEEDS_BOOTSTRAP_ORGANIZATIONS: "fhooe;eda;enedis"
CEEDS_BOOTSTRAP_ORGANIZATION_ROLES: "OPERATIONAL_ENTITY;NDSF;NDSF"
CEEDS_BOOTSTRAP_ORGANIZATION_NATIONS: ";AUT;FRA"
```

Each position belongs to the same organization. The leading empty country means
FHOOE has no country restriction. Use commas for multiple values within a position,
for example `AUT,FRA`. Supported roles are `OPERATIONAL_ENTITY` and `NDSF`;
supported countries match the backend: `AUT`, `FRA`, `ESP`, `GER`. NDSF requires
at least one country. Roles are stored in `ceeds_role`, countries in `ceeds_nations`.
Aliases use letters, digits, dots, underscores and hyphens, starting with a letter
or digit. Duplicate aliases, invalid values and mismatched list lengths fail startup
before database setup begins.

When all organization variables are omitted, startup creates FHOOE with role
`OPERATIONAL_ENTITY`, empty country and domain `fh-ooe.at`. Explicitly setting
`CEEDS_BOOTSTRAP_ORGANIZATIONS: ""` disables seeding. Providing only organization
aliases creates them with empty role/country attributes. Newly configured
organizations use their alias as display name and have no inferred domain.

Every startup creates missing organizations and updates roles/countries for listed
aliases. Existing IDs, names, domains, memberships, other attributes and unlisted
organizations remain. Missing default attributes are backfilled for all organizations.
Keycloak's default realm import skips existing realms; the bundled bootstrap also
configures existing realms, clients, registration profile and organization token mapper.
Admin changes to managed roles/countries and frontend URLs are replaced on restart.

Additional runtime settings:

| Variable | Behavior |
| --- | --- |
| `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` | Required for automatic setup, including existing installations; supply credentials for an existing admin on restart. |
| `CEEDS_DIRECTORY_CLIENT_SECRET` | Required with `start`; `start-dev` defaults to `ceeds-directory-dev-secret`. Match the backend's directory-client secret. |
| `CEEDS_FRONTEND_URLS` | Semicolon-separated frontend base URLs; defaults to `http://localhost:5173;http://localhost:8080`. Startup derives redirect URIs, logout URIs and web origins. |
| `CEEDS_BOOTSTRAP_DEMO_USER` | `true` with `start-dev` creates the development user if missing and joins FHOOE if present; default `false`. Production `start` rejects demo-user creation. |
| `KC_HTTP_PORT`, `KC_HTTP_RELATIVE_PATH` | Respected by local bootstrap; `/auth` supported. HTTP must be enabled; TLS may terminate at ingress. |

Entrypoint accepts `start` and `start-dev` with normal Keycloak arguments. Other
commands, such as `--version`, pass through without setup. Startup/authentication
and bootstrap have a combined 180-second limit. Failure stops the container;
readiness requires both bootstrap completion and Keycloak readiness. Deployment
uses one Keycloak replica. Database topology and persistence remain deployment concerns.

### Helm

```yaml
keycloak:
  image:
    name: ghcr.io/eddie-energy/ceeds-keycloak
    version: snapshot-<commit-sha>
  directoryClientSecret: <directory-client-secret>
  frontendUrls: "https://registry.example.org"
  bootstrap:
    organizations: "fhooe;eda;enedis"
    roles: "OPERATIONAL_ENTITY;NDSF;NDSF"
    nations: ";AUT;FRA"
```

Set `bootstrap.organizations: ""` to disable seeding. Omit `bootstrap` for FHOOE
defaults. Helm uses the existing admin-password Secret and disables the demo
login. Frontend URLs default to the ingress host/scheme. Existing directory-secret
chart wiring is retained; replace its development value for your deployment.

PR CI builds the image (including provider tests) and runs real-container acceptance
tests. Main CI publishes `ghcr.io/eddie-energy/ceeds-keycloak:snapshot-<commit-sha>`
before packaging the chart. Published charts use that immutable image tag through
`Chart.appVersion`; source-checkout installs must set `keycloak.image.version`
to an available image tag. CI configuration is included; images are published
when these changes reach main.

Run focused checks locally:

```sh
python3 -m unittest discover -s backend/env/keycloak/tests -v
docker build -t ceeds-keycloak:test backend/env/keycloak
python3 backend/env/keycloak/tests/smoke.py ceeds-keycloak:test
helm lint helm --set-string keycloak.image.version=26.7.5
```

Acceptance tests create and remove isolated containers/volumes. They cover default
setup, explicit empty seeding, existing-realm updates, `/auth`, demo login,
organization token claims and directory-client permissions.
