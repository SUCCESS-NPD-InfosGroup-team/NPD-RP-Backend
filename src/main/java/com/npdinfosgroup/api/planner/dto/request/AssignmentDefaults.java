package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Valores compartidos de la asignación; una persona que repite un campo lo cambia solo para ella")
public record AssignmentDefaults(
		@Schema(description = "Líder NPD; obligatorio salvo que cada persona indique el suyo") Long leaderId,
		@Schema(description = "Rol en el cliente (catálogo clientRoles)") Long clientRoleId,
		@Schema(description = "Equipo / laptop (catálogo equipment); sin valor se conserva el actual") Short equipmentId,
		@Schema(description = "Fecha final; sin valor, la del contrato del proyecto", example = "2027-03-31") LocalDate endDate,
		@Schema(description = "Sin fecha final (indefinido)") Boolean indefinite) {
}
