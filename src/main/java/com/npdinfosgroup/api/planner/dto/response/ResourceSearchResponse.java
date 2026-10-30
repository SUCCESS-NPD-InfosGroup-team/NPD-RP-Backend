package com.npdinfosgroup.api.planner.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record ResourceSearchResponse(
		@Schema(description = "Las filas de la página, ya ordenadas por grupo") List<ResourceListItemResponse> items,
		@Schema(description = "Los grupos de las filas de esta página, en el mismo orden") List<ResourceGroupResponse> groups,
		PageInfo page) {
}
