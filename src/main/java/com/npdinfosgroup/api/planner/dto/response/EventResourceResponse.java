package com.npdinfosgroup.api.planner.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record EventResourceResponse(
		@Schema(description = "Id del colaborador") long id,
		@Schema(description = "Numero de empleado") String employeeNumber,
		@Schema(description = "Nombre completo") String name,
		@Schema(description = "Rol en el cliente; null si no tiene") String clientRole) {
}
