package com.npdinfosgroup.api.planner.service;

import com.npdinfosgroup.api.planner.dto.request.AssignmentRequest;
import com.npdinfosgroup.api.planner.dto.request.ResourceForm;
import com.npdinfosgroup.api.planner.dto.request.TerminationRequest;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Writes a validated resource form into its {@code employee} row, the same way for a registration and
 * an edit.
 * <p>
 * Every field is written, including the empty ones, so an edit clears what the form left empty: on
 * Baja the person leaves their project (BD-08), and with any other status the termination is cleared.
 */
final class ResourceFields {

	/** Unique constraint of {@code employee.code}, created by V2. */
	private static final String CODE_CONSTRAINT = "employee_code_key";

	private ResourceFields() {
	}

	/**
	 * Copies the form into the employee.
	 *
	 * @param form the validated resource
	 * @param statusId id of its employment status, as the validator found it
	 * @param employee the row to write, new or loaded
	 */
	static void copy(ResourceForm form, short statusId, Employee employee) {
		employee.setCode(form.employeeNumber().strip());
		employee.setFullName(form.name().strip());
		employee.setEmail(clean(form.email()));
		employee.setJobTitleId(form.jobTitleId());
		employee.setJobPositionId(form.positionId());
		employee.setLevel(form.level());
		employee.setBusinessUnitId(form.businessUnitId());
		employee.setWorkModalityId(form.workModeId());
		employee.setGenderId(form.genderId());
		employee.setTalentReviewId(form.talentReviewId());
		employee.setHireDate(form.hireDate());
		employee.setOnProbation(form.onProbation());
		employee.setStatusId(statusId);
		boolean terminated = ResourceRequestValidator.isTerminated(form);
		copyTermination(terminated ? form.termination() : null, employee);
		copyAssignment(terminated ? null : form.assignment(), employee);
	}

	private static void copyTermination(TerminationRequest termination, Employee employee) {
		employee.setTerminationDate(termination == null ? null : termination.date());
		employee.setTerminationReasonId(termination == null ? null : termination.reasonId());
	}

	/** Without an assignment the person has no project, and so no project data either. */
	private static void copyAssignment(AssignmentRequest assignment, Employee employee) {
		if (assignment == null) {
			employee.setProjectId(null);
			employee.setLeaderId(null);
			employee.setClientRoleId(null);
			employee.setEquipmentProviderId(null);
			employee.setClientContactEmail(null);
			employee.setProjectEndDate(null);
			return;
		}
		employee.setProjectId(assignment.projectId());
		employee.setLeaderId(assignment.leaderId());
		employee.setClientRoleId(assignment.clientRoleId());
		employee.setEquipmentProviderId(assignment.equipmentId());
		employee.setClientContactEmail(clean(assignment.clientContactEmail()));
		employee.setProjectEndDate(assignment.isIndefinite() ? null : assignment.endDate());
	}

	/**
	 * Whether a failed write broke the unique employee number: someone saved the same number between
	 * the validation and the write.
	 *
	 * @param ex the failure
	 * @return {@code true} if it was the employee number
	 */
	static boolean isCodeConflict(DataIntegrityViolationException ex) {
		String message = ex.getMostSpecificCause().getMessage();
		return message != null && message.contains(CODE_CONSTRAINT);
	}

	/** Blank optional text is stored as {@code null}, never as an empty string. */
	private static String clean(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
