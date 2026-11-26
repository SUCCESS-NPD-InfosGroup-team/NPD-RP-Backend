package com.npdinfosgroup.api.planner.web;

import java.net.URI;
import java.time.LocalDate;

import com.npdinfosgroup.api.planner.dto.request.ContractEndFilter;
import com.npdinfosgroup.api.planner.dto.request.CreateProjectCommand;
import com.npdinfosgroup.api.planner.dto.request.CreateProjectRequest;
import com.npdinfosgroup.api.planner.dto.request.ProjectActivationCommand;
import com.npdinfosgroup.api.planner.dto.request.ProjectSearchCriteria;
import com.npdinfosgroup.api.planner.dto.request.ProjectVersionRequest;
import com.npdinfosgroup.api.planner.dto.request.UpdateProjectCommand;
import com.npdinfosgroup.api.planner.dto.request.UpdateProjectRequest;
import com.npdinfosgroup.api.planner.dto.response.ProjectKpisResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectListItemResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectMembersResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectResponse;
import com.npdinfosgroup.api.planner.dto.response.UpdateProjectResponse;
import com.npdinfosgroup.api.planner.service.ProjectQueryService;
import com.npdinfosgroup.api.planner.service.ProjectRegistrationService;
import com.npdinfosgroup.api.planner.service.ProjectTeamService;
import com.npdinfosgroup.api.planner.service.ProjectUpdateService;
import com.npdinfosgroup.api.shared.dto.PageResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Projects of the clients: the list of the Projects module and its KPIs, creating, editing, deactivating
 * and reactivating one, and reading one with its team.
 */
