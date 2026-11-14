package com.npdinfosgroup.api.history.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un campo que cambió en la operación")
public record FieldChangeResponse(
		@Schema(description = "Nombre técnico del campo", example = "assignment.endDate") String field,
		@Schema(description = "Nombre del campo para mostrar", example = "Fecha final") String label,
		@Schema(description = "Valor anterior; null en un registro o si estaba vacío", example = "2026-09-30") String oldValue,
		@Schema(description = "Valor nuevo; null si quedó vacío", example = "2026-10-31") String newValue) {
}
