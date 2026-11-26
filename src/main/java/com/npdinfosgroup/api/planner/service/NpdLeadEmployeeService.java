package com.npdinfosgroup.api.planner.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.request.NewNpdLeadCommand;
import com.npdinfosgroup.api.planner.dto.request.NpdLeadEmployeeRequest;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceCommand;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceRequest;
import com.npdinfosgroup.api.planner.dto.response.NpdLeadEmployeeSummary;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The employee record of NPD lead users, for the {@code access} module (Usuarios). Users and employees
 * are linked by {@code app_user.employee_id}; this service is the only way {@code access} reads or
 * changes the employee side. See {@code docs/specs/SCRUM-53/design.md} §4.
 * <p>
 * Every write is validated and audited as a resource, so a record changed from Usuarios looks the same
 * as one changed from Recursos. The writes join the transaction of the user edit: both are saved, or
 * neither. Baja, back to Activo and renaming come in the next PR (SCRUM-53 plan, PR 7b).
 */
@Service
public class NpdLeadEmployeeService {

	private static final String ACTIVE = "ACTIVO";

	/** Prefix of the field errors, which belong to the {@code employee} object of the user edit. */
	private static final String FIELD_PREFIX = "employee.";

	private final EmployeeRepository employees;

	private final ResourceRegistrationService registration;

	/**
	 * Creates the service.
	 *
	 * @param employees the employee records
	 * @param registration registers a record as any resource
	 */
	NpdLeadEmployeeService(EmployeeRepository employees, ResourceRegistrationService registration) {
		this.employees = employees;
		this.registration = registration;
	}

	/**
	 * The employee records of some users, each with how many collaborators still have them as lead (Baja
	 * excluded): what the user list shows and what must be reassigned when an NPD lead changes role.
	 *
	 * @param employeeIds ids of the linked employee records; may be empty
	 * @return the summary of each record found, by employee id
	 */
	@Transactional(readOnly = true)
	public Map<Long, NpdLeadEmployeeSummary> summaries(Collection<Long> employeeIds) {
		if (employeeIds.isEmpty()) {
			return Map.of();
		}
		return employees.findNpdLeadEmployees(employeeIds, ResourceRequestValidator.TERMINATED)
			.stream()
			.map(row -> new NpdLeadEmployeeSummary(row.id(), row.employeeNumber(), row.statusCode(),
					row.collaborators()))
			.collect(Collectors.toMap(NpdLeadEmployeeSummary::id, Function.identity()));
	}

	/**
	 * Registers the employee record of a new NPD lead (SCRUM-53 CA-10, HU-10): validated as a new
	 * resource, marked as NPD lead, without project or lead of their own, with the user's name and email.
	 *
	 * @param command the employee data, the user's name and email, and who registers it
	 * @return the id of the new record
	 * @throws ValidationException with every invalid field, named {@code employee.<field>}; the status
	 *         can only be {@code ACTIVO} or {@code LICENCIA}
	 * @throws ApiException {@link ErrorCode#EMPLOYEE_NUMBER_TAKEN} if the employee number is taken
	 */
	@Transactional
	public long register(NewNpdLeadCommand command) {
		NpdLeadEmployeeRequest employee = command.employee();
		List<FieldProblem> problems = new ArrayList<>();
		String status = employee.status();
		if (status != null && ResourceRequestValidator.TERMINATED.equals(status.strip().toUpperCase(Locale.ROOT))) {
			// A new lead cannot start on Baja: validated as Activo so the other fields are still checked
			problems.add(FieldProblem.of(FIELD_PREFIX + "status", FieldErrorCode.UNKNOWN_VALUE));
			status = ACTIVE;
		}
		RegisterResourceRequest resource = new RegisterResourceRequest(employee.employeeNumber(), command.fullName(),
				command.email(), null, employee.positionId(), null, null, employee.workModeId(), employee.genderId(),
				null, employee.hireDate(), false, status, null, null);
		long id;
		try {
			id = registration.register(new RegisterResourceCommand(resource, command.actor())).id();
		}
		catch (ValidationException ex) {
			ex.getProblems()
				.forEach(problem -> problems.add(new FieldProblem(FIELD_PREFIX + problem.field(), problem.code(),
						problem.messageArgs(), problem.details())));
			throw new ValidationException(ex.getScope(), problems);
		}
		if (!problems.isEmpty()) {
			throw new ValidationException(ResourceRequestValidator.MESSAGE_SCOPE, problems);
		}
		employees.findById(id).orElseThrow().setNpdLead(true);
		employees.flush();
		return id;
	}

}
