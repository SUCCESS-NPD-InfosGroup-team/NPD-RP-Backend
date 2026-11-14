package com.npdinfosgroup.api.access.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record RoleResponse(
		@Schema(description = "Código del rol; el valor de `role` al crear un usuario", example = "npd-leader") String code,
		@Schema(description = "Nombre para mostrar", example = "Líder NPD") String name,
		@Schema(description = "Qué puede hacer, para la tarjeta del rol") String description) {
}
