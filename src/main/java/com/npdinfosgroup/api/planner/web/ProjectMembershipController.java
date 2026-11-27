package com.npdinfosgroup.api.planner.web;

import com.npdinfosgroup.api.planner.dto.response.AssignmentCandidatesResponse;
import com.npdinfosgroup.api.planner.service.AssignmentCandidateService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The team of a project (HU-08): who can join it. Only the Operations administrator ({@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/{version}/planner/projects/{projectId}")
@Tag(name = "Equipo del proyecto", description = "Agregar, mover y quitar colaboradores de un proyecto")
class ProjectMembershipController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final AssignmentCandidateService candidateService;

	/**
	 * Creates the controller.
	 *
	 * @param candidateService reads the people who can join a project
	 */
	ProjectMembershipController(AssignmentCandidateService candidateService) {
		this.candidateService = candidateService;
	}

	/**
	 * The people who can be added to a project, for "Agregar colaboradores".
	 *
	 * @param projectId the project
	 * @param text name or employee number to look for
	 * @param pageable page number and size
	 * @return 200 with the page
	 */
	@GetMapping(path = "/candidates", version = "v1")
	@Operation(summary = "Candidatos para agregar al proyecto", description = """
			Personas que se pueden agregar al proyecto (HU-08 CA-01, «Agregar colaboradores»), 25 por página: \
			todas menos las de Baja y las que ya están en él. Solo Operations administrator.

			- Orden fijo (`sort` se ignora): primero las «Sin proyecto», luego el resto, por nombre.
			- `client` y `project` no null = su proyecto actual: al agregarla, cambia de proyecto.
			- `q` busca sin distinguir mayúsculas ni acentos, como «Buscar y filtrar recursos».
			- `withoutProject`: las «Sin proyecto» de todas las páginas. `version` se envía al asignar.""")
	@ApiResponse(responseCode = "200", description = "La página de candidatos")
	@ApiResponse(responseCode = "400", description = "El id no es un número (`INVALID_REQUEST`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "El proyecto no existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<AssignmentCandidatesResponse> candidates(
			@Parameter(description = "Id del proyecto") @PathVariable long projectId,
			@Parameter(description = "Nombre o N° de empleado a buscar")
			@RequestParam(name = "q", required = false) String text,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(candidateService.findCandidates(projectId, text, pageable));
	}

}
