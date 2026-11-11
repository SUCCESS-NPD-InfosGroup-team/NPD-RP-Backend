package com.npdinfosgroup.api.history.dto.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Última operación guardada por la API sobre un registro")
public record LastChangeResponse(
		@Schema(description = "CREATE (registro) o UPDATE (edición)", allowableValues = { "CREATE", "UPDATE" }) String action,
		@Schema(description = "Usuario de Keycloak que hizo la operación", example = "maria.perez") String actor,
		@Schema(description = "Momento de la operación") Instant occurredAt,
		@Schema(description = "Todos los campos de esa operación, en el orden del formulario") List<FieldChangeResponse> fields) {
}
