package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Una fila del roster: un colaborador")
public record ResourceListItemResponse(long id, String employeeNumber, String name, Short level,
		@Schema(description = "Rol actual en el cliente; null = «Sin rol»") String clientRole,
		@Schema(description = "Líder NPD; null si no tiene") IdName leader,
		CodeLabel status,
		@Schema(description = "Calculada: ASIGNADO, PENDIENTE_CONFIRMACION o SIN_PROYECTO") CodeLabel situation,
		LocalDate hireDate,
		@Schema(description = "En periodo de prueba") boolean onProbation,
		@Schema(description = "Día 90 desde la fecha de inicio de contrato") LocalDate probationEndDate,
		@Schema(description = "Proyecto de la fila; null = «Sin proyecto». Coincide con el `projectId` de su grupo") Long projectId,
		@Schema(description = "Inicio de la barra: la fecha más tardía entre la creación del proyecto y la contratación") LocalDate assignmentStart,
		@Schema(description = "Fecha final en el proyecto; null con indefinido o sin proyecto") LocalDate assignmentEnd,
		@Schema(description = "En un proyecto sin fecha final") boolean indefinite,
		@Schema(description = "Días hasta la fecha final (negativo si ya pasó); null sin fecha final") Long daysToAssignmentEnd) {
}