@RestController
@RequestMapping("/api/{version}/planner/projects")
@Tag(name = "Proyectos", description = "Proyectos de los clientes")
class ProjectController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private static final String CONTRACT_END_DESCRIPTION = """
			Fin de contrato: `NEXT_30_DAYS` (entre hoy y hoy + 30 días) o `NONE` (sin fecha). Sin valor no \
			filtra""";

	private static final String SEARCH_DESCRIPTION = """
			Parte del nombre del proyecto o del cliente, sin distinguir mayúsculas. Vacío no filtra; se usan \
			los primeros 150 caracteres""";

	private final ProjectQueryService queryService;

	private final ProjectRegistrationService registrationService;

	private final ProjectTeamService teamService;

	private final ProjectUpdateService updateService;

	private final CurrentUser currentUser;

	/**
	 * Creates the controller.
	 *
	 * @param queryService reads projects
	 * @param registrationService creates projects
	 * @param teamService reads the team of a project
	 * @param updateService edits projects
	 * @param currentUser who makes the request, for the audit log
	 */
	ProjectController(ProjectQueryService queryService, ProjectRegistrationService registrationService,
			ProjectTeamService teamService, ProjectUpdateService updateService, CurrentUser currentUser) {
		this.queryService = queryService;
		this.registrationService = registrationService;
		this.teamService = teamService;
		this.updateService = updateService;
		this.currentUser = currentUser;
	}

	/**
	 * A page of the projects list.
	 *
	 * @param clientId only the projects of this client
	 * @param leaderId only the projects with a collaborator of this NPD lead
	 * @param contractEndBefore only the projects whose contract ends on or before this date
	 * @param contractEnd «Próximos 30 días» or «Sin fecha»
	 * @param search part of the project or client name
	 * @param includeInactive whether the inactive projects are included
	 * @param pageable page ({@code page}, from 0) and size ({@code size}, 25 by default, 100 at most)
	 * @return 200 with the page
	 */
	@GetMapping(version = "v1")
	@Operation(summary = "Listar proyectos", description = """
			Lista del módulo Proyectos (HU-07), paginada y ordenada por cliente y luego por proyecto, para \
			agruparla por cliente. El parámetro `sort` se ignora. Todos los roles.

			- `daysToContractEnd` es `null` sin fin de contrato y negativo si ya pasó.
			- `teamSize = 0` es «Sin equipo». Los colaboradores de Baja no cuentan.
			- `contractEnd` y `search` se combinan con los demás filtros (AND); las fechas se calculan \
			en el servidor con la zona America/Bogota.""")
	@ApiResponse(responseCode = "200", description = "La página")
	@ApiResponse(responseCode = "400", description = "Un parámetro no tiene el formato esperado (`INVALID_REQUEST`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<PageResponse<ProjectListItemResponse>> list(
			@Parameter(description = "Solo los proyectos de este cliente") @RequestParam(required = false) Long clientId,
			@Parameter(description = "Solo los proyectos con al menos un colaborador de este líder NPD")
			@RequestParam(required = false) Long leaderId,
			@Parameter(description = "Solo los proyectos con fin de contrato en o antes de esta fecha (preferir `contractEnd`)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractEndBefore,
			@Parameter(description = CONTRACT_END_DESCRIPTION) @RequestParam(required = false) ContractEndFilter contractEnd,
			@Parameter(description = SEARCH_DESCRIPTION) @RequestParam(required = false) String search,
			@Parameter(description = "Incluir los proyectos inactivos") @RequestParam(defaultValue = "false") boolean includeInactive,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(queryService.list(
				new ProjectSearchCriteria(clientId, leaderId, contractEndBefore, contractEnd, search, includeInactive),
				pageable));
	}

	/**
	 * The KPIs above the projects list, over every project that matches the filters.
	 *
	 * @param clientId only the projects of this client
	 * @param leaderId only the projects with a collaborator of this NPD lead
	 * @param contractEnd «Próximos 30 días» or «Sin fecha»
	 * @param search part of the project or client name
	 * @param includeInactive whether the inactive projects are included
	 * @return 200 with the KPIs
	 */
	@GetMapping(path = "/kpis", version = "v1")
	@Operation(summary = "Indicadores de proyectos", description = """
			Indicadores del módulo Proyectos (HU-07) con los mismos filtros que la lista, calculados sobre \
			todos los proyectos que cumplen los filtros y no solo sobre la página visible. Todos los roles.

			- `contractEndingSoon` cuenta los contratos que terminan entre hoy y hoy + 30 días (los ya \
			vencidos no cuentan).
			- `collaboratorsWithoutProject` cuenta a los colaboradores en estado Activo sin proyecto en \
			toda la base; no depende de los filtros.""")
	@ApiResponse(responseCode = "200", description = "Los indicadores")
	@ApiResponse(responseCode = "400", description = "Un parámetro no tiene el formato esperado (`INVALID_REQUEST`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ProjectKpisResponse> kpis(
			@Parameter(description = "Solo los proyectos de este cliente") @RequestParam(required = false) Long clientId,
			@Parameter(description = "Solo los proyectos con al menos un colaborador de este líder NPD")
			@RequestParam(required = false) Long leaderId,
			@Parameter(description = CONTRACT_END_DESCRIPTION) @RequestParam(required = false) ContractEndFilter contractEnd,
			@Parameter(description = SEARCH_DESCRIPTION) @RequestParam(required = false) String search,
			@Parameter(description = "Incluir los proyectos inactivos") @RequestParam(defaultValue = "false") boolean includeInactive) {
		return ResponseEntity.ok(queryService
			.kpis(new ProjectSearchCriteria(clientId, leaderId, null, contractEnd, search, includeInactive)));
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
	 * A project with its client and the team figures of its row in the list, for the header of the
	 * project detail (HU-13).
	 *
	 * @param id project id
	 * @return 200 with the project
	 */
	@GetMapping(path = "/{id}", version = "v1")
	@Operation(summary = "Ver proyecto", description = """
			Encabezado del detalle de un proyecto (HU-13): su cliente y las mismas cifras de su fila en la \
			lista (`daysToContractEnd`, `teamSize`, `pendingConfirmation`, `indefiniteCount`, \
			`nextAssignmentEnd`, `leads`). La misma forma que la respuesta de «Crear proyecto». Incluye los \
			proyectos inactivos. Todos los roles.""")
	@ApiResponse(responseCode = "200", description = "El proyecto")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ProjectResponse> get(@Parameter(description = "Id del proyecto") @PathVariable long id) {
		return ResponseEntity.ok(queryService.get(id));
	}

	/**
	 * The team of a project for the table and the timeline of the project detail (HU-13).
	 *
	 * @param id project id
	 * @return 200 with the members
	 */
	@GetMapping(path = "/{id}/members", version = "v1")
	@Operation(summary = "Equipo del proyecto", description = """
			Colaboradores del proyecto para la tabla y la línea de tiempo del detalle (HU-13), sin los de \
			Baja: los mismos que cuenta `teamSize`. Todos los roles; también para proyectos inactivos.

			- Orden fijo: primero Pendiente de confirmación, luego por fecha final (la más próxima \
			primero, los indefinidos al final) y luego por nombre.
			- La barra de cada persona va de `assignmentStart` a `assignmentEnd`; la del proyecto, de su \
			`startDate` a su `contractEndDate` («Ver proyecto»).
			- `version` es la del colaborador, para «Quitar del proyecto» y «Editar».
			- Máximo 200 filas; `total` siempre es el tamaño real del equipo.""")
	@ApiResponse(responseCode = "200", description = "El equipo; `items` vacío = «Proyecto sin equipo»")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ProjectMembersResponse> members(@Parameter(description = "Id del proyecto") @PathVariable long id) {
		return ResponseEntity.ok(teamService.members(id));
	}

	/**
	 * Edits the name and the client contract end of a project (HU-14). Only an Operations administrator
	 * can do it; each change is audited with their username.
	 *
	 * @param id project id
	 * @param request the new values, with the version that was read
	 * @return 200 with the project and how many people moved their end date with the contract end
	 */
	@PutMapping(path = "/{id}", version = "v1")
	@Operation(summary = "Editar proyecto", description = """
			Cambia el nombre y el fin de contrato con el cliente de un proyecto activo (HU-14). El cliente no \
			cambia: un `clientId` en el cuerpo se ignora. Solo Operations administrator.

			- `name` es obligatorio, de hasta 150 caracteres y único dentro del cliente.
			- `contractEndDate` es opcional (null lo quita) y, si cambia, no puede haber pasado (hoy sí se \
			acepta); si no cambia, se acepta aunque ya haya pasado.
			- `version` es la del proyecto que se leyó; si otra persona lo guardó después, responde 409.
			- `membersEndDateMoved`: colaboradores cuya fecha final era el fin de contrato anterior y se \
			movió con él.
			- Sin cambios responde 200 con la misma `version`.""")
	@ApiResponse(responseCode = "200", description = "Proyecto guardado")
	@ApiResponse(responseCode = "400", description = "Uno o más campos inválidos (`VALIDATION_FAILED`, detalle en `errors`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = """
			Otro proyecto del cliente tiene ese nombre (`PROJECT_NAME_TAKEN`, con `existingProjectId`), otra \
			persona lo guardó después de leerlo (`CONCURRENT_MODIFICATION`, con `currentVersion`, `updatedAt` \
			y `updatedBy`) o el proyecto está inactivo (`PROJECT_INACTIVE`)""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<UpdateProjectResponse> update(@Parameter(description = "Id del proyecto") @PathVariable long id,
			@RequestBody UpdateProjectRequest request) {
		return ResponseEntity.ok(updateService.update(new UpdateProjectCommand(id, request, currentUser.username())));
	}

	/**
	 * Deactivates a project without team (HU-14 CA-01). Only an Operations administrator can do it.
	 *
	 * @param id project id
	 * @param request the version that was read
	 * @return 200 with the project
	 */
	@PostMapping(path = "/{id}/deactivation", version = "v1")
	@Operation(summary = "Desactivar proyecto", description = """
			Desactiva un proyecto sin equipo (los de Baja no cuentan) para que deje de aparecer como activo \
			(HU-14). Si ya está inactivo, responde 200 sin cambios. Se audita. Solo Operations administrator.""")
	@ApiResponse(responseCode = "200", description = "El proyecto, con `active = false`")
	@ApiResponse(responseCode = "400", description = "Falta `version` (`VALIDATION_FAILED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = """
			Tiene colaboradores (`PROJECT_HAS_TEAM`, con `teamSize`) u otra persona lo guardó después de \
			leerlo (`CONCURRENT_MODIFICATION`)""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ProjectResponse> deactivate(@Parameter(description = "Id del proyecto") @PathVariable long id,
			@RequestBody ProjectVersionRequest request) {
		return ResponseEntity
			.ok(updateService.deactivate(new ProjectActivationCommand(id, request.version(), currentUser.username())));
	}

	/**
	 * Reactivates an inactive project (HU-14 CA-05). Only an Operations administrator can do it.
	 *
	 * @param id project id
	 * @param request the version that was read
	 * @return 200 with the project
	 */
	@PostMapping(path = "/{id}/reactivation", version = "v1")
	@Operation(summary = "Reactivar proyecto", description = """
			Reactiva un proyecto inactivo (HU-14). Conserva su fin de contrato aunque ya haya pasado. Si ya \
			está activo, responde 200 sin cambios. Se audita. Solo Operations administrator.""")
	@ApiResponse(responseCode = "200", description = "El proyecto, con `active = true`")
	@ApiResponse(responseCode = "400", description = """
			Falta `version`, o el cliente está inactivo (`clientId` con `INACTIVE_CLIENT`, el proyecto \
			«No especificado» del import) (`VALIDATION_FAILED`)""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Otra persona lo guardó después de leerlo (`CONCURRENT_MODIFICATION`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ProjectResponse> reactivate(@Parameter(description = "Id del proyecto") @PathVariable long id,
			@RequestBody ProjectVersionRequest request) {
		return ResponseEntity
			.ok(updateService.reactivate(new ProjectActivationCommand(id, request.version(), currentUser.username())));
	}

}
