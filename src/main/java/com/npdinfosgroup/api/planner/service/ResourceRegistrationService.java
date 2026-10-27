package com.npdinfosgroup.api.planner.service;

import java.util.List;
import java.util.Map;

import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.AssignmentRequest;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceCommand;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceRequest;
import com.npdinfosgroup.api.planner.dto.response.ResourceResponse;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.service.mapper.ResourceMapper;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers a new collaborator and, if they already have one, their project (HU-01).
 * <p>
 * A resource is one {@code employee} row: the collaborator's data plus their current project, which
 * must already exist. Registering it, reading it back and auditing its initial values happen in one
 * transaction, so a failure saves nothing and the user can retry.
 */
@Service
public class ResourceRegistrationService {

	private static final Logger log = LoggerFactory.getLogger(ResourceRegistrationService.class);

	/** Unique constraint of {@code employee.code}, created by V2. */
	private static final String CODE_CONSTRAINT = "employee_code_key";

	private final ResourceRequestValidator validator;

	private final EmployeeRepository employees;

	private final ResourceQueryService queries;

	private final AuditService audit;

	private final ResourceMapper mapper;

	/**
	 * Creates the service.
	 *
	 * @param validator checks the request
	 * @param employees saves the employee
	 * @param queries gives the situation of the saved record
	 * @param audit records the initial values
	 * @param mapper builds the response and the audited values
	 */
	ResourceRegistrationService(ResourceRequestValidator validator, EmployeeRepository employees,
			ResourceQueryService queries, AuditService audit, ResourceMapper mapper) {
		this.validator = validator;
		this.employees = employees;
		this.queries = queries;
		this.audit = audit;
		this.mapper = mapper;
	}

	/**
	 * Registers a resource.
	 * <p>
	 * On Baja the project is ignored: the person is saved without project. With another status and no
	 * assignment the person is saved "Sin proyecto". The situation is never received; the database
	 * calculates it.
	 *
	 * @param command the resource and who registers it
	 * @return the saved record, with version 0
	 * @throws ValidationException with every invalid field
	 * @throws ApiException {@link ErrorCode#EMPLOYEE_NUMBER_TAKEN} if the employee number is taken
	 */
	@Transactional
	public ResourceResponse register(RegisterResourceCommand command) {
		RegisterResourceRequest request = command.resource();
		short statusId = validator.validateNew(request);
		Employee employee = toEmployee(request, statusId);
		try {
			employees.saveAndFlush(employee);
		}
		catch (DataIntegrityViolationException ex) {
			// Someone registered the same number between the check and the insert
			if (isCodeConflict(ex)) {
				throw new ApiException(ErrorCode.EMPLOYEE_NUMBER_TAKEN, List.of(employee.getCode(), ""), Map.of());
			}
			throw ex;
		}
		long id = employee.getId();
		// Loads what the database set: generated columns and the names of the related catalogs
		employees.refresh(employee);
		ResourceResponse response = mapper.toResponse(employee, queries.situationOf(id), command.actor());
		audit.recordCreation(ResourceQueryService.AUDIT_ENTITY, id, mapper.auditValues(response), command.actor());
		log.info("Resource {} registered", id);
		return response;
	}

	private static Employee toEmployee(RegisterResourceRequest request, short statusId) {
		Employee employee = new Employee();
		employee.setCode(request.employeeNumber().strip());
		employee.setFullName(request.name().strip());
		employee.setEmail(clean(request.email()));
		employee.setJobTitleId(request.jobTitleId());
		employee.setJobPositionId(request.positionId());
		employee.setLevel(request.level());
		employee.setBusinessUnitId(request.businessUnitId());
		employee.setWorkModalityId(request.workModeId());
		employee.setGenderId(request.genderId());
		employee.setTalentReviewId(request.talentReviewId());
		employee.setHireDate(request.hireDate());
		employee.setOnProbation(request.onProbation());
		employee.setStatusId(statusId);
		if (ResourceRequestValidator.isTerminated(request)) {
			employee.setTerminationDate(request.termination().date());
			employee.setTerminationReasonId(request.termination().reasonId());
		}
		else if (request.assignment() != null) {
			AssignmentRequest assignment = request.assignment();
			employee.setProjectId(assignment.projectId());
			employee.setLeaderId(assignment.leaderId());
			employee.setClientRoleId(assignment.clientRoleId());
			employee.setEquipmentProviderId(assignment.equipmentId());
			employee.setClientContactEmail(clean(assignment.clientContactEmail()));
			employee.setProjectEndDate(assignment.isIndefinite() ? null : assignment.endDate());
		}
		return employee;
	}

	private static boolean isCodeConflict(DataIntegrityViolationException ex) {
		String message = ex.getMostSpecificCause().getMessage();
		return message != null && message.contains(CODE_CONSTRAINT);
	}

	/** Blank optional text is stored as {@code null}, never as an empty string. */
	private static String clean(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
