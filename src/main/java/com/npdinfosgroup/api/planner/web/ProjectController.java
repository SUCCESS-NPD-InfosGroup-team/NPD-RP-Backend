package com.npdinfosgroup.api.planner.web;

import java.net.URI;
import java.time.LocalDate;

import com.npdinfosgroup.api.planner.dto.request.CreateProjectCommand;
import com.npdinfosgroup.api.planner.dto.request.CreateProjectRequest;
import com.npdinfosgroup.api.planner.dto.request.ProjectSearchCriteria;
import com.npdinfosgroup.api.planner.dto.response.PageResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectListItemResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectResponse;
import com.npdinfosgroup.api.planner.service.ProjectQueryService;
import com.npdinfosgroup.api.planner.service.ProjectRegistrationService;
import com.npdinfosgroup.api.shared.security.CurrentUser;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Projects of the clients: the list of the Projects module, creating one and reading one.
 */
@RestController
@RequestMapping("/api/{version}/projects")
@Tag(name = "Proyectos", description = "Proyectos de los clientes")
class ProjectController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final ProjectQueryService queryService;

	private final ProjectRegistrationService registrationService;

	private final CurrentUser currentUser;

	/**
	 * Creates the controller.
	 *
	 * @param queryService reads projects
	 * @param registrationService creates projects
	 * @param currentUser who makes the request, for the audit log
	 */
	ProjectController(ProjectQueryService queryService, ProjectRegistrationService registrationService,
			CurrentUser currentUser) {
		this.queryService = queryService;
		this.registrationService = registrationService;
		this.currentUser = currentUser;
	}

	/**
	 * A page of the projects list.
	 *
	 * @param clientId only the projects of this client
	 * @param leaderId only the projects with a collaborator of this NPD lead
	 * @param contractEndBefore only the projects whose contract ends on or before this date
	 * @param includeInactive whether the inactive projects are included
	 * @param pageable page ({@code page}, from 0) and size ({@code size}, 25 by default, 100 at most)
	 * @return 200 with the page
	 */
	@GetMapping(version = "v1")
	@Operation(summary = "Listar proyectos", description = """
			Lista del módulo Proyectos (HU-07), paginada y ordenada por cliente y luego por proyecto, para \
			agruparla por cliente. El parámetro `sort` se ignora. Todos los roles.

			- `daysToContractEnd` es `null` sin fin de contrato y negativo si ya pasó.
			- `teamSize = 0` es «Sin equipo». Los colaboradores de Baja no cuentan.""")
	@ApiResponse(responseCode = "200", description = "La página")
	@ApiResponse(responseCode = "400", description = "Un parámetro no tiene el formato esperado (`INVALID_REQUEST`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<PageResponse<ProjectListItemResponse>> list(
			@Parameter(description = "Solo los proyectos de este cliente") @RequestParam(required = false) Long clientId,
			@Parameter(description = "Solo los proyectos con al menos un colaborador de este líder NPD")
			@RequestParam(required = false) Long leaderId,
			@Parameter(description = "Solo los proyectos con fin de contrato en o antes de esta fecha")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractEndBefore,
			@Parameter(description = "Incluir los proyectos inactivos") @RequestParam(defaultValue = "false") boolean includeInactive,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(queryService
			.list(new ProjectSearchCriteria(clientId, leaderId, contractEndBefore, includeInactive), pageable));
	}

	/**
	 * Creates a project. Only an Operations administrator can do it; the creation is audited with their
	 * username.
	 *
	 * @param request the project, with its existing or new client
	 * @return 201 with the project and its location
	 */
	@PostMapping(version = "v1")
	@Operation(summary = "Crear proyecto", description = """
			Crea un proyecto activo y sin equipo (HU-07). Se usa desde el módulo Proyectos y desde «Crear \
			proyecto» en el registro de recurso. Solo Operations administrator.

			- Va exactamente uno: `clientId` (cliente existente y activo) o `newClient` (se crea junto con \
			el proyecto, en la misma transacción).
			- `name` es obligatorio, de hasta 150 caracteres y único dentro del cliente.
			- El inicio del proyecto es la fecha de creación; no se envía.
			- `contractEndDate` es opcional y, si llega, no puede haber pasado (hoy sí se acepta).
			- Primero se informan todos los campos inválidos (400); los nombres repetidos van aparte (409).""")
	@ApiResponse(responseCode = "201", description = "Proyecto creado; `Location` apunta a él")
	@ApiResponse(responseCode = "400", description = "Uno o más campos inválidos (`VALIDATION_FAILED`, detalle en `errors`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = """
			El cliente ya tiene un proyecto con ese nombre (`PROJECT_NAME_TAKEN`, con `existingProjectId`) o \
			ya existe un cliente con el nombre del cliente nuevo (`CLIENT_NAME_TAKEN`, con `existingClientId`)""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "La base de datos no respondió; no se guardó nada (`SERVICE_UNAVAILABLE`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ProjectResponse> create(@RequestBody CreateProjectRequest request) {
		ProjectResponse project = registrationService.create(new CreateProjectCommand(request, currentUser.username()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
			.path("/{id}")
			.buildAndExpand(project.id())
			.toUri();
		return ResponseEntity.created(location).body(project);
	}

	/**
	 * A project with its client and team size.
	 *
	 * @param id project id
	 * @return 200 with the project
	 */
	@GetMapping(path = "/{id}", version = "v1")
	@Operation(summary = "Ver proyecto", description = """
			Un proyecto con su cliente y su número de colaboradores; la misma forma que la respuesta de \
			«Crear proyecto». Todos los roles.""")
	@ApiResponse(responseCode = "200", description = "El proyecto")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ProjectResponse> get(@Parameter(description = "Id del proyecto") @PathVariable long id) {
		return ResponseEntity.ok(queryService.get(id));
	}

}
