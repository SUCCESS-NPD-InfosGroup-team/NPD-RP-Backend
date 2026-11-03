package com.npdinfosgroup.api.access.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Usuario a crear con su rol")
public record CreateUserRequest(
		@Schema(description = "Nombre completo", example = "Ana María Ríos") @NotBlank @Size(max = 150) String fullName,
		@Schema(description = "Correo; también es el usuario para iniciar sesión", example = "ana.rios@infosgroup.com") @NotBlank @Email(regexp = ".+@.+\\..+") @Size(max = 254) String email,
		@Schema(description = "Código del rol", allowableValues = { "operations-administrator", "npd-leader",
				"customer-success", "guest" }, example = "npd-leader") @NotBlank String role) {
}
