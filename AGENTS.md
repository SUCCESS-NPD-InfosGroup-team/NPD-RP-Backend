# AGENTS.md

Guía para agentes de IA (y personas) que trabajan en este repositorio.

## Qué es este proyecto

NPD-RP Backend es la API que reemplaza un Excel que hoy se usa como herramienta de trabajo. El objetivo principal es que exista **una sola fuente de verdad** para esos datos, y sobre ella ofrecer:

- Gestión (CRUD) de la información que antes vivía en el Excel, incluida su importación inicial.
- Análisis estadístico e informes sobre esos datos.
- Notificaciones automáticas.
- Posiblemente un algoritmo de machine learning (**aún no confirmado**; no asumir que existe).

Stack: **Java 21**, **Spring Boot 4.1.1**, PostgreSQL 18, Flyway, JPA, Lombok. Despliegue en Railway. No uses APIs ni patrones de versiones anteriores (por ejemplo, Spring Boot 2/3): usa lo que ofrecen estas versiones.

## Arquitectura

- **Monolito modular.** Se eligió por cuestiones de tiempo: un solo despliegue, pero el código se separa en módulos por funcionalidad bajo `com.npdinfosgroup.api`.
- **Arquitectura en capas dentro de cada módulo.** No es Domain-Driven Design: el diseño está **centrado en datos** (entidades JPA + servicios), sin agregados, value objects ni eventos de dominio.
- El esquema de base de datos lo controla **Flyway** (`spring.jpa.hibernate.ddl-auto=validate`); Hibernate nunca crea ni modifica tablas.

### Capas (paquetes que se repiten en los módulos)

| Paquete       | Responsabilidad |
|---------------|-----------------|
| `web`         | Controladores REST. Solo reciben/devuelven DTOs y delegan en `service`. |
| `service`     | Lógica de negocio y transacciones. |
| `persistence` | Entidades JPA y repositorios Spring Data. |
| `dto`         | Objetos de entrada/salida de la API. |
| `client`      | Llamadas a sistemas o servicios externos. |
| `job`         | Tareas programadas (`@Scheduled`). |
| `listener`    | Reacción a eventos de la aplicación o de persistencia. |

Flujo esperado: `web → service → persistence`. Los controladores no acceden a repositorios directamente y las entidades no salen por la API (se mapean a DTOs).

### Módulos (`src/main/java/com/npdinfosgroup/api/`)

| Módulo          | Propósito | Paquetes |
|-----------------|-----------|----------|
| `planner`       | Núcleo: los datos migrados del Excel. `importer` carga el Excel existente. | `web`, `service` (+`mapper`), `persistence` (`entity`, `repository`), `dto` (`request`, `response`), `importer` |
| `insights`      | Análisis estadístico y, si se confirma, el modelo de ML. | `web`, `service`, `persistence`, `dto`, `client`, `job` |
| `reports`       | Generación de informes, incluidos los programados. | `web`, `service`, `persistence`, `dto`, `job` |
| `export`        | Exportación de datos a archivos. `writer` genera el formato de salida. | `web`, `service`, `dto`, `writer` |
| `notifications` | Notificaciones automáticas (por eventos o programadas). | `web`, `service`, `persistence`, `dto`, `client`, `job`, `listener` |
| `history`       | Historial de cambios sobre los datos. | `web`, `service`, `persistence`, `dto`, `listener` |
| `access`        | Usuarios, permisos y qué datos puede ver cada uno (`visibility`). | `web`, `service`, `persistence`, `dto`, `client`, `visibility` |
| `integration`   | Integración y sincronización con sistemas externos. | `web`, `service`, `persistence`, `dto`, `client`, `job` |
| `shared`        | Código transversal; no es un módulo de negocio. | `audit`, `config`, `exception`, `security`, `util` |

Otros directorios:

- `src/main/resources/db/migration/` — migraciones Flyway (`V<n>__<descripcion>.sql`). Nunca editar una migración ya aplicada; crear una nueva.
- `src/test/java/...` — tests.

