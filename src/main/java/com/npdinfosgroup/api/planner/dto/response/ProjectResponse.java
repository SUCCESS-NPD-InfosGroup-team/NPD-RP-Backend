package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectResponse(long id, String name, ClientResponse client, boolean active,
		@Schema(description = "Día en que se creó el proyecto") LocalDate startDate, LocalDate contractEndDate,
		@Schema(description = "Colaboradores en el proyecto, sin contar Baja; 0 = «Sin equipo»") long teamSize) {
}
