# Planner import

One-time import of the client's planner (`PLANIFICACION DE RECURSOS NPD`, sheet `PLANIFICADOR`) into the database (story SCRUM-66). After the platform goes live, data is entered by hand; the import is only run again to correct this first load.

It is a command, not an endpoint: the application imports the file when it starts with `--planner.import.file`, writes a report and stops. Code: [`planner/importer`](../../src/main/java/com/npdinfosgroup/api/planner/importer).

> **The Excel has personal data. Never commit it, nor its report, to any repository.**

## Run it

Step-by-step flow and commands for this repo (Maven or Docker), the infra repo's global compose and Railway: [ejecucion.md](ejecucion.md).

The database must have the migrations (Flyway applies them when the application starts). Put the Excel in a folder without spaces, for example `C:\import\planner.xlsx`.

**Local database** (Docker Desktop running; `spring-boot-docker-compose` starts the `postgres` service):

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments="--planner.import.file=C:/import/planner.xlsx --spring.main.web-application-type=none"
```

**Another database** (staging or production): build the jar and pass the connection with the `DB_*` variables of `application.properties`:

```sh
./mvnw package -DskipTests
java -jar target/api-0.0.1-SNAPSHOT.jar --planner.import.file=C:/import/planner.xlsx --spring.main.web-application-type=none
```

| Argument | Meaning |
|---|---|
| `--planner.import.file` | Path of the planner `.xlsx` (required; without it the application starts normally and imports nothing) |
| `--planner.import.report` | Path of the report; default `reporte-import-planificador.xlsx` next to the Excel |
| `--spring.main.web-application-type=none` | Do not start the web server, so the application stops when the import ends |

The log only shows counts and the report path, never personal data:

```
Planner import finished: 162 rows read, 162 imported, 0 rejected, 37 flagged. Report: C:\import\reporte-import-planificador.xlsx
```

## What it does

- Finds the header row (the one with `COD EMPL`) and reads each column by its name, so extra or moved columns do not break it. The calculated columns (probation, vacation, end month) are not stored: the database derives them.
- Cleans every value: joins spaces and line breaks, treats `*`, `-`, `N/A` and blanks as empty, and fixes known typos (`Pesencial`, `NPD-ADMNISTRACION`, `Hibrido . 1 al mes`).
- Creates the clients, projects, job titles, positions and client roles that do not exist yet, matching names case-insensitively.
- Creates or updates each employee by `COD EMPL`, then assigns leaders by `COD LIDER` once every employee exists.
- Runs in a single transaction: if anything fails, nothing is saved.
- **Running it again does not duplicate data**: employees are updated by code (`version` goes up), catalogs are reused. Employees missing from the new Excel are not deleted.

## Rules

| Case | What happens |
|---|---|
| Missing code, name, position, hire date, gender or status | Row rejected |
| Code repeated in the Excel | Only the first row is imported; the others are rejected |
| Unknown gender, status or work modality | Row rejected |
| Empty work modality, client or project | Saved as **"No especificado"** (migration V3) and flagged |
| `SIN PROYECTO` | Employee without project; the end date is ignored |
| `FECHA FINAL` = `INDEFINIDO` | No end date |
| End date already passed | Flagged; the employee shows as `PENDIENTE_CONFIRMACION` in `resource_v` |
| `MES FINAL` does not match the end date | Flagged; the date wins |
| `COD LIDER` is not an employee (Maribel Escala `1`, Vanessa Gill `731`) | No leader; flagged |
| `LIDER TLP` name does not match the `COD LIDER` employee | The code wins; flagged |
| Invalid or repeated email | Saved empty; flagged |
| Client contact from another client's domain | Imported as is; flagged |
| Termination reason while status is Activo | Imported as is; flagged |
| Unknown optional value (UEN, laptop, level, talent review, termination reason) | Saved empty; flagged |

## Report

An `.xlsx` for whoever reviews the data, in Spanish:

- **Resumen**: rows read, imported, rejected and flagged.
- **Rechazadas**: row, employee code, name, column, original value and reason.
- **Observaciones**: the same columns for values imported with a correction.

Texts are in [`messages.properties`](../../src/main/resources/messages.properties).
