# AGENTS.md

Guide for AI agents (and people) working in this repository.

## What this project is

NPD-RP Backend is the API that replaces an Excel workbook used today as a work tool. The main goal is to have **a single source of truth** for that data and, on top of it, to offer:

- Management (CRUD) of the information that used to live in the Excel, including its initial import.
- Statistical analysis and reports on that data.
- Automatic notifications.
- Possibly a machine learning algorithm (**not confirmed yet**; do not assume it exists).

Stack: **Java 21**, **Spring Boot 4.1.1**, PostgreSQL 18, Flyway, JPA, Lombok. Deployed on Railway. Do not use APIs or patterns from older versions (for example, Spring Boot 2/3): use what these versions offer.

## Language

- **Everything is written in English**: code, comments, Javadoc, documentation, migrations, commits and PRs.
- **The only exception is the text the frontend shows to users**: the messages in `messages.properties` are in Spanish, the default language of every client.
- Existing Spanish text is translated when its file is changed for its own task, not in unrelated PRs (see [PR size](#pr-size)).

## Architecture

- **Modular monolith.** Chosen for time reasons: a single deployment, but the code is split into modules by feature under `com.npdinfosgroup.api`.
- **Layered architecture inside each module.** It is not Domain-Driven Design: the design is **data-centric** (JPA entities + services), with no aggregates, value objects or domain events.
- The database schema is owned by **Flyway** (`spring.jpa.hibernate.ddl-auto=validate`); Hibernate never creates or alters tables.

### Data access: JPA repositories only

Team decision: all code that serves users (endpoints and the services they use) accesses the database **only through Spring Data JPA repositories**.

- Use derived methods (`findByActiveTrueOrderByNameAsc`), `@EntityGraph` to fetch relations in one query and, if that is not enough, `@Query` in **JPQL with named parameters**.
- Do not use `JdbcClient`, `JdbcTemplate`, native SQL or SQL built by concatenating text. That way there is no handwritten SQL in code that receives user data, and protection against SQL injection does not depend on reviewing every query.
- Entities mirror the tables Flyway creates, and Hibernate **only validates them** (`ddl-auto=validate`). If an entity does not match its table (column name or type), the application does not start: fix the entity or create a new migration.
- Views (`resource_v`, `project_summary_v`) are mapped as read-only entities (`@Immutable`) with a repository that only reads.
- A relation written by its id (for example, `Employee.genderId`) may also have a read-only `@ManyToOne` (`insertable = false, updatable = false`) to read the names.
- **Only exception:** the Excel import (`planner/importer`, `PlannerImportRepository`) uses `JdbcClient`. It is a one-time load that developers run from the command line, not an endpoint, and it receives no data through the API.

### Layers (packages repeated across modules)

| Package       | Responsibility |
|---------------|----------------|
| `web`         | REST controllers. They only receive/return DTOs and delegate to `service`. |
| `service`     | Business logic and transactions. |
| `persistence` | JPA entities and Spring Data repositories. |
| `dto`         | API input/output objects. |
| `client`      | Calls to external systems or services. |
| `job`         | Scheduled tasks (`@Scheduled`). |
| `listener`    | Reactions to application or persistence events. |

Subpackages, when a layer grows:

| Subpackage | Content |
|------------|---------|
| `service/mapper` | Builds the responses (DTOs) from the entities. |
| `service/validator` | Rules checked before saving. They collect every error of a form into a single response. |
| `persistence/entity` | JPA entities, and enums that identify tables (for example, `Catalog`). |
| `persistence/repository` | Spring Data repositories only, with their base interfaces (`CatalogEntityRepository`) and fragments (`EmployeeRefresh`). |
| `persistence/query` | Query pieces that are not repositories: filters (`Specification`) and projections (results of a query). |

Expected flow: `web → service → persistence`. Controllers do not access repositories directly, and entities never leave through the API (they are mapped to DTOs).

### Modules (`src/main/java/com/npdinfosgroup/api/`)

| Module          | Purpose | Packages |
|-----------------|---------|----------|
| `planner`       | Core: the data migrated from the Excel. `importer` loads the existing Excel. | `web`, `service` (+`mapper`, `validator`), `persistence` (`entity`, `repository`, `query`), `dto` (`request`, `response`), `importer` |
| `insights`      | Statistical analysis and, if confirmed, the ML model. | `web`, `service`, `persistence`, `dto`, `client`, `job` |
| `reports`       | Report generation, including scheduled reports. | `web`, `service`, `persistence`, `dto`, `job` |
| `export`        | Data export to files. `writer` produces the output format. | `web`, `service`, `dto`, `writer` |
| `notifications` | Automatic notifications (event-driven or scheduled). | `web`, `service`, `persistence`, `dto`, `client`, `job`, `listener` |
| `history`       | Change history of the data. | `web`, `service`, `persistence`, `dto`, `listener` |
| `access`        | Users, permissions and which data each one can see (`visibility`). | `web`, `service`, `persistence`, `dto`, `client`, `visibility` |
| `integration`   | Integration and synchronization with external systems. | `web`, `service`, `persistence`, `dto`, `client`, `job` |
| `shared`        | Cross-cutting code; not a business module. | `audit`, `config`, `exception`, `security`, `util` |

Other directories:

- `src/main/resources/db/migration/` — Flyway migrations (`V<n>__<description>.sql`). Never edit a migration that was already applied; create a new one.
- `src/test/java/...` — tests.
- `docs/specs/<JIRA-ID>/` — specification, design and PR plan of each Jira task (see [Spec-driven development](#spec-driven-development)).

Modules without code are only folders (`.gitkeep`). When you create new code, place it in the matching module and layer; do not create new packages without a clear reason.

### Rules between modules

- A module uses another one through its `service` layer, never through its `persistence`.
- `shared` does not depend on any business module.

## Mandatory rules for every change

### Spec-driven development

Every Jira task is specified, designed and planned **before any code is written**. The documents live in `docs/specs/<JIRA-ID>/` (for example, `docs/specs/SCRUM-93/`), one folder per task:

| File | Content |
|------|---------|
| `spec.md` | **What and why.** The user story, its acceptance criteria and the scope: what is included, what is explicitly out of it, and the open questions. No technical decisions. |
| `design.md` | **How.** The technical decisions, each with the options considered and the reason for the choice (for example, how the API talks to Keycloak), plus the data model, endpoints, error codes, settings and environment variables, and any change needed outside the repository (Keycloak, Railway). |
| `plan.md` | **The PRs.** One entry per PR, in merge order: branch, content, estimated changed lines, what it depends on, and its status (pending, in review, merged) with the PR link. |

- Order: `spec.md` → `design.md` → `plan.md` → code. The developer reviews and approves each document before the next one is written, and AI agents do not write code until the three are approved.
- The documents are kept up to date: a PR that changes a decision updates `design.md` in the same PR, and `plan.md` is updated as PRs are opened and merged.
- The spec folder usually goes in its own `docs/` PR, before the first code PR, and follows the [PR size](#pr-size) rule.
- Documentation that is not about one task (for example, `docs/database/`) stays in its own folder under `docs/`.

### PR size

Every PR **must have fewer than 400 changed lines**. A small PR gets a thorough review and can be reverted without dragging other changes along.

- Count the added plus the deleted lines of the **whole** diff: code, tests, migrations, configuration and documentation. Measure it with `git diff --shortstat origin/develop...HEAD`.
- If a task does not fit, split it into several PRs merged into `develop` in order (for example: external client, then persistence and service, then endpoint). Each PR states in its description which part it is (for example, "Part 2 of 4") and the Jira ID.
- Each PR must stand on its own: it compiles, passes the checks and brings **its own tests** (80% coverage on new code). Tests are never left for a later PR.
- A PR does not mix changes unrelated to its task (refactors, formatting, other stories).

### Commits by AI agents

It is **mandatory** for an AI agent to **never commit before the developer has reviewed the changes**.

- Leave the changes uncommitted in the working tree and report what changed (files, purpose, test results and the changed-line count).
- Commit only after the developer reviews the changes and explicitly approves that commit. Approval of one commit does not extend to later changes.
- The same applies to anything that publishes work: no push, PR or merge without the developer's explicit approval.

### Controllers

Controller routes include the name of their module. For example, a controller in the `planner` module serves `/api/v1/planner/projects` to get the projects.

Module names: `planner|history|insights|reports|export|access|notifications`.

### Spring Boot guidelines

Every change must follow the guidelines in [guidelines.md](guidelines.md) (constructor injection, typed properties, transaction boundaries, DTOs as records, API versioning, centralized error handling, logging, etc.):

@guidelines.md

### Javadoc

Javadoc documentation is **mandatory**:

- Every class, interface, record and enum has Javadoc explaining its purpose, **except DTOs and entities** (see below).
- Every method in `web`, `service`, `client`, `job` and `listener` has Javadoc, whatever its visibility (the guidelines prefer package-private, and that does not exempt it from documentation). Include `@param`, `@return` and `@throws` where they apply.
- Javadoc explains what the code does and why; it does not repeat the method name.

**NOT documented with Javadoc:**

- **Input or output DTOs** (`dto`, `dto/request`, `dto/response`, commands): neither the class/record nor its fields.
- **Database entities** (`persistence`, `persistence/entity`): neither the class nor its fields or relations.
- Getters, setters and constructors generated by Lombok or trivial ones.

### OpenAPI + Scalar

The API is documented for the frontends with **OpenAPI**, rendered with **Scalar UI**:

- Every new or modified endpoint must be fully described in OpenAPI: summary, parameters, request body, responses and status codes (including errors in ProblemDetails format).
- The OpenAPI specification and Scalar UI are **only available in dev mode**: `APP_DEV_MODE=true` in `.env`. If the variable is missing or `false` (production on Railway), both endpoints answer 404.
- With dev mode on: specification at `/v3/api-docs` and Scalar UI at `/scalar`.
- Implementation: the `springdoc-openapi-starter-webmvc-scalar` dependency (springdoc 3.x, compatible with Spring Boot 4). In `application.properties`, `app.dev-mode` controls `springdoc.api-docs.enabled` and `scalar.enabled`. The metadata (title, description, version) lives in `shared/config/OpenApiConfig`.
- Do not add any other way to expose the documentation (for example, Swagger UI), and do not enable it outside `APP_DEV_MODE`.

## Running the project

- Configuration: `cp .env.example .env`
- Everything in Docker (Postgres + API): `docker compose up --build`
- API from Maven/IDE: `./mvnw spring-boot:run` (`spring-boot-docker-compose` starts only the `postgres` service)
- Tests: `./mvnw test`. They need Docker running: tests that load the context use Testcontainers (`TestcontainersConfiguration`) and start their own PostgreSQL, without depending on `.env`. Every new `@SpringBootTest` must import that configuration.
- Coverage: JaCoCo generates `target/site/jacoco/index.html` when running `./mvnw verify`. `dto`, `persistence/entity`, `shared/config` and `ApiApplication` do not count toward coverage (excluded in `pom.xml`, both for JaCoCo and in `sonar.coverage.exclusions`), nor does code generated by Lombok (`lombok.config`). If you add an exclusion, add it in both places.
- Health check: `curl localhost:8080/actuator/health`
- `DB_HOST`/`DB_PORT` in `.env` are the values seen from the host (`localhost:5433`); the compose `api` service always uses `postgres:5432`. Port 5433 avoids clashing with a local Windows PostgreSQL on 5432.
- Do not use `docker run` directly for local testing: it does not load `.env`.

## How to contribute

The full workflow is in [CONTRIBUTING.md](CONTRIBUTING.md). Summary:

- `main` = production, deployed automatically to Railway. There is no staging: `develop` is not deployed and is run locally with `docker compose up --build`.
- Work on a `<type>/<kebab-case-description>` branch created from `develop` (types: `feature`, `fix`, `hotfix`, `refactor`, `docs`, `test`, `chore`, `ci`) and open the PR against `develop`. `main` only receives PRs from `develop`. The `branch-policy.yml` workflow enforces this.
- Every PR must pass the "Validar ramas" (`branch-policy.yml`) and "Run Unit Tests" (`unit-test.yml`: tests + SonarQube Cloud analysis) checks. The latter fails if a test fails **or** if the "Sonar way" quality gate does not pass: no new bugs or vulnerabilities and **80% coverage on new code**.
- Every PR has **fewer than 400 changed lines** (see [PR size](#pr-size)).
- AI agents never commit, push or open a PR before the developer reviews and approves the changes (see [Commits by AI agents](#commits-by-ai-agents)).
- The PR must state the task type, the Jira ID (if any), a description and, **mandatorily**, whether AI was used and which model.
- If you are an AI agent: declare it in the PR with the exact model name and version.
