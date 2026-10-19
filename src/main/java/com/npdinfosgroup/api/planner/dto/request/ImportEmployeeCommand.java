package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

public record ImportEmployeeCommand(
		String code,
		String fullName,
		String email,
		Short genderId,
		Long jobTitleId,
		long jobPositionId,
		Short level,
		Short businessUnitId,
		Short workModalityId,
		LocalDate hireDate,
		Short statusId,
		Short terminationReasonId,
		Short talentReviewId,
		Long projectId,
		Long clientRoleId,
		Short equipmentProviderId,
		String clientContactEmail,
		LocalDate projectEndDate) {
}
