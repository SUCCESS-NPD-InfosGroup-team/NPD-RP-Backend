package com.npdinfosgroup.api.history.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un campo que se puede elegir en el filtro «Campo»")
public record AuditFieldOption(
		@Schema(description = "Nombre técnico, el valor del parámetro field", example = "assignment.endDate") String field,
		@Schema(description = "Nombre del campo para mostrar", example = "Fecha final") String label) {
}