Por ahora los módulos son solo carpetas (`.gitkeep`). Al crear código nuevo, colócalo en el módulo y la capa que correspondan; no crees paquetes nuevos sin una razón clara.

### Reglas entre módulos

- Un módulo usa a otro a través de su capa `service`, nunca de su `persistence`.
- `shared` no depende de ningún módulo de negocio.

## Reglas obligatorias para cada cambio

### Guías de Spring Boot

Todo cambio debe cumplir las guías de [guidelines.md](guidelines.md) (inyección por constructor, propiedades tipadas, límites transaccionales, DTOs como records, versionado de API, manejo centralizado de errores, logging, etc.):

@guidelines.md

### Javadoc

La documentación con Javadoc es **obligatoria**:

- Toda clase, interfaz, record y enum lleva Javadoc que explique su propósito, **excepto los DTOs y las entidades** (ver abajo).
- Todo método de `web`, `service`, `client`, `job` y `listener` lleva Javadoc, sin importar su visibilidad (las guías prefieren package-private, y eso no exime de documentar). Incluye `@param`, `@return` y `@throws` cuando apliquen.
- El Javadoc explica qué hace y por qué, no repite el nombre del método.

**NO se documenta con Javadoc:**

- **DTOs de entrada o salida** (`dto`, `dto/request`, `dto/response`, commands): ni la clase/record ni sus campos.
- **Entidades de base de datos** (`persistence`, `persistence/entity`): ni la clase ni sus campos o relaciones.
- Getters, setters y constructores generados por Lombok o triviales.

### OpenAPI + Scalar

La API se documenta para los frontends con **OpenAPI**, visualizado con **Scalar UI**:

- Cada endpoint nuevo o modificado debe quedar bien descrito en OpenAPI: resumen, parámetros, cuerpo de petición, respuestas y códigos de estado (incluidos los errores en formato ProblemDetails).
- La especificación OpenAPI y Scalar UI **solo están disponibles en modo desarrollo**: `APP_DEV_MODE=true` en `.env`. Si la variable falta o es `false` (staging y producción en Railway), ambos endpoints responden 404.
- Con el modo desarrollo activo: especificación en `/v3/api-docs` y Scalar UI en `/scalar`.
- Implementación: dependencia `springdoc-openapi-starter-webmvc-scalar` (springdoc 3.x, compatible con Spring Boot 4). En `application.properties`, `app.dev-mode` controla `springdoc.api-docs.enabled` y `scalar.enabled`. Los metadatos (título, descripción, versión) están en `shared/config/OpenApiConfig`.
- No agregues otra forma de exponer la documentación (por ejemplo, Swagger UI) ni la actives fuera de `APP_DEV_MODE`.

## Ejecutar el proyecto

- Configuración: `cp .env.example .env`
- Todo en Docker (Postgres + API): `docker compose up --build`
- API desde Maven/IDE: `./mvnw spring-boot:run` (`spring-boot-docker-compose` levanta solo el servicio `postgres`)
- Tests: `./mvnw test`
- Health check: `curl localhost:8080/actuator/health`
- `DB_HOST`/`DB_PORT` en `.env` son valores vistos desde el host (`localhost:5433`); el servicio `api` de compose siempre usa `postgres:5432`. El puerto 5433 evita chocar con un PostgreSQL local de Windows en el 5432.
- No usar `docker run` directamente para pruebas locales: no carga `.env`.

## Cómo contribuir

El flujo completo está en [CONTRIBUTING.md](CONTRIBUTING.md). Resumen:

- `main` = producción y `develop` = staging; ambas despliegan automáticamente en Railway.
- Trabaja en una rama `feature/<descripcion>` creada desde `develop` y abre el PR hacia `develop`.
- El PR debe indicar tipo de tarea, ID de Jira (si aplica), descripción y **obligatoriamente** si se usó IA y qué modelo.
- Si eres un agente de IA: decláralo en el PR con el nombre y versión exactos del modelo.
