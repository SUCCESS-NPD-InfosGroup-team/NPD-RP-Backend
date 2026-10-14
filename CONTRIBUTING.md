# Cómo contribuir

## Ramas

| Rama      | Entorno     | Despliegue                  |
|-----------|-------------|-----------------------------|
| `main`    | Producción  | Automático en Railway       |
| `develop` | Staging     | Automático en Railway       |
| `<tipo>/<descripcion>` | Local | — |

- Nadie hace push directo a `main` ni a `develop`; todo entra por Pull Request.
- `develop` sirve para revisar los cambios en staging antes de pasarlos a producción.
- `main` solo recibe PRs desde `develop`, cuando lo que hay en staging está validado. Esto incluye los `hotfix/`: también pasan primero por `develop`.
- No se abren PRs de `main` hacia `develop`.

### Nombre de la rama

Formato: `<tipo>/<descripcion-en-kebab-case>`. El tipo va en minúsculas; la descripción admite mayúsculas, minúsculas, números y guiones simples (sin espacios, `_` ni `--`).

| Tipo       | Uso |
|------------|-----|
| `feature`  | Funcionalidad nueva |
| `fix`      | Corrección de error |
| `hotfix`   | Corrección urgente para producción |
| `refactor` | Cambio interno sin alterar comportamiento |
| `docs`     | Documentación |
| `test`     | Tests |
| `chore`    | Configuración, dependencias |
| `ci`       | Pipelines y workflows |

Ejemplos válidos: `feature/importar-excel-planner`, `fix/SCRUM-31-fecha-nula`. No válidos: `Feature/algo`, `feature/fecha_nula`, `mi-rama`.

El workflow `.github/workflows/branch-policy.yml` valida estas reglas en cada PR hacia `main` o `develop`; si falla, el PR no se puede fusionar.

## Flujo de trabajo

1. Actualiza `develop` y crea tu rama desde ahí:
   ```bash
   git checkout develop
   git pull
   git checkout -b <tipo>/<descripcion>
   ```
   Sigue la [nomenclatura de ramas](#nombre-de-la-rama), por ejemplo `feature/importar-excel-planner`.
2. Haz tus cambios y verifica que todo funciona:
   ```bash
   ./mvnw test
   ```
   Docker debe estar en ejecución: los tests levantan su propio PostgreSQL con Testcontainers.
3. Haz commit. Si hay tarea de Jira, empieza el mensaje con su ID:
   ```
   SCRUM-17 | Descripción breve del cambio
   ```
4. Sube la rama y abre un PR **hacia `develop`** llenando la plantilla de abajo.
   En el PR corren dos checks obligatorios. Si alguno falla, el PR no se puede fusionar:
   - **Validar ramas**: nombre y origen de la rama.
   - **Run Unit Tests**: ejecuta los tests y analiza el código con SonarQube Cloud. Falla si un test falla o si no se cumple el quality gate: sin bugs ni vulnerabilidades nuevas y **al menos 80% de cobertura en las líneas nuevas o modificadas** del PR. SonarCloud deja un comentario en el PR con el detalle.

   Para ver tu cobertura antes de subir: `./mvnw verify` y abre `target/site/jacoco/index.html`.
5. Cuando el PR se aprueba y se fusiona, el cambio se despliega en staging.
6. Para pasar a producción se abre un PR de `develop` hacia `main`.

## Plantilla del Pull Request

Todos los campos son obligatorios salvo el ID de Jira cuando no exista tarea. **El campo de IA siempre debe llenarse**, aunque la respuesta sea "No". La plantilla está en `.github/pull_request_template.md` y GitHub la carga automáticamente al abrir el PR.

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
