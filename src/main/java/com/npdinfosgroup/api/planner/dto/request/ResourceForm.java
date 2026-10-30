package com.npdinfosgroup.api.planner.dto.request;

import java.time.LocalDate;

public interface ResourceForm {

	String employeeNumber();

	String name();

	String email();

	Long jobTitleId();

	Long positionId();

	Short level();

	Short businessUnitId();

	Short workModeId();

	Short genderId();

	Short talentReviewId();

	LocalDate hireDate();

	Boolean onProbation();

	String status();

	TerminationRequest termination();

	AssignmentRequest assignment();

}
