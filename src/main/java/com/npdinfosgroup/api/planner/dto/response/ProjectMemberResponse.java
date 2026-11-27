package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un colaborador del equipo de un proyecto")
public record ProjectMemberResponse(long id, String employeeNumber, String name, Short level,
		@Schema(description = "Rol actual en el cliente; null = «Sin rol»") String clientRole,
		@Schema(description = "Líder NPD; null si no tiene") IdName leader,
		@Schema(description = "ACTIVO o LICENCIA; los de Baja no están en el equipo") CodeLabel status,
		@Schema(description = "ASIGNADO o PENDIENTE_CONFIRMACION") CodeLabel situation,
		@Schema(description = "Inicio de la barra: la fecha más tardía entre la creación del proyecto y la contratación") LocalDate assignmentStart,
		@Schema(description = "Fecha final en el proyecto; null con indefinido") LocalDate assignmentEnd,
		@Schema(description = "Sin fecha final") boolean indefinite,
		@Schema(description = "Días hasta la fecha final (negativo si ya pasó); null con indefinido") Long daysToAssignmentEnd,
		@Schema(description = "`version` del colaborador, para «Quitar del proyecto» y «Editar»") Integer version,
		@Schema(description = "Columna «Equipo»: quién provee el laptop (Cliente o Infos); null si no está registrado",
				example = "Cliente", nullable = true) String equipmentProvider,
		@Schema(description = "Columna «Contacto por cliente»: correo de contacto en el cliente; null si no está registrado",
				example = "contacto@caja.com", nullable = true) String clientContactEmail) {
}
