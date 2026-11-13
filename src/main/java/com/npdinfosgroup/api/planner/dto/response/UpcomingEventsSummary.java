package com.npdinfosgroup.api.planner.dto.response;

import java.util.Map;

import com.npdinfosgroup.api.planner.dto.request.UpcomingEventType;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpcomingEventsSummary(
		@Schema(description = "Eventos con los filtros de ventana, cliente y lider (sin el de tipo)") long total,
		@Schema(description = "Eventos por tipo, para las tarjetas; ignoran el filtro de tipo") Map<UpcomingEventType, Long> byType) {
}
