package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

public record ProjectSearchCriteria(Long clientId, Long leaderId, LocalDate contractEndBefore, boolean includeInactive) {
}
