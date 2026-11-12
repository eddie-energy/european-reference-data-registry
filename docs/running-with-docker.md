# Running S3 with Docker

This guide starts the complete S3 stack — backend with bundled web frontend, PostgreSQL and
Keycloak — with a single command. No Java, Node or Gradle installation is needed.

## What you get

| Service              | URL / port                                   | Purpose                                   |
|----------------------|----------------------------------------------|-------------------------------------------|
| `backend`            | http://localhost:8080                        | S3 web app, REST API, Swagger UI          |
| `keycloak`           | http://localhost:8081                        | Login (realm `ceeds`)                     |
| `postgres`           | `localhost:5440`                             | Database (`backend` / `backend` / `backend`) |
| `keycloak-bootstrap` | —                                            | One-shot job that seeds organizations, exits when done |

The frontend is part of the backend image and served on port 8080 — there is nothing extra to start.

## Prerequisites

- Docker Desktop, or Docker Engine with the Compose plugin **2.20 or newer** (`docker compose version`).
- Ports **8080**, **8081** and **5440** free on your machine.
- A GitHub account with access to the `eddie-energy` organization's packages. The backend image
  `ghcr.io/eddie-energy/european-reference-data-registry` is private.

## 1. Log in to the GitHub container registry (once)

Create a GitHub personal access token (classic) with the `read:packages` scope at
https://github.com/settings/tokens, then:

```sh
docker login ghcr.io -u <your-github-username>
# paste the token as password
```

Docker stores the credentials, so this is needed only once per machine.

## 2. Start the stack

```sh
git clone git@github.com:eddie-energy/ceeds-s3-federator.git
cd ceeds-s3-federator
docker compose up -d
```

The first start takes a few minutes: the custom Keycloak image is built locally and the backend
image is downloaded. Later starts take seconds.

The backend waits until PostgreSQL is healthy and `keycloak-bootstrap` has finished. Check progress with:

```sh
docker compose ps
docker compose logs -f backend
```

The stack is ready when http://localhost:8080/actuator/health reports `"status":"UP"`.

## 3. Use it

- App: http://localhost:8080 — sign in with **`ceeds` / `ceeds`**. This user belongs to the seeded
  organization `fhooe` (Operational Entity) and therefore has full rights.
- Swagger UI: http://localhost:8080/swagger-ui.html
- Keycloak admin console: http://localhost:8081 — **`admin` / `admin`**

New users can self-register on the login page. Roles are assigned to organizations, not users;
see [README](../README.md#organization-responsibilities).

## Everyday commands

```sh
docker compose stop                               # stop, keep containers and data
docker compose up -d                              # start again
docker compose pull backend && docker compose up -d   # update to the latest published backend
docker compose down                               # remove containers, keep database
```

To wipe all data and start from scratch:

```sh
docker compose down -v
```

> **Warning:** `down -v` permanently deletes the PostgreSQL volume, including all reference data.

### Running a specific backend version

The image defaults to `latest` (the newest build of `main`). Every build is also tagged
`snapshot-<commit-sha>`. Pin one with `S3_IMAGE`:

```sh
S3_IMAGE=ghcr.io/eddie-energy/european-reference-data-registry:snapshot-<commit-sha> docker compose up -d
```

`S3_IMAGE` also accepts a locally built image, e.g. from `./gradlew :backend:jibDockerBuild`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `unauthorized` / `denied` pulling `ghcr.io/eddie-energy/...` | Run step 1; the token needs `read:packages`. |
| `port is already allocated` | Another process uses 8080, 8081 or 5440. Stop it, or stop a locally running backend (IDE / `bootRun`). |
| Backend stays in `Created` | It waits for `keycloak-bootstrap`. Check `docker compose logs keycloak keycloak-bootstrap`. |
| Login redirects fail or show "Invalid redirect URI" | Open the app via `http://localhost:8080`, not `127.0.0.1` or another host name. |
| Old UI after update | Hard-refresh the browser (cached `index.html`). |

## Developing instead

For backend or frontend development run only the infrastructure and start the backend from your IDE or Gradle:

```sh
docker compose -f backend/env/docker-compose.yaml up -d
./gradlew :backend:bootRun
```
