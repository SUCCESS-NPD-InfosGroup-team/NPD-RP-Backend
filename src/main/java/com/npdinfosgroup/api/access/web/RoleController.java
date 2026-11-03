package com.npdinfosgroup.api.access.web;

import java.util.Locale;

import com.npdinfosgroup.api.access.dto.response.RolesResponse;
import com.npdinfosgroup.api.access.service.RoleQueryService;
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
 * Roles a user can get. Only Operations administrators can use this route (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/{version}/access/roles")
@Tag(name = "Usuarios", description = "Cuentas de los usuarios de la plataforma y sus roles")
class RoleController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final RoleQueryService roles;

	/**
	 * Creates the controller.
	 *
	 * @param roles reads the roles
	 */
	RoleController(RoleQueryService roles) {
		this.roles = roles;
	}

	/**
	 * The roles, with their names and descriptions.
	 *
	 * @param locale language of the request
	 * @return 200 with the roles
	 */
	@GetMapping(version = "v1")
	@Operation(summary = "Listar roles", description = """
			Los cuatro roles, con su nombre y descripción para las tarjetas del panel «Crear usuario» \
			(HU-02). `code` es el valor de `role` al crear un usuario. Solo Operations administrator.""")
	@ApiResponse(responseCode = "200", description = "Los roles")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<RolesResponse> list(Locale locale) {
		return ResponseEntity.ok(roles.list(locale));
	}

}
