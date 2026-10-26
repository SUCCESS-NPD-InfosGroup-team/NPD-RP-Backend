package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

public record AssignmentProjectResponse(long id, String name, LocalDate contractEndDate) {
}
