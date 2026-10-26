package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

public record TerminationResponse(LocalDate date, IdName reason) {
}
