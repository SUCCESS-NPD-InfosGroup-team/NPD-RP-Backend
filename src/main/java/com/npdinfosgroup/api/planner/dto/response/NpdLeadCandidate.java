package com.npdinfosgroup.api.planner.dto.response;

import java.time.LocalDate;

public record NpdLeadCandidate(Long id, String name, String employeeNumber, String email, String position,
		String project, String leader, String status, LocalDate hireDate, boolean npdLead, String matchedBy) {
}
