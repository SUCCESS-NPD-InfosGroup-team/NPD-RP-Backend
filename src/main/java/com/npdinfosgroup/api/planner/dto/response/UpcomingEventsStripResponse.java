package com.npdinfosgroup.api.planner.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpcomingEventsStripResponse(
		@Schema(description = "Dias hacia adelante que resume la franja; siempre 30") int windowDays,
		@Schema(description = "Eventos de la ventana, pendientes de confirmacion incluidos: el mismo total que muestra Arribos al abrir") long total,
		@Schema(description = "Hasta 4 eventos de hoy en adelante, por fecha; sin los pendientes de confirmacion") List<UpcomingEventResponse> nearest) {
}
