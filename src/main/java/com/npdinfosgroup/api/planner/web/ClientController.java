package com.npdinfosgroup.api.planner.web;

import com.npdinfosgroup.api.planner.dto.response.ClientResponse;
import com.npdinfosgroup.api.planner.dto.response.ItemsResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectOptionResponse;
import com.npdinfosgroup.api.planner.service.ClientQueryService;
import com.npdinfosgroup.api.planner.service.ProjectQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clients and the projects each one has.
 */
@RestController
@RequestMapping("/api/{version}/clients")
@Tag(name = "Clientes", description = "Clientes y sus proyectos")
class ClientController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final ClientQueryService clientQueryService;

	private final ProjectQueryService projectQueryService;

	/**
	 * Creates the controller.
	 *
	 * @param clientQueryService reads the clients
	 * @param projectQueryService reads the projects
	 */
	ClientController(ClientQueryService clientQueryService, ProjectQueryService projectQueryService) {
		this.clientQueryService = clientQueryService;
		this.projectQueryService = projectQueryService;
	}

	/**
	 * The clients, for the "Cliente" selectors of the project and resource forms.
	 *
	 * @param active {@code true} for the active clients, {@code false} for the inactive ones
	 * @return 200 with the clients
	 */
	@GetMapping(version = "v1")
	@Operation(summary = "Listar clientes", description = """
			Clientes por nombre, para el selector «Cliente» del formulario de proyecto y del registro de \
			recurso. Todos los roles.""")
	@ApiResponse(responseCode = "200", description = "Los clientes")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ItemsResponse<ClientResponse>> list(@Parameter(
			description = "true: solo los activos (por defecto); false: solo los inactivos") @RequestParam(
					defaultValue = "true") boolean active) {
		return ResponseEntity.ok(clientQueryService.list(active));
	}

	/**
	 * The active projects of a client, for the "Proyecto asignado" selector.
	 *
	 * @param clientId client id
	 * @return 200 with the projects
	 */
	@GetMapping(path = "/{clientId}/projects", version = "v1")
	@Operation(summary = "Proyectos activos del cliente", description = """
			Lista liviana para el selector «Proyecto asignado» del registro y la edición de recursos: solo \
			proyectos activos, por nombre, con su número de colaboradores (sin contar Baja) y su fin de \
			contrato. Todos los roles.""")
	@ApiResponse(responseCode = "200", description = "Los proyectos; `items` vacío si el cliente no tiene activos")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "El cliente no existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ItemsResponse<ProjectOptionResponse>> getProjects(
			@Parameter(description = "Id del cliente") @PathVariable long clientId) {
		return ResponseEntity.ok(projectQueryService.findActiveByClient(clientId));
	}

}
