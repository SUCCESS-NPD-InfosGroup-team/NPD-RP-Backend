# CLAUDE.md

## Running the project

- Setup: `cp .env.example .env`
- Full stack in Docker (Postgres + API): `docker compose up --build`
- API from Maven/IDE: `./mvnw spring-boot:run` (spring-boot-docker-compose auto-starts only the `postgres` service)
- Health check: `curl localhost:8080/actuator/health`
- `DB_HOST`/`DB_PORT` in `.env` are host-side values (`localhost:5433`); the `api` compose service always uses `postgres:5432`. Port 5433 avoids a local Windows PostgreSQL on 5432.
- Don't use plain `docker run` for local testing; it doesn't load `.env`.
