package com.npdinfosgroup.api.access.web;

import java.net.URI;

import com.npdinfosgroup.api.access.dto.request.CreateUserCommand;
import com.npdinfosgroup.api.access.dto.request.CreateUserRequest;
import com.npdinfosgroup.api.access.dto.request.UpdateUserCommand;
import com.npdinfosgroup.api.access.dto.request.UpdateUserRequest;
import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.service.UserEditService;
import com.npdinfosgroup.api.access.service.UserQueryService;
import com.npdinfosgroup.api.access.service.UserRegistrationService;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

	private final UserQueryService queryService;

	private final UserRegistrationService registrationService;

	private final UserEditService editService;

	private final CurrentUser currentUser;

	/**
	 * Creates the controller.
	 *
	 * @param queryService reads users
	 * @param registrationService creates users
	 * @param editService edits the name and the role of users
	 * @param currentUser who makes the request, for the audit log
	 */
	UserController(UserQueryService queryService, UserRegistrationService registrationService,
			UserEditService editService, CurrentUser currentUser) {
		this.queryService = queryService;
		this.registrationService = registrationService;
		this.editService = editService;
		this.currentUser = currentUser;
	}

	/**
	 * A page of the user list.
	 *
	 * @param pageable page ({@code page}, from 0) and size ({@code size}, 25 by default, 100 at most)
	 * @return 200 with the page
	 */
	@GetMapping(version = "v1")
	@Operation(summary = "Listar usuarios", description = """
			Lista de usuarios creados en la plataforma (HU-02), paginada y ordenada por nombre completo, sin \
			filtros. El parámetro `sort` se ignora. Solo Operations administrator.

			- `role` es el código del rol; su nombre está en «Listar roles».
			- `state`: `PENDING` (invitación enviada, aún no usa la plataforma) o `ACTIVE`.
			- `lastAccessAt` es `null` hasta su primer acceso.
			- `createdAt` y `createdBy`: cuándo y quién lo creó. `createdBy` es el nombre completo del \
			usuario que lo creó o, si esa cuenta no es un usuario de la plataforma, su nombre de usuario.
			- `version`: se envía al editar el usuario; cambia cada vez que se guarda.
			- `employee`: el registro de empleado vinculado (solo líderes NPD y antiguos líderes NPD), con \
			`id`, `employeeNumber`, `status` (`ACTIVO`, `LICENCIA`, `BAJA`) y `collaborators` (colaboradores \
			que aún lo tienen como líder, sin contar los de Baja). `null` si no tiene; un `npd-leader` sin \
			registro de empleado es un error de datos y su cuenta se debe eliminar.""")
	@ApiResponse(responseCode = "200", description = "La página")
	@ApiResponse(responseCode = "400", description = "Un parámetro no tiene el formato esperado (`INVALID_REQUEST`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<PageResponse<UserResponse>> list(@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(queryService.list(pageable));
	}

	/**
	 * One user.
	 *
	 * @param id user id
	 * @return 200 with the user
	 */
	@GetMapping(path = "/{id}", version = "v1")
	@Operation(summary = "Ver usuario", description = """
			Un usuario, con los mismos campos que «Listar usuarios»: es lo que muestra el modal «Editar». \
			Solo Operations administrator.""")
	@ApiResponse(responseCode = "200", description = "El usuario")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<UserResponse> get(@Parameter(description = "Id del usuario") @PathVariable long id) {
		return ResponseEntity.ok(queryService.get(id));
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
			- `role` es uno de: `operations-administrator`, `customer-success`, `guest`. `npd-leader` se \
			rechaza con `ROLE_NOT_ASSIGNABLE` **por ahora**: un líder NPD también necesita su registro de \
			empleado para poder elegirlo como líder, y este endpoint solo crea el usuario. Se podrá crear \
			con el registro de líderes NPD (HU-10, SCRUM-239).
			- El usuario queda en estado `PENDING` hasta que use la plataforma por primera vez.
			- Si el servicio de inicio de sesión falla, no queda nada creado y se puede reintentar (503).""")
	@ApiResponse(responseCode = "201", description = "Usuario creado e invitación enviada; `Location` apunta a él")
	@ApiResponse(responseCode = "400", description = """
			Uno o más campos inválidos (`VALIDATION_FAILED`, detalle en `errors`), del usuario y de \
			`employee` (`employee.<campo>`, mismos códigos que al registrar un recurso, como \
			`EMPLOYEE_NUMBER_TAKEN`)""",
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

	/**
	 * Changes the full name and the role of a user (SCRUM-53). The changes are audited with the
	 * administrator's username.
	 *
	 * @param id user id
	 * @param request the new values and the version that was read
	 * @return 200 with the user as it is saved
	 */
	@PutMapping(path = "/{id}", version = "v1")
	@Operation(summary = "Editar usuario", description = """
			Cambia el nombre completo y el rol de un usuario (HU-03). El correo, el estado, el último acceso \
			y la creación no se editan. Solo Operations administrator.

			- `version` es la del usuario leído; si alguien lo guardó después, la edición se rechaza (409 \
			`CONCURRENT_MODIFICATION`, con `currentVersion`).
			- `fullName` (obligatorio, hasta 150 caracteres) se guarda sin espacios al inicio ni al final, \
			también en la cuenta de inicio de sesión. Primero se informan todos los campos inválidos (400).
			- `role` es uno de: `operations-administrator`, `npd-leader`, `customer-success`, `guest`.
			- **A `npd-leader`, sin registro de empleado:** `employee` es obligatorio (N°, género, posición, \
			modalidad, fecha de inicio y estado `ACTIVO` o `LICENCIA`); sus errores van con los del usuario \
			como `employee.<campo>`. Se crea su registro de empleado como líder NPD, sin proyecto ni líder, \
			con el nombre y el correo del usuario. **Con registro (antiguo líder NPD):** `employee` se \
			ignora y su registro vuelve de Baja a Activo, sin proyecto.
			- **Si ya es empleado** (un empleado tiene su correo o el N° de `employee`): 409 \
			`EXISTING_NPD_LEAD_EMPLOYEE` con `existingEmployee` para confirmar que es la misma persona; al \
			confirmar, se reenvía con `linkEmployeeId` = `existingEmployee.id` y se vincula ese registro sin \
			cambiarlo (solo se completa su correo si no tenía). Si ese empleado no es líder NPD, está de \
			Baja o ya tiene usuario: 409 `EMPLOYEE_NOT_LINKABLE`.
			- **De `npd-leader` a otro rol:** su registro de empleado pasa a Baja por «Cambio de rol» y \
			deja su proyecto; sus colaboradores lo siguen teniendo como líder. La respuesta trae \
			`employee.id` y `employee.collaborators` para ofrecer «Ver sus colaboradores».
			- El nombre de un usuario con registro de empleado se cambia también en el registro.
			- El rol solo cambia si el registro de empleado se guarda también: ambos o ninguno.
			- Al cambiar el rol se cierran las sesiones del usuario: su siguiente petición da 401 y vuelve a \
			entrar con el nuevo rol. Cambiar solo el nombre no cierra sesiones.
			- Guardar los mismos valores no cambia nada: no se audita y `version` sigue igual.
			- Un `npd-leader` sin registro de empleado no se puede editar (`NPD_LEAD_WITHOUT_EMPLOYEE`): \
			su cuenta se debe eliminar.
			- Si el servicio de inicio de sesión falla, no queda nada cambiado y se puede reintentar (503).""")
	@ApiResponse(responseCode = "200", description = "El usuario guardado, con su nueva `version`")
	@ApiResponse(responseCode = "400", description = """
			Uno o más campos inválidos (`VALIDATION_FAILED`, detalle en `errors`), del usuario y de \
			`employee` (`employee.<campo>`, mismos códigos que al registrar un recurso, como \
			`EMPLOYEE_NUMBER_TAKEN`)""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "El rol no es Operations administrator (`FORBIDDEN`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = """
			- `CONCURRENT_MODIFICATION`: alguien guardó el usuario después de leerlo (`currentVersion` trae \
			la versión actual), o su registro de empleado se editó a la vez en Recursos.
			- `EXISTING_NPD_LEAD_EMPLOYEE`: un empleado tiene su correo o N° (`matchedBy`: `EMAIL`, \
			`EMPLOYEE_NUMBER`); `existingEmployee` trae sus datos (N°, posición, proyecto, líder…) para confirmar.
			- `EMPLOYEE_NOT_LINKABLE`: el empleado no se puede vincular; `reason`: `HAS_USER` (ya tiene \
			usuario), `NOT_NPD_LEAD` (no es líder NPD) o `TERMINATED` (de Baja, también un antiguo líder \
			dado de baja por otro motivo), con `matchedBy`, `existingResourceId` y `existingResourceName`.
			- `LAST_OPERATIONS_ADMINISTRATOR`: no quedaría ningún Operations administrator activo.
			- `NPD_LEAD_WITHOUT_EMPLOYEE`: líder NPD sin registro de empleado; su cuenta se debe eliminar.""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = """
			El servicio de inicio de sesión no respondió; no quedó nada cambiado \
			(`IDENTITY_PROVIDER_UNAVAILABLE`)""",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<UserResponse> update(@Parameter(description = "Id del usuario") @PathVariable long id,
			@RequestBody UpdateUserRequest request) {
		return ResponseEntity.ok(editService.update(new UpdateUserCommand(id, request, currentUser.username())));
	}

}
