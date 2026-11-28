package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Una persona a asignar; los campos con valor cambian los de defaults solo para ella")
public record MemberAssignment(
		@Schema(description = "Id del recurso", example = "890") Long employeeId,
		@Schema(description = "`version` que mostró la pantalla", example = "2") Integer version,
		@Schema(description = "Líder NPD propio") Long leaderId,
		@Schema(description = "Rol en el cliente propio") Long clientRoleId,
		@Schema(description = "Equipo / laptop propio") Short equipmentId,
		@Schema(description = "Fecha final propia", example = "2027-06-30") LocalDate endDate,
		@Schema(description = "Sin fecha final, solo para esta persona") Boolean indefinite) {
}
