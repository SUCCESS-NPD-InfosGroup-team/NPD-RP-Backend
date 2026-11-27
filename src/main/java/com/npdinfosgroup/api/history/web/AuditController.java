package com.npdinfosgroup.api.history.web;

import java.time.LocalDate;

import com.npdinfosgroup.api.history.dto.request.AuditLogCriteria;
import com.npdinfosgroup.api.history.dto.response.AuditLogResponse;
import com.npdinfosgroup.api.history.dto.response.LastChangeResponse;
import com.npdinfosgroup.api.history.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Changes recorded in the audit log, as the screens show them.
 */
@RestController
@RequestMapping("/api/{version}/history")
@Tag(name = "Historial", description = "Cambios registrados en la auditoría")
class AuditController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final AuditQueryService queryService;

	/**
	 * Creates the controller.
	 *
	 * @param queryService reads the audit log
	 */
	AuditController(AuditQueryService queryService) {
		this.queryService = queryService;
	}

	/**
	 * The last change of a resource, for "Último cambio" in its side panel.
	 *
	 * @param id employee id
	 * @return 200 with the last operation, or 204 if the resource was never changed through the API
	 */
	@GetMapping(path = "/resources/{id}/last-change", version = "v1")
	@Operation(summary = "Último cambio de un recurso", description = """
			Última operación guardada por la API sobre un recurso (SCRUM-85, «Último cambio» del panel de \
			detalle): su registro o su última edición, con **todos** los campos de esa operación. Las \
			operaciones anteriores no se devuelven. Todos los roles.

			- `action` = `CREATE` en un registro: cada campo trae su valor inicial y `oldValue` es null.
			- `actor` es el usuario de Keycloak tal como quedó en la auditoría.
			- Los valores vienen como los guarda la auditoría: fechas `yyyy-MM-dd` y catálogos por nombre.
			- 204 si el recurso nunca se cambió por la API (viene del import). No se comprueba que el \
			recurso exista: un id desconocido también responde 204; el 404 lo da «Ver recurso».""")
	@ApiResponse(responseCode = "200", description = "La última operación")
	@ApiResponse(responseCode = "204", description = "Sin cambios registrados por la API", content = @Content)
	@ApiResponse(responseCode = "400", description = "El id no es un número (`INVALID_REQUEST`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<LastChangeResponse> resourceLastChange(
			@Parameter(description = "Id del recurso") @PathVariable long id) {
		return queryService.findResourceLastChange(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/**
	 * The audit log of a resource, for its "Auditoría" screen (HU-04). Only the Operations administrator
	 * reaches it: {@code SecurityConfig} answers 403 to the other roles.
	 *
	 * @param id employee id
	 * @param actor only the changes of this user
	 * @param field only the edits of this field
	 * @param from first day included
	 * @param to last day included
	 * @param pageable page number and size
	 * @return 200 with the page, empty if the resource was never changed through the API
	 */
	@GetMapping(path = "/resources/{id}/audit-log", version = "v1")
	@Operation(summary = "Auditoría de un recurso", description = """
			Quién cambió qué y cuándo en un recurso (HU-04, pantalla «Auditoría»), 25 entradas por página, \
			las más recientes primero (`sort` se ignora). Solo Operations administrator.

			- `action` = `UPDATE` («Edición»): una entrada por campo editado, con su valor anterior y nuevo.
			- `action` = `CREATE` («Alta»): **una** entrada con todos los valores con que se registró \
			(`oldValue` siempre null).
			- `actor` es el usuario de Keycloak tal como quedó en la auditoría.
			- Filtros opcionales, combinables: `actor`, `field` (no devuelve el alta), `from` y `to` (días \
			completos en hora de Bogotá, ambos incluidos).
			- `actors` y `fields` traen las opciones de los filtros «Usuario» y «Campo» de todo el recurso, \
			sin aplicar los filtros.
			- Un recurso sin cambios por la API (viene del import) o un id desconocido responden una página \
			vacía; el 404 lo da «Ver recurso».""")
	@ApiResponse(responseCode = "200", description = "La página de la auditoría")
	@ApiResponse(responseCode = "400", description = """
			El id o una fecha no tienen el formato esperado (`INVALID_REQUEST`), o `to` es anterior a `from` \
			(`VALIDATION_FAILED`, detalle en `errors`)""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<AuditLogResponse> resourceAuditLog(@Parameter(description = "Id del recurso") @PathVariable long id,
			@Parameter(description = "Solo los cambios de este usuario (valor de `actors`)")
			@RequestParam(required = false) String actor,
			@Parameter(description = "Solo las ediciones de este campo (valor `field` de `fields`)")
			@RequestParam(required = false) String field,
			@Parameter(description = "Desde este día, incluido (yyyy-MM-dd)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "Hasta este día, incluido (yyyy-MM-dd)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@ParameterObject Pageable pageable) {
		return ResponseEntity
			.ok(queryService.findResourceLog(id, new AuditLogCriteria(actor, field, from, to), pageable));
	}

}
