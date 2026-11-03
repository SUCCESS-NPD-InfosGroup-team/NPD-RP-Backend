package com.npdinfosgroup.api.access.web;

import java.net.URI;

import com.npdinfosgroup.api.access.dto.request.CreateUserCommand;
import com.npdinfosgroup.api.access.dto.request.CreateUserRequest;
import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.service.UserRegistrationService;
import com.npdinfosgroup.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Users of the platform. Only Operations administrators can use these routes (see
 * {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/{version}/access/users")
@Tag(name = "Usuarios", description = "Cuentas de los usuarios de la plataforma y sus roles")
class UserController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final UserRegistrationService registrationService;

	private final CurrentUser currentUser;

	/**
	 * Creates the controller.
	 *
	 * @param registrationService creates users
	 * @param currentUser who makes the request, for the audit log
	 */
	UserController(UserRegistrationService registrationService, CurrentUser currentUser) {
		this.registrationService = registrationService;
		this.currentUser = currentUser;
	}

	/**
	 * Creates a user account with its role and sends the invitation email. The creation is audited
	 * with the administrator's username.
	 *
	 * @param request the user and its role
	 * @return 201 with the user and its location
	 */
	@PostMapping(version = "v1")
	@Operation(summary = "Crear usuario", description = """
			Crea la cuenta del usuario con su rol (HU-02) y le envía un correo para que defina su \
			contraseña; el enlace vale 48 horas y lo lleva a la página de recursos. Solo Operations \
			administrator.

			- `fullName` (hasta 150 caracteres), `email` (hasta 254, también es el usuario) y `role` son \
			obligatorios. Primero se informan todos los campos inválidos (400).
			- `role` es uno de: `operations-administrator`, `npd-leader`, `customer-success`, `guest`.
			- El usuario queda en estado `PENDING` hasta que use la plataforma por primera vez.
			- Si el servicio de inicio de sesión falla, no queda nada creado y se puede reintentar (503).""")
	@ApiResponse(responseCode = "201", description = "Usuario creado e invitación enviada; `Location` apunta a él")
	@ApiResponse(responseCode = "400", description = "Uno o más campos inválidos (`VALIDATION_FAILED`, detalle en `errors`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese correo (`EMAIL_TAKEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = """
			El servicio de inicio de sesión no respondió o no pudo enviar el correo; no se creó nada \
			(`IDENTITY_PROVIDER_UNAVAILABLE`)""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {
		UserResponse user = registrationService.register(new CreateUserCommand(request, currentUser.username()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(user.id()).toUri();
		return ResponseEntity.created(location).body(user);
	}

}
