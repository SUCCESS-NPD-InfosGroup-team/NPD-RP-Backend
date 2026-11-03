# How to contribute

## Branches

| Branch    | Environment | Deployment                  |
|-----------|-------------|-----------------------------|
| `main`    | Production  | Automatic on Railway        |
| `develop` | Local only  | None: run it with `docker compose up --build` |
| `<type>/<description>` | Local | — |

- There is no staging environment (a cost decision): `develop` is not deployed anywhere.
- Nobody pushes directly to `main` or `develop`; everything goes in through a Pull Request.
- `develop` gathers the merged changes; check them locally with `docker compose up --build` before they go to production.
- `main` only receives PRs from `develop`, once what is in `develop` has been checked. This includes `hotfix/` branches: they also go through `develop` first.
- No PRs are opened from `main` to `develop`.

### Branch name

Format: `<type>/<kebab-case-description>`. The type is lowercase; the description allows uppercase and lowercase letters, numbers and single hyphens (no spaces, `_` or `--`).

| Type       | Use |
|------------|-----|
| `feature`  | New functionality |
| `fix`      | Bug fix |
| `hotfix`   | Urgent fix for production |
| `refactor` | Internal change without changing behavior |
| `docs`     | Documentation |
| `test`     | Tests |
| `chore`    | Configuration, dependencies |
| `ci`       | Pipelines and workflows |

Valid examples: `feature/import-planner-excel`, `fix/SCRUM-31-null-date`. Not valid: `Feature/something`, `feature/null_date`, `my-branch`.

The `.github/workflows/branch-policy.yml` workflow checks these rules on every PR to `main` or `develop`; if it fails, the PR cannot be merged.

## Workflow

1. Update `develop` and create your branch from it:
   ```bash
   git checkout develop
   git pull
   git checkout -b <type>/<description>
   ```
   Follow the [branch naming](#branch-name), for example `feature/import-planner-excel`.
2. Make your changes and check that everything works:
   ```bash
   ./mvnw test
   ```
   Docker must be running: the tests start their own PostgreSQL with Testcontainers.
3. Commit. If there is a Jira task, start the message with its ID:
   ```
   SCRUM-17 | Short description of the change
   ```
4. Push the branch and open a PR **to `develop`**, filling in the template below.
   Two mandatory checks run on the PR. If either fails, the PR cannot be merged:
   - **Validar ramas**: name and origin of the branch.
   - **Run Unit Tests**: runs the tests and analyzes the code with SonarQube Cloud. It fails if a test fails or if the quality gate is not met: no new bugs or vulnerabilities and **at least 80% coverage on the new or changed lines** of the PR. SonarCloud leaves a comment on the PR with the details.

   To see your coverage before pushing: `./mvnw verify` and open `target/site/jacoco/index.html`.
5. When the PR is approved and merged, the change is in `develop`. Nothing is deployed: run it locally with `docker compose up --build`.
6. To release to production, open a PR from `develop` to `main`. Before merging it, add to Railway any new environment variable and make any Keycloak change that the released PRs need.

## Pull Request template

Every field is mandatory except the Jira ID when there is no task. **The AI field must always be filled in**, even if the answer is "No". The template is in `.github/pull_request_template.md` and GitHub loads it automatically when the PR is opened. Its headings stay in Spanish; write the content in English.

```markdown
## Tipo de tarea
<!-- Marca una -->
- [ ] Feature (funcionalidad nueva)
- [ ] Fix (corrección de error)
- [ ] Refactor (sin cambio de comportamiento)
- [ ] Docs (documentación)
- [ ] Chore (configuración, dependencias, CI)
- [ ] Test

## Tarea de Jira
<!-- Ej: SCRUM-17. Escribe "N/A" si no aplica -->

## Descripción
<!-- Qué se hizo y por qué, en pocas líneas -->

## Uso de IA (OBLIGATORIO)
- [ ] No se usó IA
- [ ] Sí se usó IA

Modelo(s) usado(s): <!-- Ej: Claude Opus 5.5 vía Claude Code. Escribe "N/A" si no se usó -->
```
