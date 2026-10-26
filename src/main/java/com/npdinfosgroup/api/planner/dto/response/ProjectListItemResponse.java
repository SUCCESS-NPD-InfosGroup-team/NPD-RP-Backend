package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectListItemResponse(long id, String name, ClientResponse client, boolean active,
		@Schema(description = "Día en que se creó el proyecto") LocalDate startDate, LocalDate contractEndDate,
		@Schema(description = "Días hasta el fin de contrato (negativo si ya pasó); null sin fin de contrato") Long daysToContractEnd,
		@Schema(description = "Colaboradores en el proyecto, sin contar Baja; 0 = «Sin equipo»") long teamSize,
		@Schema(description = "Colaboradores con fecha final vencida («N por confirmar»)") long pendingConfirmation,
		@Schema(description = "Colaboradores sin fecha final (indefinido)") long indefiniteCount,
		@Schema(description = "Fecha final más próxima entre los colaboradores") LocalDate nextAssignmentEnd,
		@Schema(description = "Líderes NPD de los colaboradores") List<IdName> leads) {
}
