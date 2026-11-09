package com.npdinfosgroup.api.planner.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectKpisResponse(
		@Schema(description = "Proyectos que cumplen los filtros") long projects,
		@Schema(description = "Clientes distintos de esos proyectos") long clients,
		@Schema(description = "Proyectos sin colaboradores (sin contar Baja), «Sin equipo»") long withoutTeam,
		@Schema(description = "Proyectos cuyo contrato con el cliente termina entre hoy y hoy + 30 días") long contractEndingSoon,
		@Schema(description = "Colaboradores con fecha final vencida en esos proyectos («por confirmar»)") long pendingConfirmation,
		@Schema(description = "Colaboradores activos sin proyecto en toda la base (no depende de los filtros)") long collaboratorsWithoutProject) {
}
