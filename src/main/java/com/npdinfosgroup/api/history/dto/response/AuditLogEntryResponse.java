package com.npdinfosgroup.api.history.dto.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Una entrada de la auditoría: la edición de un campo o el alta del registro")
public record AuditLogEntryResponse(
		@Schema(description = "Id estable de la entrada", example = "812") long id,
		@Schema(description = "CREATE (Alta) o UPDATE (Edición)", allowableValues = { "CREATE", "UPDATE" }) String action,
		@Schema(description = "Usuario de Keycloak que hizo la operación", example = "maria.perez") String actor,
		@Schema(description = "Momento de la operación") Instant occurredAt,
		@Schema(description = "UPDATE: el campo editado. CREATE: todos los valores con que se registró") List<FieldChangeResponse> fields) {
}
