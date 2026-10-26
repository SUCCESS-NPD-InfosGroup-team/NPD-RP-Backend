package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

public record AssignmentResponse(IdName client, AssignmentProjectResponse project, IdName leader, IdName clientRole,
		IdName equipment, LocalDate endDate, boolean indefinite, String clientContactEmail) {
}
