package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un grupo del roster que aparece en la página, con su total en todas las páginas")
public record ResourceGroupResponse(
		@Schema(description = "NOT_SPECIFIED = proyecto inactivo («No especificado», a corregir); NO_PROJECT = «Sin proyecto»; PROJECT = proyecto activo",
				allowableValues = { "NOT_SPECIFIED", "NO_PROJECT", "PROJECT" }) String type,
		@Schema(description = "null en «Sin proyecto»") Long projectId,
		String project,
		IdName client,
		@Schema(description = "Fin de contrato del proyecto; los grupos van de la más cercana a la más lejana") LocalDate contractEndDate,
		@Schema(description = "Colaboradores del grupo que cumplen los filtros, en todas las páginas") long total) {
}
