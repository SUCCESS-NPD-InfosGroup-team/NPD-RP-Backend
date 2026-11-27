package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
		@Schema(description = "Nombre; único dentro del cliente", example = "Banesco - Portal de clientes") @NotBlank @Size(max = 150) String name,
		@Schema(description = "Fin de contrato con el cliente; null lo quita. Si cambia, no puede haber pasado") LocalDate contractEndDate,
		@Schema(description = "`version` del proyecto que se leyó; si otra persona lo cambió después, responde 409", example = "3") @NotNull Integer version) {
}
