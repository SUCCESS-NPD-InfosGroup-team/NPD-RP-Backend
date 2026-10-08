# NPD-RP Backend

Spring Boot 4 (Java 21) API backed by PostgreSQL 18, with Flyway migrations.

## Prerequisites

- Docker Desktop (running)
- Java 21 — only needed to run from the IDE / Maven

## Configuration

Copy the example env file and adjust values if needed:

```bash
cp .env.example .env
```

| Variable      | Default        | Description                                              |
|---------------|----------------|----------------------------------------------------------|
| `PORT`        | `8080`         | HTTP port of the API                                     |
| `JAVA_OPTS`   | `-XX:MaxRAMPercentage=75` | JVM options for the container                 |
| `DB_HOST`     | `localhost`    | Database host as seen from your machine                  |
| `DB_PORT`     | `5433`         | Database port published on your machine                  |
| `DB_NAME`     | `npd_rp`       | Database name                                            |
| `DB_USER`     | `npd_rp`       | Database user                                            |
| `DB_PASSWORD` | `change-me`    | Database password                                        |

`DB_HOST` / `DB_PORT` describe the database **from your machine**. Inside Docker Compose the API always connects to `postgres:5432`, regardless of these values.

## Running locally

### Option A — everything in Docker

```bash
docker compose up --build
```

Starts Postgres and the API. Add `-d` to run in the background; stop with `docker compose down` (data is kept in the `pgdata` volume; `docker compose down -v` wipes it).

### Option B — API from the IDE / Maven

Run `ApiApplication` from the IDE, or:

```bash
./mvnw spring-boot:run
```

`spring-boot-docker-compose` starts only the `postgres` service from `compose.yaml` automatically and wires the connection, so no extra setup is needed.

### Check it's up

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP", ...}
```

## Deployment (Railway)

Railway builds from the `Dockerfile` using the settings in `railway.json` (health check on `/actuator/health`, restart on failure). Railway injects `PORT`; set the database variables on the API service using references to the Postgres service:

```
DB_HOST=${{Postgres.PGHOST}}
DB_PORT=${{Postgres.PGPORT}}
DB_NAME=${{Postgres.PGDATABASE}}
DB_USER=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}
```

## Troubleshooting

- **Password authentication failed / wrong database:** a PostgreSQL installed locally on Windows usually listens on `5432`. Keep `DB_PORT=5433` (or any free port) in `.env`.
- **Connection refused to `localhost` inside a container:** don't start the image with plain `docker run`; it doesn't read `.env`. Use `docker compose up --build`.
- **Port 8080 already in use:** stop the `api` container (`docker compose stop api`) before running from the IDE, or the other way around.
