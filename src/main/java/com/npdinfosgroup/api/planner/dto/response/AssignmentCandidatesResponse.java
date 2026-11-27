package com.npdinfosgroup.api.planner.dto.response;

import java.util.List;

import com.npdinfosgroup.api.shared.dto.PageInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Personas que se pueden agregar al proyecto: primero las «Sin proyecto», luego el resto, por nombre")
public record AssignmentCandidatesResponse(List<AssignmentCandidateResponse> items, PageInfo page,
		@Schema(description = "Cuántas personas «Sin proyecto» hay en total con la misma búsqueda, en todas las páginas", example = "2") long withoutProject) {
}
