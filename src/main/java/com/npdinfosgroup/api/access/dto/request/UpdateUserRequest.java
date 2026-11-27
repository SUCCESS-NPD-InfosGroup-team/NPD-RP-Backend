package com.npdinfosgroup.api.access.dto.request;

import com.npdinfosgroup.api.planner.dto.request.NpdLeadEmployeeRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Nuevos nombre y rol de un usuario, con la versión que se leyó")
public record UpdateUserRequest(
		@Schema(description = "Versión del usuario que se leyó", example = "3") @NotNull Integer version,
		@Schema(description = "Nombre completo", example = "Ana María Ríos") @NotBlank @Size(max = 150) String fullName,
		@Schema(description = "Código del rol", allowableValues = { "operations-administrator", "npd-leader",
				"customer-success", "guest" }, example = "guest") @NotBlank String role,
		@Schema(description = """
				Registro de empleado del líder NPD: obligatorio solo al cambiar a `npd-leader` un usuario sin \
				registro; si no, se ignora""") NpdLeadEmployeeRequest employee,
		@Schema(description = """
				Confirma el vínculo con el empleado que ofreció una respuesta `EXISTING_NPD_LEAD_EMPLOYEE`: su \
				`existingEmployee.id`""") Long linkEmployeeId) {
}
