package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

public record ProjectOptionResponse(long id, String name, long teamSize, LocalDate contractEndDate) {
}
