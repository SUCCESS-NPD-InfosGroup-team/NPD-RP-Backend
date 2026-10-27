package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Desvinculación, para Estado = BAJA")
public record TerminationRequest(
		@Schema(description = "Fecha de desvinculación; no antes de la fecha de inicio de contrato") @NotNull LocalDate date,
		@Schema(description = "Motivo (catálogo terminationReasons)") @NotNull Short reasonId) {
}
