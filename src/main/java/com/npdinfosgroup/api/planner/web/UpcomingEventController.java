package com.npdinfosgroup.api.planner.web;

import com.npdinfosgroup.api.planner.dto.request.UpcomingEventCriteria;
import com.npdinfosgroup.api.planner.dto.request.UpcomingEventType;
import com.npdinfosgroup.api.planner.dto.response.UpcomingEventsPageResponse;
import com.npdinfosgroup.api.planner.dto.response.UpcomingEventsStripResponse;
import com.npdinfosgroup.api.planner.service.UpcomingEventQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The upcoming events list ("Arribos proximos", HU-06): what is due soon for the resources and the
 * client contracts, without reviewing the planner. Read only, for every role.
 */
@RestController
@RequestMapping("/api/{version}/planner/upcoming-events")
@Tag(name = "Eventos proximos", description = "Fines de asignacion, de contrato, de periodo de prueba e inicios de vacaciones")
class UpcomingEventController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final UpcomingEventQueryService queryService;

	/**
	 * Creates the controller.
	 *
	 * @param queryService reads the events
	 */
	UpcomingEventController(UpcomingEventQueryService queryService) {
		this.queryService = queryService;
	}

	/**
	 * A page of the upcoming events list with the counts per type.
	 *
	 * @param windowDays days ahead the list looks: 30, 60 or 90
	 * @param clientId only the events of this client
	 * @param leaderId only the events of the collaborators of this NPD lead
	 * @param type only the events of this type; the counts ignore it
	 * @param pageable page ({@code page}, from 0) and size ({@code size}, 25 by default, 100 at most)
	 * @return 200 with the counts and the page
	 */
	@GetMapping(version = "v1")
	@Operation(summary = "Listar eventos proximos", description = """
			Lista "Arribos proximos" (HU-06), paginada y ordenada por fecha. El parametro `sort` se ignora. \
			Todos los roles, solo lectura.

			- Tipos: `ASSIGNMENT_END` (fin de asignacion del colaborador), `CONTRACT_END` (fin del contrato \
			del proyecto con el cliente, para todo el equipo), `PROBATION_END` (dia 90 de quien sigue en \
			periodo de prueba), `VACATION_START` (primer dia del mes de vacaciones) y \
			`PENDING_CONFIRMATION` (fin de asignacion ya pasado que nadie extendio).
			- `PENDING_CONFIRMATION` se lista siempre, sea cual sea la ventana, con `daysRemaining` negativo.
			- `summary` usa los filtros de ventana, cliente y lider, pero no el de tipo.
			- Los colaboradores de Baja no generan eventos. Las fechas se calculan en el servidor con la \
			zona America/Bogota.
			- `notice` es siempre `NONE` mientras no existan los avisos por correo.""")
	@ApiResponse(responseCode = "200", description = "La pagina")
	@ApiResponse(responseCode = "400", description = """
			La ventana no es 30, 60 ni 90, o un parametro no tiene el formato esperado (`INVALID_REQUEST`)""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token invalido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<UpcomingEventsPageResponse> list(
			@Parameter(description = "Dias hacia adelante: 30, 60 o 90", schema = @Schema(allowableValues = { "30", "60", "90" }))
			@RequestParam(defaultValue = "30") int windowDays,
			@Parameter(description = "Solo los eventos de este cliente") @RequestParam(required = false) Long clientId,
			@Parameter(description = """
					Solo los eventos de los colaboradores de este lider NPD; un fin de contrato entra si el \
					proyecto tiene al menos uno""")
			@RequestParam(required = false) Long leaderId,
			@Parameter(description = "Solo los eventos de este tipo; los totales de `summary` no lo usan")
			@RequestParam(required = false) UpcomingEventType type,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(queryService.list(new UpcomingEventCriteria(windowDays, clientId, leaderId, type), pageable));
	}

	/**
	 * The strip at the top of Recursos (HU-06 CA-01): how many events are due in the next 30 days and
	 * the four nearest ones still to come.
	 *
	 * @return 200 with the total and the nearest events
	 */
	@GetMapping(path = "/summary", version = "v1")
	@Operation(summary = "Resumen de arribos proximos", description = """
			Franja "Arribos proximos" de Recursos (HU-06 CA-01). Sin parametros: resume toda la base para \
			los proximos 30 dias. Todos los roles, solo lectura.

			- `total` es el numero de "Ver los N eventos": los eventos de la ventana de 30 dias, \
			pendientes de confirmacion incluidos. Es el mismo `summary.total` que devuelve la lista \
			`GET /upcoming-events?windowDays=30` sin filtros.
			- `nearest` trae hasta 4 eventos de hoy en adelante, ordenados por fecha, con la misma forma \
			que las filas de la lista. No incluye los pendientes de confirmacion (fechas ya pasadas): se \
			ven en la lista.
			- Las fechas se calculan en el servidor con la zona America/Bogota.""")
	@ApiResponse(responseCode = "200", description = "El total y los eventos mas cercanos")
	@ApiResponse(responseCode = "401", description = "Sin token o token invalido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<UpcomingEventsStripResponse> summary() {
		return ResponseEntity.ok(queryService.summary());
	}

}
