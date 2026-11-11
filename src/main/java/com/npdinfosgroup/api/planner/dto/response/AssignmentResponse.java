package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

public record AssignmentResponse(IdName client, AssignmentProjectResponse project, IdName leader, IdName clientRole,
		IdName equipment, LocalDate endDate, boolean indefinite, String clientContactEmail,
		@Schema(description = "N° de empleado del líder NPD («cód. 631»); null sin líder", example = "631") String leaderEmployeeNumber,
		@Schema(description = "Inicio de la asignación: la fecha más tardía entre la creación del proyecto y la contratación") LocalDate startDate,
		@Schema(description = "Días hasta la fecha final (negativo si ya pasó); null con fecha indefinida") Long daysToEndDate) {
}
