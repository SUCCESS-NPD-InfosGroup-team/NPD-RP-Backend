package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Asignación a un proyecto existente y activo")
public record AssignmentRequest(
		@Schema(description = "Proyecto asignado; su cliente sale del proyecto") @NotNull Long projectId,
		@Schema(description = "Líder NPD (otro empleado)") @NotNull Long leaderId,
		@Schema(description = "Rol actual en cliente (catálogo clientRoles)") Long clientRoleId,
		@Schema(description = "Equipo / laptop (catálogo equipment)") Short equipmentId,
		@Schema(description = "Fecha final; no puede haber pasado. Va esta fecha o indefinite = true", example = "2027-03-31") LocalDate endDate,
		@Schema(description = "Sin fecha final (indefinido); null = false") Boolean indefinite,
		@Schema(description = "Contacto por cliente") @Email @Size(max = 254) String clientContactEmail) {

	public boolean isIndefinite() {
		return Boolean.TRUE.equals(indefinite);
	}

}
