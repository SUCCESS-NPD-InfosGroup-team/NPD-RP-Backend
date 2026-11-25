package com.npdinfosgroup.api.planner.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectMembersResponse(List<ProjectMemberResponse> items,
		@Schema(description = "Colaboradores del equipo; igual a `teamSize` del proyecto") long total) {
}
