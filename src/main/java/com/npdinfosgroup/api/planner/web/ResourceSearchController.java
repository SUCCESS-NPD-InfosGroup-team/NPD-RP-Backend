package com.npdinfosgroup.api.planner.web;

import java.util.List;

import com.npdinfosgroup.api.planner.dto.request.ResourceSearchCriteria;
import com.npdinfosgroup.api.planner.dto.response.ResourceSearchResponse;
import com.npdinfosgroup.api.planner.service.ResourceSearchService;
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
 * The roster: searching and filtering the resources of the planner (HU-03).
 */
@RestController
@RequestMapping("/api/{version}/planner/resources")
@Tag(name = "Recursos", description = "Colaboradores de NPD y su proyecto actual")
class ResourceSearchController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final ResourceSearchService searchService;

	/**
	 * Creates the controller.
	 *
	 * @param searchService searches the roster
	 */
	ResourceSearchController(ResourceSearchService searchService) {
		this.searchService = searchService;
	}

	/**
	 * A page of the roster that matches every filter, grouped by project.
	 *
	 * @param text text to look for in the name, employee number, project or client
	 * @param clientId only this client
	 * @param projectId only this project
	 * @param leaderId only this NPD lead's collaborators
	 * @param clientRoleId only this role in the client
	 * @param level only this level
	 * @param statuses only these employment statuses
	 * @param situations only these situations
	 * @param pageable page number and size
	 * @return 200 with the page
	 */
	@GetMapping(version = "v1")
	@Operation(summary = "Buscar y filtrar recursos", description = """
			Roster de recursos (HU-03): una fila por colaborador, 25 por página, que cumple **todos** los \\
			filtros a la vez. Todos los roles.

			- `q` busca en nombre, N° de empleado, proyecto y cliente, sin distinguir mayúsculas ni \\
			acentos («zuniga» encuentra a «Zúñiga»).
			- `status` y `situation` se pueden repetir y aceptan cualquiera de los valores. El filtro \\
			«Activo» de la pantalla incluye Licencia: `status=ACTIVO&status=LICENCIA`.
			- Orden fijo (`sort` se ignora): primero el grupo «No especificado» (proyecto inactivo, a \\
			corregir), luego «Sin proyecto», luego los proyectos cuyo fin de contrato está más cerca. \\
			Dentro de cada grupo, primero quien termina antes.
			- `groups` trae los grupos de las filas de la página con su `total` en todas las páginas, para \\
			mostrar «2 colaboradores» o «1 de 2 · sigue en la pág. 2».
			- Un estado o situación desconocidos responden 400.""")
	@ApiResponse(responseCode = "200", description = "La página del roster")
	@ApiResponse(responseCode = "400", description = "`status` o `situation` desconocidos (`VALIDATION_FAILED`, detalle en `errors`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ResourceSearchResponse> search(
			@Parameter(description = "Texto a buscar en nombre, N° de empleado, proyecto o cliente")
			@RequestParam(name = "q", required = false) String text,
			@Parameter(description = "Solo este cliente") @RequestParam(required = false) Long clientId,
			@Parameter(description = "Solo este proyecto") @RequestParam(required = false) Long projectId,
			@Parameter(description = "Solo los colaboradores de este líder NPD") @RequestParam(required = false) Long leaderId,
			@Parameter(description = "Solo este rol en el cliente (catálogo clientRoles)") @RequestParam(required = false) Long clientRoleId,
			@Parameter(description = "Solo este nivel") @RequestParam(required = false) Short level,
			@Parameter(description = "Estados (ACTIVO, LICENCIA, BAJA); se puede repetir")
			@RequestParam(name = "status", required = false) List<String> statuses,
			@Parameter(description = "Situaciones (ASIGNADO, PENDIENTE_CONFIRMACION, SIN_PROYECTO); se puede repetir")
			@RequestParam(name = "situation", required = false) List<String> situations,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(searchService.search(new ResourceSearchCriteria(text, clientId, projectId, leaderId,
				clientRoleId, level, statuses, situations), pageable));
	}

}
