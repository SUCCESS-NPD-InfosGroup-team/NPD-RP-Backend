package com.npdinfosgroup.api.planner.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.npdinfosgroup.api.planner.dto.request.AssignmentRequest;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceRequest;
import com.npdinfosgroup.api.planner.dto.request.TerminationRequest;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.entity.Project;
import com.npdinfosgroup.api.planner.persistence.repository.Catalog;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

/**
 * Checks a resource before it is saved and reports every problem at once, so the form can list them
 * all in its error summary instead of one per attempt.
 * <p>
 * Combines the Jakarta constraints of the request with the rules that depend on other fields or on
 * the database: the project fields only when there is a project, the termination only on Baja, dates,
 * inactive "No especificado" values and duplicates.
 */
@Component
class ResourceRequestValidator {

	/** Status that ends the employment: requires a termination and leaves the project. */
	static final String TERMINATED = "BAJA";

	/** Fields in form order, so the error summary reads top to bottom. */
	private static final List<String> FORM_ORDER = List.of("employeeNumber", "name", "email", "jobTitleId",
			"positionId", "level", "businessUnitId", "workModeId", "genderId", "talentReviewId", "hireDate", "status",
			"termination.date", "termination.reasonId", "assignment.projectId", "assignment.leaderId",
			"assignment.clientRoleId", "assignment.equipmentId", "assignment.endDate", "assignment.clientContactEmail");

	private static final String EMPLOYEE_NUMBER = "employeeNumber";

	private static final String ASSIGNMENT_END_DATE = "assignment.endDate";

	private final Validator validator;

	private final CatalogService catalogs;

	private final ProjectRepository projects;

	private final EmployeeRepository employees;

	private final Clock clock;

	/**
	 * Creates the validator.
	 *
	 * @param validator Jakarta Bean Validation
	 * @param catalogs catalog and status checks
	 * @param projects project checks
	 * @param employees lead and duplicate checks
	 * @param clock gives today for the "already passed" rule
	 */
	ResourceRequestValidator(Validator validator, CatalogService catalogs, ProjectRepository projects,
			EmployeeRepository employees, Clock clock) {
		this.validator = validator;
		this.catalogs = catalogs;
		this.projects = projects;
		this.employees = employees;
		this.clock = clock;
	}

	/**
	 * Validates a new resource.
	 *
	 * @param request the resource to register
	 * @return the id of its employment status
	 * @throws ValidationException with every invalid field, when there is at least one
	 * @throws ApiException {@link ErrorCode#EMPLOYEE_NUMBER_TAKEN} when the only problem is that the
	 *         employee number is already registered
	 */
	short validateNew(RegisterResourceRequest request) {
		List<FieldProblem> problems = new ArrayList<>(constraintProblems(validator.validate(request), ""));
		Optional<Short> statusId = checkStatus(request.status(), problems);
		checkCatalog(problems, "jobTitleId", Catalog.JOB_TITLES, request.jobTitleId());
		checkCatalog(problems, "positionId", Catalog.POSITIONS, request.positionId());
		checkCatalog(problems, "businessUnitId", Catalog.BUSINESS_UNITS, request.businessUnitId());
		checkCatalog(problems, "workModeId", Catalog.WORK_MODES, request.workModeId());
		checkCatalog(problems, "genderId", Catalog.GENDERS, request.genderId());
		checkCatalog(problems, "talentReviewId", Catalog.TALENT_REVIEWS, request.talentReviewId());
		if (isTerminated(request)) {
			checkTermination(request.termination(), request.hireDate(), problems);
		}
		else if (request.assignment() != null) {
			checkAssignment(request.assignment(), problems);
		}
		checkEmailFree(request.email(), problems);

		Optional<Employee> existing = hasProblem(problems, EMPLOYEE_NUMBER) ? Optional.empty()
				: employees.findFirstByCodeIgnoreCase(request.employeeNumber().strip());
		if (existing.isPresent() && problems.isEmpty()) {
			throw employeeNumberTaken(existing.get());
		}
		existing.ifPresent(employee -> problems.add(new FieldProblem(EMPLOYEE_NUMBER,
				FieldErrorCode.EMPLOYEE_NUMBER_TAKEN, takenArgs(employee),
				existingResourceDetails(employee))));
		if (!problems.isEmpty()) {
			throw new ValidationException(inFormOrder(problems));
		}
		return statusId.orElseThrow();
	}

	/**
	 * Whether the resource is on Baja, in which case its project is ignored.
	 *
	 * @param request the resource
	 * @return {@code true} on Baja
	 */
	static boolean isTerminated(RegisterResourceRequest request) {
		return request.status() != null && TERMINATED.equals(request.status().strip().toUpperCase(Locale.ROOT));
	}

	/**
	 * The 409 error of a taken employee number, with a link to the existing record.
	 *
	 * @param existing the employee who already has the number
	 * @return the error
	 */
	static ApiException employeeNumberTaken(Employee existing) {
		return new ApiException(ErrorCode.EMPLOYEE_NUMBER_TAKEN, takenArgs(existing),
				existingResourceDetails(existing));
	}

	/**
	 * Arguments of the "employee number taken" messages: the number and, in parentheses, who has it.
	 *
	 * @param existing the employee who already has the number
	 * @return the arguments
	 */
	private static List<Object> takenArgs(Employee existing) {
		return List.of(existing.getCode(), " (" + existing.getFullName() + ")");
	}

