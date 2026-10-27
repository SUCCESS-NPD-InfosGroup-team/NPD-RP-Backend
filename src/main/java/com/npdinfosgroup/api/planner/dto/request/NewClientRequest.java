package com.npdinfosgroup.api.planner.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Cliente nuevo")
public record NewClientRequest(
		@Schema(description = "Nombre; único", example = "TIGO") @NotBlank @Size(max = 150) String name,
		@Schema(description = "Cliente interno (Infosgroup); null = false") Boolean isInternal) {
}
