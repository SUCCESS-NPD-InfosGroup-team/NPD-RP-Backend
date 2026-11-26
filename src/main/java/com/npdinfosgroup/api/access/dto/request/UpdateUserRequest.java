package com.npdinfosgroup.api.access.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Nuevos nombre y rol de un usuario, con la versión que se leyó")
public record UpdateUserRequest(
		@Schema(description = "Versión del usuario que se leyó", example = "3") @NotNull Integer version,
		@Schema(description = "Nombre completo", example = "Ana María Ríos") @NotBlank @Size(max = 150) String fullName,
		@Schema(description = "Código del rol", allowableValues = { "operations-administrator", "npd-leader",
				"customer-success", "guest" }, example = "guest") @NotBlank String role) {
}
