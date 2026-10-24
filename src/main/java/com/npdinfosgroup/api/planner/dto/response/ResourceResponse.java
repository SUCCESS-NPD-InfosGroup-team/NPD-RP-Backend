package com.npdinfosgroup.api.planner.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Registro completo de un recurso")
public record ResourceResponse(long id, String employeeNumber, String name, String email, IdName jobTitle,
		IdName position, Short level, IdName businessUnit, IdName workMode, IdName gender, IdName talentReview,
		LocalDate hireDate,
		@Schema(description = "Día 90 desde la fecha de inicio de contrato") LocalDate probationEndDate,
		CodeLabel status,
		@Schema(description = "Calculada: ASIGNADO, PENDIENTE_CONFIRMACION o SIN_PROYECTO") CodeLabel situation,
		@Schema(description = "null = Sin proyecto") AssignmentResponse assignment,
		@Schema(description = "Solo con Estado = BAJA") TerminationResponse termination,
		@Schema(description = "Campos con un valor «No especificado» importado que hay que corregir") List<String> fieldsToFix,
		int version, Instant updatedAt,
		@Schema(description = "Usuario del último cambio hecho por la API; null si viene del import") String updatedBy) {
}
