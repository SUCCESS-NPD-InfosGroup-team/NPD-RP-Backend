package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Colaborador a registrar y, si ya tiene uno, su proyecto")
public record RegisterResourceRequest(
		@Schema(description = "N° de empleado; único", example = "1102") @NotBlank @Size(max = 20) String employeeNumber,
		@Schema(description = "Nombre de empleado", example = "Laura Gómez") @NotBlank @Size(max = 150) String name,
		@Schema(description = "Correo del recurso; único", example = "laura.gomez@infosgroup.com") @Email @Size(max = 254) String email,
		@Schema(description = "Puesto (catálogo jobTitles)") Long jobTitleId,
		@Schema(description = "Posición en Infosgroup (catálogo positions)") @NotNull Long positionId,
		@Schema(description = "Nivel, número mayor que cero", example = "3") @Positive Short level,
		@Schema(description = "UEN (catálogo businessUnits)") Short businessUnitId,
		@Schema(description = "Modalidad laboral (catálogo workModes)") @NotNull Short workModeId,
		@Schema(description = "Género (catálogo genders)") @NotNull Short genderId,
		@Schema(description = "Resultado de Talent Review (catálogo talentReviews)") Short talentReviewId,
		@Schema(description = "Fecha de inicio de contrato", example = "2026-10-20") @NotNull LocalDate hireDate,
		@Schema(description = "En periodo de prueba. La fecha en que termina (día 90) la calcula el sistema") @NotNull Boolean onProbation,
		@Schema(description = "Estado", allowableValues = { "ACTIVO", "LICENCIA", "BAJA" }) @NotBlank String status,
		@Schema(description = "Desvinculación; obligatoria con Estado = BAJA y se ignora con los demás") TerminationRequest termination,
		@Schema(description = "Proyecto; null = «Sin proyecto por ahora». Se ignora con Estado = BAJA") AssignmentRequest assignment) {
}
