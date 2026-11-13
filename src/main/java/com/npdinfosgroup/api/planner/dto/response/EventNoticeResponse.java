package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

public record EventNoticeResponse(
		@Schema(description = "SENT, SCHEDULED o NONE. Hoy siempre NONE: los avisos por correo aun no existen",
				allowableValues = { "SENT", "SCHEDULED", "NONE" }) String status,
		@Schema(description = "LEADER, SALES, CUSTOMER_SUCCESS o SUMMARY; null sin aviso") String audience,
		@Schema(description = "30 o 15 dias antes en los avisos en dos pasos; null si no aplica") Integer stage,
		@Schema(description = "Dia en que se envio o se enviara; null sin aviso") LocalDate date) {

	public static EventNoticeResponse none() {
		return new EventNoticeResponse("NONE", null, null, null);
	}

}
