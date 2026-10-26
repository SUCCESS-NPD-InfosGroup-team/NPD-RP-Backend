package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Proyecto a crear, de un cliente existente (clientId) o de uno nuevo (newClient)")
public record CreateProjectRequest(
		@Schema(description = "Cliente existente y activo; va este o newClient", example = "3") Long clientId,
		@Schema(description = "Cliente nuevo, que se crea junto con el proyecto; va este o clientId") NewClientRequest newClient,
		@Schema(description = "Nombre; único dentro del cliente", example = "Banesco - Portal de clientes") @NotBlank @Size(max = 150) String name,
		@Schema(description = "Fin de contrato con el cliente; opcional", example = "2027-03-31") LocalDate contractEndDate) {
}
