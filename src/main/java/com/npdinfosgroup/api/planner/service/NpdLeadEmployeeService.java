package com.npdinfosgroup.api.planner.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.NewNpdLeadCommand;
import com.npdinfosgroup.api.planner.dto.request.NpdLeadEmployeeRequest;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceCommand;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceRequest;
import com.npdinfosgroup.api.planner.dto.response.NpdLeadEmployeeSummary;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.TerminationReasonRepository;
import com.npdinfosgroup.api.planner.service.mapper.ResourceMapper;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The employee record of NPD lead users, for the {@code access} module (Usuarios). Users and employees
 * are linked by {@code app_user.employee_id}; this service is the only way {@code access} reads or
 * changes the employee side. See {@code docs/specs/SCRUM-53/design.md} §4.
 * <p>
 * Every write is validated and audited as a resource, so a record changed from Usuarios looks the same
 * as one changed from Recursos. The writes join the transaction of the user edit: both are saved, or
 * neither.
 */
@Service
public class NpdLeadEmployeeService {

	/** Termination reason of a lead whose user left the NPD lead role (V12; not offered in Recursos). */
	public static final String ROLE_CHANGE = "CAMBIO_DE_ROL";

	private static final String ACTIVE = "ACTIVO";

	/** Prefix of the field errors, which belong to the {@code employee} object of the user edit. */
	private static final String FIELD_PREFIX = "employee.";

	private final EmployeeRepository employees;

	private final ResourceRegistrationService registration;

	private final ResourceRequestValidator validator;

	private final CatalogService catalogs;

	private final TerminationReasonRepository reasons;

	private final AuditService audit;

	private final ResourceMapper mapper;

	private final Clock clock;

	/**
	 * Creates the service.
	 *
	 * @param employees the employee records
	 * @param registration registers a record as any resource
	 * @param validator checks a record before anything is saved
	 * @param catalogs gives the ids of the statuses
	 * @param reasons gives the id of {@link #ROLE_CHANGE}
	 * @param audit records the changes
	 * @param mapper gives the audited values of a record
	 * @param clock gives today, the date of a Baja
	 */
	NpdLeadEmployeeService(EmployeeRepository employees, ResourceRegistrationService registration,
			ResourceRequestValidator validator, CatalogService catalogs, TerminationReasonRepository reasons,
			AuditService audit, ResourceMapper mapper, Clock clock) {
		this.employees = employees;
		this.registration = registration;
		this.validator = validator;
		this.catalogs = catalogs;
		this.reasons = reasons;
		this.audit = audit;
		this.mapper = mapper;
		this.clock = clock;
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
		List<FieldProblem> problems = new ArrayList<>();
		RegisterResourceRequest resource = toResource(command, problems);
		long id;
		try {
			id = registration.register(new RegisterResourceCommand(resource, command.actor())).id();
		}
		catch (ValidationException ex) {
			problems.addAll(prefixed(ex));
			throw new ValidationException(ex.getScope(), problems);
		}
		if (!problems.isEmpty()) {
			throw new ValidationException(ResourceRequestValidator.MESSAGE_SCOPE, problems);
		}
		employees.findById(id).orElseThrow().setNpdLead(true);
		employees.flush();
		return id;
	}

	/**
	 * Checks the employee data of a new NPD lead without saving anything, so the user edit can report
	 * its errors together with the user's before calling Keycloak.
	 *
	 * @param command the employee data, the user's name and email, and who registers it
	 * @return every problem, named {@code employee.<field>}; empty if the record can be registered
	 */
	@Transactional(readOnly = true)
	public List<FieldProblem> problemsOf(NewNpdLeadCommand command) {
		List<FieldProblem> problems = new ArrayList<>();
		RegisterResourceRequest resource = toResource(command, problems);
		try {
			validator.validateNew(resource);
		}
		catch (ValidationException ex) {
			problems.addAll(prefixed(ex));
		}
		catch (ApiException ex) {
			// The number taken, when it is the only problem of the resource: here it joins the others
			problems.add(new FieldProblem(FIELD_PREFIX + "employeeNumber", FieldErrorCode.EMPLOYEE_NUMBER_TAKEN,
					ex.getDetailArgs(), ex.getProperties()));
		}
		return problems;
	}

	/**
	 * The resource form of a new NPD lead. A lead cannot start on Baja: that is reported in
	 * {@code problems} and the form says Activo, so the other fields are still checked.
	 */
	private static RegisterResourceRequest toResource(NewNpdLeadCommand command, List<FieldProblem> problems) {
		NpdLeadEmployeeRequest employee = command.employee();
		String status = employee.status();
		if (status != null && ResourceRequestValidator.TERMINATED.equals(status.strip().toUpperCase(Locale.ROOT))) {
			problems.add(FieldProblem.of(FIELD_PREFIX + "status", FieldErrorCode.UNKNOWN_VALUE));
			status = ACTIVE;
		}
		return new RegisterResourceRequest(employee.employeeNumber(), command.fullName(), command.email(), null,
				employee.positionId(), null, null, employee.workModeId(), employee.genderId(), null,
				employee.hireDate(), false, status, null, null);
	}

