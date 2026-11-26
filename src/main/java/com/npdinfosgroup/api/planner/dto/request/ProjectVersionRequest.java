package com.npdinfosgroup.api.planner.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectVersionRequest(
		@Schema(description = "`version` del proyecto que se leyó; si otra persona lo cambió después, responde 409", example = "3") Integer version) {
}
