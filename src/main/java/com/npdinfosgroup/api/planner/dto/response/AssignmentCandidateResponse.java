package com.npdinfosgroup.api.planner.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Una persona que se puede agregar al proyecto")
public record AssignmentCandidateResponse(long id, String employeeNumber, String name, Short level,
		@Schema(description = "Rol actual en el cliente; null = «Sin rol»") String clientRole,
		CodeLabel status,
		@Schema(description = "Cliente de su proyecto actual; null = «Sin proyecto»") IdName client,
		@Schema(description = "Proyecto actual; null = «Sin proyecto». Si no es null, la persona cambiará de proyecto") IdName project,
		@Schema(description = "`version` del registro, para enviarla al asignar", example = "3") Integer version) {
}
