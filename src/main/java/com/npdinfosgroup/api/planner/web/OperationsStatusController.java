package com.npdinfosgroup.api.planner.web;

import com.npdinfosgroup.api.planner.dto.response.OperationsStatusResponse;
import com.npdinfosgroup.api.planner.service.OperationsStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The status bar at the bottom of every screen (HU-11): when the data was last updated and by whom,
 * the main counts and the next notice send. Read only, for every role.
 */
@RestController
@RequestMapping("/api/{version}/planner/operations-status")
@Tag(name = "Barra de estado", description = "Última actualización de datos, conteos y próximo envío de avisos")
class OperationsStatusController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final OperationsStatusService service;

	/**
	 * Creates the controller.
	 *
	 * @param service builds the status
	 */
	OperationsStatusController(OperationsStatusService service) {
		this.service = service;
	}

	/**
	 * The data of the status bar.
	 *
	 * @return 200 with the status
	 */
	@GetMapping(version = "v1")
	@Operation(summary = "Barra de estado", description = """
			Datos de la barra inferior de todas las pantallas (HU-11 CA-01). Sin parámetros. Todos los \
			roles, solo lectura.

			- `lastUpdatedAt` y `lastUpdatedBy`: el último cambio guardado por la API en cualquier registro \
			(la fila más reciente de la auditoría) y el nombre completo de quien lo hizo, o su usuario si \
			no es un usuario de la plataforma. Son `null` mientras no haya cambios: la importación del \
			Excel no cuenta como cambio.
			- `nextNotificationAt`: el próximo envío programado de los avisos por correo; `null` mientras \
			no haya envío programado.
			- Los conteos son de toda la base y no dependen de los filtros de la pantalla. \
			`activeResources` es el mismo número que da la búsqueda de recursos con «Estado = Activo».""")
	@ApiResponse(responseCode = "200", description = "Los datos de la barra")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<OperationsStatusResponse> status() {
		return ResponseEntity.ok(service.status());
	}

}
