package com.npdinfosgroup.api.planner.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos de la barra de estado inferior (HU-11)")
public record OperationsStatusResponse(
		@Schema(description = "Momento del último cambio guardado por la API en cualquier registro; null si aún no hay cambios (solo datos importados)",
				nullable = true) Instant lastUpdatedAt,
		@Schema(description = "Nombre completo de quien hizo ese cambio, o su usuario si no es un usuario de la plataforma; null si aún no hay cambios",
				example = "María Pérez", nullable = true) String lastUpdatedBy,
		@Schema(description = "Próximo envío programado de los avisos por correo; null si no hay envío programado",
				nullable = true) Instant nextNotificationAt,
		@Schema(description = "Recursos en estado Activo", example = "142") long activeResources,
		@Schema(description = "Proyectos activos", example = "31") long activeProjects,
		@Schema(description = "Clientes activos", example = "12") long activeClients) {
}
