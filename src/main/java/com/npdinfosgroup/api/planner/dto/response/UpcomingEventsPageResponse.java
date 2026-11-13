package com.npdinfosgroup.api.planner.dto.response;

import java.util.List;

import com.npdinfosgroup.api.shared.dto.PageInfo;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpcomingEventsPageResponse(
		@Schema(description = "Totales por tipo para las tarjetas") UpcomingEventsSummary summary,
		@Schema(description = "Eventos de la pagina, por fecha") List<UpcomingEventResponse> items,
		PageInfo page) {
}
