package com.npdinfosgroup.api.planner.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateProjectResponse(ProjectResponse project,
		@Schema(description = "Colaboradores cuya fecha final era el fin de contrato anterior y se movió con él") long membersEndDateMoved) {
}