	private static List<FieldProblem> prefixed(ValidationException ex) {
		return ex.getProblems()
			.stream()
			.map(problem -> new FieldProblem(FIELD_PREFIX + problem.field(), problem.code(), problem.messageArgs(),
					problem.details()))
			.toList();
	}

	/**
	 * Puts the record of a lead whose user leaves the NPD lead role on Baja, with today's date and the
	 * reason {@link #ROLE_CHANGE} (SCRUM-53 CA-11). As any Baja, the person leaves their project; the NPD
	 * lead mark stays, and the collaborators keep them as lead until someone reassigns them.
	 *
	 * @param employeeId id of the record
	 * @param actor who changes the role, for the audit
	 * @throws NotFoundException if the record does not exist
	 * @throws ApiException {@link ErrorCode#CONCURRENT_MODIFICATION} if it is saved meanwhile from Recursos
	 */
	@Transactional
	public void leaveRole(long employeeId, String actor) {
		short terminated = catalogs.findStatusId(ResourceRequestValidator.TERMINATED).orElseThrow();
		short reason = reasons.findByCode(ROLE_CHANGE).orElseThrow().getId();
		change(employeeId, actor, employee -> {
			employee.setStatusId(terminated);
			employee.setTerminationDate(LocalDate.now(clock));
			employee.setTerminationReasonId(reason);
			ResourceFields.leaveProject(employee);
		});
	}

	/**
	 * Puts back to Activo, without project, the record of a former NPD lead whose user is an NPD lead
	 * again (SCRUM-53 CA-10). Only a Baja by {@link #ROLE_CHANGE} comes back this way; a record that is
	 * not on Baja keeps its status.
	 *
	 * @param employeeId id of the record
	 * @param actor who changes the role, for the audit
	 * @throws NotFoundException if the record does not exist
	 * @throws ApiException {@link ErrorCode#EMPLOYEE_NOT_LINKABLE} ({@code reason = TERMINATED}) if it is
	 *         on Baja for another reason, or {@link ErrorCode#CONCURRENT_MODIFICATION} if it is saved
	 *         meanwhile from Recursos
	 */
	@Transactional
	public void returnToRole(long employeeId, String actor) {
		short active = catalogs.findStatusId(ACTIVE).orElseThrow();
		change(employeeId, actor, employee -> {
			employee.setNpdLead(true);
			if (!ResourceRequestValidator.TERMINATED.equals(employee.getStatus().getCode())) {
				return;
			}
			boolean leftByRoleChange = employee.getTerminationReason() != null
					&& ROLE_CHANGE.equals(employee.getTerminationReason().getCode());
			if (!leftByRoleChange) {
				throw new ApiException(ErrorCode.EMPLOYEE_NOT_LINKABLE, List.of(employee.getFullName()),
						Map.of("reason", "TERMINATED", "existingResourceId", employee.getId(), "existingResourceName",
								employee.getFullName()));
			}
			employee.setStatusId(active);
			employee.setTerminationDate(null);
			employee.setTerminationReasonId(null);
		});
	}

	/**
	 * Gives the record the new name of its user (HU-11 CA-04), so the user list and Recursos show the
	 * same name.
	 *
	 * @param employeeId id of the record
	 * @param fullName the new name, already validated and trimmed
	 * @param actor who renames the user, for the audit
	 * @throws NotFoundException if the record does not exist
	 * @throws ApiException {@link ErrorCode#CONCURRENT_MODIFICATION} if it is saved meanwhile from Recursos
	 */
	@Transactional
	public void rename(long employeeId, String fullName, String actor) {
		change(employeeId, actor, employee -> employee.setFullName(fullName));
	}

	/**
	 * Applies a change to a record and audits it as a resource edit: the values before, the change, the
	 * write (checked against the version that was loaded) and the values after.
	 */
	private void change(long employeeId, String actor, Consumer<Employee> edit) {
		Employee employee = employees.findDetailedById(employeeId).orElseThrow(() -> new NotFoundException(employeeId));
		Map<String, String> before = mapper.auditValues(employee);
		edit.accept(employee);
		try {
			employees.flush();
		}
		catch (OptimisticLockingFailureException ex) {
			throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(), Map.of());
		}
		// Loads the names of the new status, reason and so on, for the audited values
		employees.refresh(employee);
		audit.recordChanges(ResourceQueryService.AUDIT_ENTITY, employeeId, before, mapper.auditValues(employee), actor);
	}

}