	private static Map<String, Object> existingResourceDetails(Employee existing) {
		return Map.of("existingResourceId", existing.getId(), "existingResourceName", existing.getFullName());
	}

	private Optional<Short> checkStatus(String status, List<FieldProblem> problems) {
		if (status == null || status.isBlank()) {
			return Optional.empty();
		}
		Optional<Short> statusId = catalogs.findStatusId(status.strip().toUpperCase(Locale.ROOT));
		if (statusId.isEmpty()) {
			problems.add(FieldProblem.of("status", FieldErrorCode.UNKNOWN_VALUE));
		}
		return statusId;
	}

	private void checkTermination(TerminationRequest termination, LocalDate hireDate, List<FieldProblem> problems) {
		if (termination == null) {
			problems.add(FieldProblem.of("termination.date", FieldErrorCode.REQUIRED));
			problems.add(FieldProblem.of("termination.reasonId", FieldErrorCode.REQUIRED));
			return;
		}
		problems.addAll(constraintProblems(validator.validate(termination), "termination."));
		checkCatalog(problems, "termination.reasonId", Catalog.TERMINATION_REASONS, termination.reasonId());
		if (termination.date() != null && hireDate != null && termination.date().isBefore(hireDate)) {
			problems.add(FieldProblem.of("termination.date", FieldErrorCode.DATE_BEFORE_HIRE));
		}
	}

	private void checkAssignment(AssignmentRequest assignment, List<FieldProblem> problems) {
		problems.addAll(constraintProblems(validator.validate(assignment), "assignment."));
		if (assignment.projectId() != null) {
			projects.findById(assignment.projectId())
				.map(Project::isActive)
				.ifPresentOrElse(active -> addIf(!active, problems, "assignment.projectId", FieldErrorCode.INACTIVE_PROJECT),
						() -> problems.add(FieldProblem.of("assignment.projectId", FieldErrorCode.UNKNOWN_ID)));
		}
		if (assignment.leaderId() != null) {
			employees.findById(assignment.leaderId())
				.map(leader -> leader.getStatus().getCode())
				.ifPresentOrElse(
						status -> addIf(TERMINATED.equals(status), problems, "assignment.leaderId",
								FieldErrorCode.INACTIVE_LEADER),
						() -> problems.add(FieldProblem.of("assignment.leaderId", FieldErrorCode.UNKNOWN_ID)));
		}
		checkCatalog(problems, "assignment.clientRoleId", Catalog.CLIENT_ROLES, assignment.clientRoleId());
		checkCatalog(problems, "assignment.equipmentId", Catalog.EQUIPMENT, assignment.equipmentId());
		if (assignment.isIndefinite() && assignment.endDate() != null) {
			problems.add(FieldProblem.of(ASSIGNMENT_END_DATE, FieldErrorCode.CONFLICTING_VALUES));
		}
		else if (!assignment.isIndefinite() && assignment.endDate() == null) {
			problems.add(FieldProblem.of(ASSIGNMENT_END_DATE, FieldErrorCode.REQUIRED));
		}
		else if (assignment.endDate() != null && assignment.endDate().isBefore(LocalDate.now(clock))) {
			problems.add(FieldProblem.of(ASSIGNMENT_END_DATE, FieldErrorCode.DATE_IN_PAST));
		}
	}

	private void checkEmailFree(String email, List<FieldProblem> problems) {
		if (email != null && !email.isBlank() && !hasProblem(problems, "email")
				&& employees.existsByEmailIgnoreCase(email.strip())) {
			problems.add(FieldProblem.of("email", FieldErrorCode.EMAIL_TAKEN));
		}
	}

	private void checkCatalog(List<FieldProblem> problems, String field, Catalog catalog, Number id) {
		if (id == null) {
			return;
		}
		catalogs.findActive(catalog, id)
			.ifPresentOrElse(active -> addIf(!active, problems, field, FieldErrorCode.NOT_SPECIFIED_VALUE),
					() -> problems.add(FieldProblem.of(field, FieldErrorCode.UNKNOWN_ID)));
	}

	private static void addIf(boolean condition, List<FieldProblem> problems, String field, FieldErrorCode code) {
		if (condition) {
			problems.add(FieldProblem.of(field, code));
		}
	}

	private static boolean hasProblem(List<FieldProblem> problems, String field) {
		return problems.stream().anyMatch(problem -> problem.field().equals(field));
	}

	private static <T> List<FieldProblem> constraintProblems(Set<ConstraintViolation<T>> violations, String prefix) {
		return violations.stream()
			.map(violation -> FieldProblem.of(prefix + violation.getPropertyPath(), codeOf(violation)))
			.toList();
	}

	private static FieldErrorCode codeOf(ConstraintViolation<?> violation) {
		Class<?> constraint = violation.getConstraintDescriptor().getAnnotation().annotationType();
		if (constraint == NotNull.class || constraint == NotBlank.class) {
			return FieldErrorCode.REQUIRED;
		}
		return constraint == Size.class ? FieldErrorCode.TOO_LONG : FieldErrorCode.INVALID_FORMAT;
	}

	private static List<FieldProblem> inFormOrder(List<FieldProblem> problems) {
		return problems.stream().sorted(Comparator.comparingInt(ResourceRequestValidator::formPosition)).toList();
	}

	private static int formPosition(FieldProblem problem) {
		int position = FORM_ORDER.indexOf(problem.field());
		return position < 0 ? FORM_ORDER.size() : position;
	}

}
