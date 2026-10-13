# Cómo contribuir

## Ramas

| Rama      | Entorno     | Despliegue                  |
|-----------|-------------|-----------------------------|
| `main`    | Producción  | Automático en Railway       |
| `develop` | Staging     | Automático en Railway       |
| `feature/<descripcion>` | Local | — |

- Nadie hace push directo a `main` ni a `develop`; todo entra por Pull Request.
- `develop` sirve para revisar los cambios en staging antes de pasarlos a producción.
- `main` solo recibe PRs desde `develop`, cuando lo que hay en staging está validado.

## Flujo de trabajo

1. Actualiza `develop` y crea tu rama desde ahí:
   ```bash
   git checkout develop
   git pull
   git checkout -b feature/<descripcion>
   ```
   Usa kebab-case y una descripción corta, por ejemplo `feature/importar-excel-planner`.
2. Haz tus cambios y verifica que todo funciona:
   ```bash
   ./mvnw test
   ```
3. Haz commit. Si hay tarea de Jira, empieza el mensaje con su ID:
   ```
   SCRUM-17 | Descripción breve del cambio
   ```
4. Sube la rama y abre un PR **hacia `develop`** llenando la plantilla de abajo.
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
