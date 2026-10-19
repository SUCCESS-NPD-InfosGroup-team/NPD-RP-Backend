package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

public record ImportEmployeeCommand(
		String code,
		String fullName,
		String email,
		short genderId,
		Long jobTitleId,
		long jobPositionId,
		Short level,
		Short businessUnitId,
		short workModalityId,
		LocalDate hireDate,
		short statusId,
		Short terminationReasonId,
		Short talentReviewId,
		Long projectId,
		Long clientRoleId,
		Short equipmentProviderId,
		String clientContactEmail,
		LocalDate projectEndDate) {
}
