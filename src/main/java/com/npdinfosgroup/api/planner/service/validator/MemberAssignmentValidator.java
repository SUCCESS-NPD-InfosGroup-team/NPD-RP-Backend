package com.npdinfosgroup.api.planner.service.validator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.request.AssignMembersRequest;
import com.npdinfosgroup.api.planner.dto.request.MemberAssignment;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.entity.Project;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.service.CatalogService;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.springframework.stereotype.Component;

/**
 * Checks a bulk assignment (SCRUM-127, HU-08) before anything is written: who can be assigned to the project.
 * <p>
 * Every problem is collected, so the screen marks all the wrong rows at once. A row problem carries the
 * person ({@code employeeId}) and its position in {@code members} ({@code index}).
 */
@Component
public class MemberAssignmentValidator {

	/** The texts of the problems are {@code validation.members.<field>.<CODE>}. */
	public static final String MESSAGE_SCOPE = "members";

	/** Most people in one request. */
	public static final int MAX_MEMBERS = 100;

	private static final String INDEX = "index";

	private final EmployeeRepository employees;

	private final CatalogService catalogs;

	/**
	 * Creates the validator.
	 *
	 * @param employees reads the members in one query
	 * @param catalogs gives the id of Baja
	 */
	MemberAssignmentValidator(EmployeeRepository employees, CatalogService catalogs) {
		this.employees = employees;
		this.catalogs = catalogs;
	}

	/**
	 * Validates who a bulk assignment adds or moves to a project.
	 *
	 * @param project the target project, already found and active
	 * @param request the request
	 * @return the person of each member of the request, in the same order
	 * @throws ValidationException with every problem, when there is at least one
	 */
	public List<Employee> validate(Project project, AssignMembersRequest request) {
		List<MemberAssignment> members = Objects.requireNonNullElse(request.members(), List.of());
		if (members.isEmpty() || members.size() > MAX_MEMBERS) {
			throw new ValidationException(MESSAGE_SCOPE, List.of(FieldProblem.of("members",
					members.isEmpty() ? FieldErrorCode.REQUIRED : FieldErrorCode.TOO_MANY)));
		}
		Check check = new Check(project, load(members),
				catalogs.findStatusId(ResourceRequestValidator.TERMINATED).orElseThrow());
		List<Employee> people = new ArrayList<>();
		for (int index = 0; index < members.size(); index++) {
			check.member(members.get(index), index).ifPresent(people::add);
		}
		if (!check.problems.isEmpty()) {
			throw new ValidationException(MESSAGE_SCOPE, check.problems.stream()
				.sorted(Comparator.comparingInt(problem -> (int) problem.details().getOrDefault(INDEX, -1)))
				.toList());
		}
		return people;
	}

	/** The members named in the request, by id, in one query. */
	private Map<Long, Employee> load(List<MemberAssignment> members) {
		Set<Long> ids = members.stream().map(MemberAssignment::employeeId).filter(Objects::nonNull).collect(Collectors.toSet());
		return employees.findAllById(ids).stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
	}

	/** The checks of one request: what it shares, and the problems found so far. */
	private final class Check {

		private final Project project;

		private final Map<Long, Employee> people;

		private final short terminated;

		private final Set<FieldProblem> problems = new LinkedHashSet<>();

		private final Set<Long> seen = new HashSet<>();

		/** Whether the member being checked has a problem, even one already reported for another member. */
		private boolean failed;

		private Check(Project project, Map<Long, Employee> people, short terminated) {
			this.project = project;
			this.people = people;
			this.terminated = terminated;
		}

		/** Checks one member; its person when the row has no problem. */
		private Optional<Employee> member(MemberAssignment member, int index) {
			Long id = member.employeeId();
			Map<String, Object> row = id == null ? Map.of(INDEX, index) : Map.of("employeeId", id, INDEX, index);
			failed = false;
			Employee employee = id == null ? null : people.get(id);
			FieldErrorCode personProblem = id == null ? FieldErrorCode.REQUIRED
					: !seen.add(id) ? FieldErrorCode.DUPLICATED
					: employee == null ? FieldErrorCode.UNKNOWN_ID
					: employee.getStatusId() == terminated ? FieldErrorCode.TERMINATED
					: project.getId().equals(employee.getProjectId()) ? FieldErrorCode.ALREADY_IN_PROJECT : null;
			if (personProblem != null) {
				add("employeeId", personProblem, row);
				return Optional.empty();
			}
			if (member.version() == null) {
				add("version", FieldErrorCode.REQUIRED, row);
			}
			return failed ? Optional.empty() : Optional.of(employee);
		}

		private void add(String field, FieldErrorCode code, Map<String, Object> details) {
			failed = true;
			problems.add(new FieldProblem(field, code, List.of(), details));
		}

	}

}
