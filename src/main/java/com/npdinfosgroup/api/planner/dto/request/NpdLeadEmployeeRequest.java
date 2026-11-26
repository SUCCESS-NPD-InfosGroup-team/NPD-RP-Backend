package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Registro de empleado de un líder NPD: los datos de la HU-10 que no salen del usuario")
public record NpdLeadEmployeeRequest(
		@Schema(description = "N° de empleado; único", example = "1105") String employeeNumber,
		@Schema(description = "Género (catálogo genders)") Short genderId,
		@Schema(description = "Posición en Infosgroup (catálogo positions)") Long positionId,
		@Schema(description = "Modalidad laboral (catálogo workModes)") Short workModeId,
		@Schema(description = "Fecha de inicio de contrato", example = "2026-03-03") LocalDate hireDate,
		@Schema(description = "Estado", allowableValues = { "ACTIVO", "LICENCIA" }) String status) {
}
