package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Registro completo del recurso tal como queda después de editarlo, con la versión que se leyó")
public record UpdateResourceRequest(
		@Schema(description = "N° de empleado; único", example = "1102") @NotBlank @Size(max = 20) String employeeNumber,
		@Schema(description = "Nombre de empleado", example = "Laura Gómez") @NotBlank @Size(max = 150) String name,
		@Schema(description = "Correo del recurso; único", example = "laura.gomez@infosgroup.com") @Email @Size(max = 254) String email,
		@Schema(description = "Puesto (catálogo jobTitles)") Long jobTitleId,
		@Schema(description = "Posición en Infosgroup (catálogo positions)") @NotNull Long positionId,
		@Schema(description = "Nivel, número mayor que cero", example = "3") @Positive Short level,
		@Schema(description = "UEN (catálogo businessUnits)") Short businessUnitId,
		@Schema(description = "Modalidad laboral (catálogo workModes); «No especificado» se rechaza") @NotNull Short workModeId,
		@Schema(description = "Género (catálogo genders)") @NotNull Short genderId,
		@Schema(description = "Resultado de Talent Review (catálogo talentReviews)") Short talentReviewId,
		@Schema(description = "Fecha de inicio de contrato", example = "2024-03-01") @NotNull LocalDate hireDate,
		@Schema(description = "En periodo de prueba. La fecha en que termina (día 90) la calcula el sistema") @NotNull Boolean onProbation,
		@Schema(description = "Estado. Con BAJA la persona sale de su proyecto", allowableValues = { "ACTIVO", "LICENCIA", "BAJA" }) @NotBlank String status,
		@Schema(description = "Desvinculación; obligatoria con Estado = BAJA y se ignora con los demás") TerminationRequest termination,
		@Schema(description = "Proyecto actual; null = «Sin proyecto». Otro projectId mueve a la persona. Se ignora con Estado = BAJA") AssignmentRequest assignment,
		@Schema(description = "`version` del registro que se leyó (GET); si otra persona lo cambió después, responde 409", example = "3") @NotNull Integer version)
		implements ResourceForm {
}
