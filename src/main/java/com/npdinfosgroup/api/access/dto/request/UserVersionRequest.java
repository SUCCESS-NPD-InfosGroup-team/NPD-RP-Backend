package com.npdinfosgroup.api.access.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserVersionRequest(
		@Schema(description = "`version` del usuario que se leyó; si otra persona lo guardó después, responde 409", example = "3") Integer version) {
}
