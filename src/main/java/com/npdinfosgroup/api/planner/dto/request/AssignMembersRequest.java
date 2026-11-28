package com.npdinfosgroup.api.planner.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agregar o mover varias personas a un proyecto")
public record AssignMembersRequest(
		@Schema(description = "Valores para todas las personas; cada una puede cambiarlos") AssignmentDefaults defaults,
		@Schema(description = "De 1 a 100 personas, sin repetir") List<MemberAssignment> members) {
}
