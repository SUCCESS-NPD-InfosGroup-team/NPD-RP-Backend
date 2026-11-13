package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

import com.npdinfosgroup.api.planner.dto.request.UpcomingEventType;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpcomingEventResponse(
		@Schema(description = "Id estable del evento, por ejemplo `ASSIGNMENT_END-12`") String id,
		@Schema(description = "Tipo de evento") UpcomingEventType type,
		@Schema(description = "Fecha del evento") LocalDate date,
		@Schema(description = "Dias hasta el evento; negativo si ya paso") long daysRemaining,
		@Schema(description = "Colaborador; null en CONTRACT_END, que afecta a todo el equipo") EventResourceResponse resource,
		@Schema(description = "Personas afectadas: 1, o el equipo del proyecto (sin Baja) en CONTRACT_END") long resourceCount,
		@Schema(description = "Cliente; null si el colaborador no tiene proyecto") IdName client,
		@Schema(description = "Proyecto; null si el colaborador no tiene proyecto") IdName project,
		@Schema(description = "Lider NPD; en CONTRACT_END solo si todo el equipo tiene el mismo") IdName leader,
		@Schema(description = "Aviso por correo del evento") EventNoticeResponse notice) {
}
