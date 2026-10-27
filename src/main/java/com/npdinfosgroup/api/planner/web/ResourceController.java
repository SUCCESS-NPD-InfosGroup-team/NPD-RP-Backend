package com.npdinfosgroup.api.planner.web;

import java.net.URI;

import com.npdinfosgroup.api.planner.dto.request.RegisterResourceCommand;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceRequest;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.ItemsResponse;
import com.npdinfosgroup.api.planner.dto.response.ResourceResponse;
import com.npdinfosgroup.api.planner.service.ResourceQueryService;
import com.npdinfosgroup.api.planner.service.ResourceRegistrationService;
import com.npdinfosgroup.api.shared.security.CurrentUser;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Resources of the planner: registering a collaborator (with or without project) and reading their
 * record.
 */
@RestController
@RequestMapping("/api/{version}/planner/resources")
@Tag(name = "Recursos", description = "Colaboradores de NPD y su proyecto actual")
class ResourceController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final ResourceRegistrationService registrationService;

	private final ResourceQueryService queryService;

	private final CurrentUser currentUser;

	/**
	 * Creates the controller.
	 *
	 * @param registrationService registers resources
	 * @param queryService reads resources
	 * @param currentUser who makes the request, for the audit log
	 */
	ResourceController(ResourceRegistrationService registrationService, ResourceQueryService queryService,
			CurrentUser currentUser) {
		this.registrationService = registrationService;
		this.queryService = queryService;
		this.currentUser = currentUser;
	}

	/**
	 * Registers a collaborator and, optionally, their current project. Only an Operations
	 * administrator can do it; the creation is audited with their username.
	 *
	 * @param request the collaborator and, if any, their assignment to an existing project
	 * @return 201 with the record and its location
	 */
	@PostMapping(version = "v1")
	@Operation(summary = "Registrar recurso", description = """
			Registra un colaborador y, si ya tiene uno, su proyecto (HU-01). Solo Operations administrator.

			- `assignment = null` es «Sin proyecto por ahora»: no se piden proyecto, líder ni fecha final.
			- Con `assignment` son obligatorios `projectId` (proyecto existente y activo; el cliente sale \
			de él), `leaderId` y `endDate` (no pasada) o `indefinite = true`.
			- Con `status = BAJA` son obligatorios `termination.date` (no antes de `hireDate`) y \
			`termination.reasonId`, y el recurso se guarda sin proyecto aunque llegue `assignment`.
			- `onProbation` es obligatorio: indica si entra en periodo de prueba. `probationEndDate` \
			(día 90 desde `hireDate`) la calcula el sistema y se devuelve siempre.
			- La situación (Asignado, Pendiente de confirmación, Sin proyecto) no se envía: la calcula el \
			sistema.
			- Todos los errores de validación llegan juntos en `errors`. Si el único problema es el \
			N° de empleado repetido, la respuesta es 409.""")
	@ApiResponse(responseCode = "201", description = "Recurso registrado; `Location` apunta a su registro")
	@ApiResponse(responseCode = "400", description = "Uno o más campos inválidos (`VALIDATION_FAILED`, detalle en `errors`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = """
			N° de empleado ya registrado (`EMPLOYEE_NUMBER_TAKEN`), con `existingResourceId` y \
			`existingResourceName`""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "La base de datos no respondió; no se guardó nada (`SERVICE_UNAVAILABLE`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ResourceResponse> register(@RequestBody RegisterResourceRequest request) {
		ResourceResponse resource = registrationService.register(new RegisterResourceCommand(request, currentUser.username()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
			.path("/{id}")
			.buildAndExpand(resource.id())
			.toUri();
		return ResponseEntity.created(location).body(resource);
	}

	/**
	 * The full record of a resource, to show it or to prefill the edit form.
	 *
	 * @param id employee id
	 * @return 200 with the record
	 */
	@GetMapping(path = "/{id}", version = "v1")
	@Operation(summary = "Ver recurso", description = """
			Registro completo de un recurso, con su situación calculada, los campos «No especificado» \
			por corregir (`fieldsToFix`) y la `version` que exigirá la edición. Todos los roles.""")
	@ApiResponse(responseCode = "200", description = "El recurso")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ResourceResponse> get(@Parameter(description = "Id del recurso") @PathVariable long id) {
		return ResponseEntity.ok(queryService.get(id));
	}

	/**
	 * The NPD leads that can be chosen in the "Líder NPD" selector.
	 *
	 * @return 200 with the leads
	 */
	@GetMapping(path = "/npd-leads", version = "v1")
	@Operation(summary = "Líderes NPD", description = """
			Colaboradores que hoy lideran al menos a otro y no están de Baja, por nombre, como \
			`{ id, name }`. Todos los roles.""")
	@ApiResponse(responseCode = "200", description = "Los líderes")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ItemsResponse<IdName>> listNpdLeads() {
		return ResponseEntity.ok(queryService.findNpdLeads());
	}

}
